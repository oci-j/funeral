package io.oci.resource;

import io.oci.dto.ErrorResponse;
import io.oci.model.Blob;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.service.AbstractStorageService;
import io.oci.service.BlobStorage;
import io.oci.service.DigestService;
import io.oci.service.ManifestStorage;
import io.oci.service.RepositoryStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.BufferedInputStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resource for uploading and analyzing Docker tar files created by 'docker save' command.
 * Automatically extracts image metadata and stores it in the registry.
 */
@Path("/funeral_addition/write/upload")
@ApplicationScoped
@Authenticated
public class DockerTarResource {

    private static final Logger log = LoggerFactory.getLogger(DockerTarResource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    @Named("manifestStorage")
    ManifestStorage manifestStorage;

    @Inject
    DigestService digestService;

    @Inject
    @Named("blobStorage")
    BlobStorage blobStorage;

    @Inject
    @Named("repositoryStorage")
    RepositoryStorage repositoryStorage;

    @Inject
    @Named("storage")
    AbstractStorageService storageService;

    @ConfigProperty(name = "oci.storage.local-storage-path", defaultValue = "/tmp/funeral-storage")
    String storagePath;

    /**
     * Upload and process a Docker tar file.
     * Automatically analyzes the tar content and extracts image metadata.
     *
     * @param fileInputStream The uploaded tar file
     * @return Analysis results including repositories, manifests, and blobs found
     */
    @POST
    @Path("/dockertar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadDockerTar(@FormParam("file") InputStream fileInputStream) {
        log.info("Received Docker tar file upload request");

        File tempFile = null;
        File tarFile = null;
        try {
            // Save uploaded file to temporary location
            tempFile = saveToTempFile(fileInputStream);

            // Check if it's a zip file and extract tar if needed
            tarFile = extractTarFromZipIfNeeded(tempFile);

            // Parse the tar file
            TarParseResult result = parseDockerTar(tarFile != null ? tarFile : tempFile);

            // Save to storage
            saveToStorage(result);

            // Create response without blobData to avoid serialization issues
            return Response.ok(new UploadResponse(
                result.repositories,
                result.manifests,
                result.blobs
            )).build();

        } catch (Exception e) {
            log.error("Failed to process Docker tar file", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("UPLOAD_FAILED", "Failed to process Docker tar file", e.getMessage())
                    )))
                    .build();
        } finally {
            // Clean up temp files
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            if (tarFile != null && tarFile.exists()) {
                tarFile.delete();
            }
        }
    }

    /**
     * Saves input stream to a temporary file.
     */
    private File saveToTempFile(InputStream inputStream) throws IOException {
        File tempFile = File.createTempFile("docker-tar-", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }

    /**
     * Extracts tar file from zip archive if the uploaded file is a zip.
     * Returns the extracted tar file or null if no extraction was needed.
     */
    private File extractTarFromZipIfNeeded(File uploadedFile) throws IOException {
        // Check if the file is a zip by reading the first few bytes
        try (FileInputStream fis = new FileInputStream(uploadedFile)) {
            byte[] signature = new byte[4];
            if (fis.read(signature) != 4) {
                return null; // File too small
            }

            // ZIP file signature is 0x504B0304 (PK..)
            if (signature[0] == 0x50 && signature[1] == 0x4B) {
                log.info("Detected zip file, extracting tar archive...");
                return extractTarFromZip(uploadedFile);
            }
        }

        // Not a zip file, assume it's already a tar
        return null;
    }

    /**
     * Extracts the first tar file found in a zip archive.
     */
    private File extractTarFromZip(File zipFile) throws IOException {
        File extractedTar = File.createTempFile("docker-tar-extracted-", ".tar");

        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();
                if (entryName.endsWith(".tar") || entryName.endsWith(".tar.gz") || entryName.endsWith(".tgz") || entryName.endsWith(".tar.zst")) {
                    log.info("Found tar file in zip: {}", entry.getName());

                    // Extract the tar file
                    try (FileOutputStream fos = new FileOutputStream(extractedTar)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    log.info("Successfully extracted tar file from zip");
                    return extractedTar;
                }
                zis.closeEntry();
            }
        }

        // No tar file found in zip
        extractedTar.delete();
        throw new IOException("No tar file found in zip archive");
    }

    /**
     * Parses Docker tar file and extracts image metadata and actual blob content.
     * Handles both plain tar and gzip-compressed tar files.
     */
    private TarParseResult parseDockerTar(File tarFile) throws IOException {
        TarParseResult result = new TarParseResult();
        result.repositories = new HashSet<>();
        result.manifests = new ArrayList<>();
        result.blobs = new ArrayList<>();
        result.blobData = new HashMap<>(); // Store actual blob content

        List<ManifestInfo> manifestList = new ArrayList<>();

        log.info("Parsing Docker tar file: {} (size: {} bytes)", tarFile.getName(), tarFile.length());

        // First pass: read manifest.json to get the structure
        List<String> foundFiles = new ArrayList<>();
        Map<String, byte[]> blobContentMap = new HashMap<>(); // Store all blob content

        // Check if the file is gzip compressed by reading the first few bytes
        boolean isGzip = false;
        boolean isZstd = false;
        int bytesRead = 0;
        byte[] signature = new byte[4];
        try (FileInputStream checkFis = new FileInputStream(tarFile)) {
            bytesRead = checkFis.read(signature);
            // Gzip signature is 0x1f 0x8b (31, 139)
            if (bytesRead >= 2 && (signature[0] & 0xFF) == 0x1f && (signature[1] & 0xFF) == 0x8b) {
                isGzip = true;
                log.info("Detected gzip signature: {}{} (0x1f 0x8b)",
                    String.format("%02X", signature[0] & 0xFF), String.format("%02X", signature[1] & 0xFF));
            }
            // Zstandard signature is 0xFD, 0x2F, 0xB5, 0x28 (magic number)
            // But some files may have 0x28, 0xB5, 0x2F, 0xFD (little-endian byte order)
            else if (bytesRead >= 4 && signature[0] == (byte) 0xFD && signature[1] == (byte) 0x2F &&
                     signature[2] == (byte) 0xB5 && signature[3] == (byte) 0x28) {
                isZstd = true;
                log.info("Detected Zstandard signature: {}{}{}{} (big-endian)",
                    String.format("%02X", signature[0] & 0xFF),
                    String.format("%02X", signature[1] & 0xFF),
                    String.format("%02X", signature[2] & 0xFF),
                    String.format("%02X", signature[3] & 0xFF));
            }
            // Check for little-endian Zstandard signature
            else if (bytesRead >= 4 && signature[0] == (byte) 0x28 && signature[1] == (byte) 0xB5 &&
                     signature[2] == (byte) 0x2F && signature[3] == (byte) 0xFD) {
                isZstd = true;
                log.info("Detected Zstandard signature: {}{}{}{} (little-endian)",
                    String.format("%02X", signature[0] & 0xFF),
                    String.format("%02X", signature[1] & 0xFF),
                    String.format("%02X", signature[2] & 0xFF),
                    String.format("%02X", signature[3] & 0xFF));
            } else if (bytesRead >= 4) {
                log.info("File signature: {}{}{}{} (not recognized as gzip or zstd)",
                    String.format("%02X", signature[0] & 0xFF),
                    String.format("%02X", signature[1] & 0xFF),
                    String.format("%02X", signature[2] & 0xFF),
                    String.format("%02X", signature[3] & 0xFF));
            } else if (bytesRead >= 2) {
                log.info("File signature: {}{} (too short, not recognized)",
                    String.format("%02X", signature[0] & 0xFF),
                    String.format("%02X", signature[1] & 0xFF));
            } else {
                log.warn("Could not read file signature, file might be too small or corrupted");
            }
        }

        // Now process the file with the appropriate stream
        if (isZstd) {
            log.info("Detected Zstandard compressed tar file (.tar.zst), decompressing...");
            try (FileInputStream fis = new FileInputStream(tarFile);
                 BufferedInputStream bis = new BufferedInputStream(fis, 8192);
                 ZstdCompressorInputStream zstdStream = new ZstdCompressorInputStream(bis)) {
                processTarStream(zstdStream, result, manifestList, foundFiles, blobContentMap);
            } catch (Exception e) {
                log.error("Failed to decompress Zstandard stream", e);
                throw new IOException("Failed to decompress Zstandard stream: " + e.getMessage(), e);
            }
        } else if (isGzip) {
            log.info("Detected gzip compressed tar file, decompressing...");
            try (FileInputStream fis = new FileInputStream(tarFile);
                 BufferedInputStream bis = new BufferedInputStream(fis, 8192);
                 GzipCompressorInputStream gzipStream = new GzipCompressorInputStream(bis)) {
                processTarStream(gzipStream, result, manifestList, foundFiles, blobContentMap);
            } catch (Exception e) {
                log.error("Failed to decompress gzip stream", e);
                throw new IOException("Failed to decompress gzip stream: " + e.getMessage(), e);
            }
        } else {
            // Plain tar file
            log.info("Processing as plain tar file...");
            try (FileInputStream fis = new FileInputStream(tarFile);
                 BufferedInputStream bis = new BufferedInputStream(fis, 8192)) {
                processTarStream(bis, result, manifestList, foundFiles, blobContentMap);
            }
        }

        log.info("Finished processing tar. blobContentMap has {} entries", blobContentMap.size());

        // Process manifests and create database entries
        for (ManifestInfo manifestInfo : manifestList) {
            // Each manifest represents an image with potentially multiple tags
            for (String repoTag : manifestInfo.repoTags) {
                // Parse repository and tag from format: registry:port/repository:tag
                // Example: 192.168.8.9:8911/ubuntu:25.04 -> repository: ubuntu, tag: 25.04
                String repositoryName;
                String tag;

                // Find the last colon which separates the tag from the repository path
                int lastColonIndex = repoTag.lastIndexOf(':');
                if (lastColonIndex != -1) {
                    tag = repoTag.substring(lastColonIndex + 1);
                    String repoPath = repoTag.substring(0, lastColonIndex);
                    // Remove registry host:port if present (take only the path after the last /)
                    repositoryName = repoPath;
                } else {
                    // No tag specified, use latest
                    tag = "latest";
                    // Remove registry host:port if present
                    repositoryName = repoTag;
                }
                int indexOfFirstSlash = repositoryName.indexOf('/');
                int indexOfFirstColon = repositoryName.indexOf(':');
                int indexOfFirstDot = repositoryName.indexOf('.');
                if ((indexOfFirstSlash > 0 && (indexOfFirstColon > 0 && indexOfFirstColon < indexOfFirstSlash) ||
                     (indexOfFirstDot > 0 && indexOfFirstDot < indexOfFirstSlash))) {
                    // There is a registry part, remove it
                    repositoryName = repositoryName.substring(indexOfFirstSlash + 1);
                }
                log.debug("Parsed repoTag '{}': repository='{}', tag='{}'", repoTag, repositoryName, tag);

                result.repositories.add(repositoryName);

                // Save manifest info
                TarManifest tarManifest = new TarManifest();
                tarManifest.repository = repositoryName;
                tarManifest.tag = tag;
                tarManifest.configDigest = "sha256:" + manifestInfo.config;
                // Layer digests are already extracted to just the hash part
                tarManifest.layerDigests = Arrays.stream(manifestInfo.layers)
                        .map(layer -> "sha256:" + layer)
                        .collect(Collectors.toList());
                result.manifests.add(tarManifest);

                // Track config as a blob (config is already extracted to just the hash part)
                result.blobs.add(createBlobInfo("sha256:" + manifestInfo.config, manifestInfo.configSize));
            }

            // Track layers as blobs (layer digests are already extracted to just the hash part)
            for (String layer : manifestInfo.layers) {
                result.blobs.add(createBlobInfo("sha256:" + layer, 0)); // Size unknown from manifest
            }
        }

        // Store all blob content
        result.blobData = blobContentMap;

        return result;
    }

    /**
     * Processes a tar input stream and extracts metadata and blob content.
     */
    private void processTarStream(InputStream inputStream, TarParseResult result,
                                  List<ManifestInfo> manifestList, List<String> foundFiles,
                                  Map<String, byte[]> blobContentMap) throws IOException {
        int entryCount = 0;
        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(inputStream)) {
            ArchiveEntry entry;
            boolean foundManifest = false;
            while ((entry = tarInput.getNextEntry()) != null) {
                entryCount++;
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                foundFiles.add(entryName);
                log.info("Processing tar entry: {} (size: {} bytes)", entryName, entry.getSize());

                // Read manifest.json or index.json (for OCI layout)
                if (entryName.equals("manifest.json") || entryName.equals("./manifest.json") ||
                    entryName.equals("index.json") || entryName.equals("./index.json")) {
                    foundManifest = true;
                    log.info("Found {}, parsing...", entryName);

                    // Read the manifest content into memory to avoid stream issues
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = tarInput.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }

                    // Parse from the byte array
                    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                    manifestList.addAll(parseManifestJson(bais));
                    log.info("Parsed {} manifests from {}", manifestList.size(), entryName);
                } else if (entryName.contains("/sha256/")) {
                    // This is an OCI blob (config or layer) - read it as a blob
                    log.info("Reading OCI blob: {}", entryName);
                    byte[] content = readEntryContent(tarInput, entry);
                    String digest = extractDigestFromFilename(entryName);
                    String fullPathKey = entryName; // Store with full path for matching
                    blobContentMap.put(digest, content);
                    blobContentMap.put(fullPathKey, content); // Also store with full path
                    log.info("Read {} bytes for blob {} (full path: {}). Map size now: {}", content.length, digest, fullPathKey, blobContentMap.size());
                } else if (entryName.endsWith(".json") && !entryName.contains("manifest.json") && !entryName.contains("index.json")) {
                    // These are config files (Docker format), read them as blobs
                    log.info("Reading config blob: {}", entryName);
                    byte[] content = readEntryContent(tarInput, entry);
                    String digest = extractDigestFromFilename(entryName);
                    blobContentMap.put(digest, content);
                    log.info("Read {} bytes for config blob {}. Map size now: {}", content.length, digest, blobContentMap.size());
                } else if (entryName.endsWith(".tar.gz") || entryName.endsWith(".tar") || entryName.endsWith(".tar.zst")) {
                    // These are layer files (Docker format), read them as blobs
                    log.info("Reading layer blob: {}", entryName);
                    byte[] content = readEntryContent(tarInput, entry);
                    String digest = extractDigestFromFilename(entryName);
                    blobContentMap.put(digest, content);
                    log.info("Read {} bytes for layer blob {}. Map size now: {}", content.length, digest, blobContentMap.size());
                }
            }

            log.info("Successfully processed {} entries from tar archive", entryCount);

            if (!foundManifest) {
                log.warn("No manifest.json found in tar file. Available files:");
                for (String fileName : foundFiles) {
                    log.warn("Entry: {}", fileName);
                }
                throw new IOException("No manifest.json found in tar file. Processed " + entryCount + " entries.");
            }
        } catch (IOException e) {
            log.error("IO error while processing tar stream at entry {}: {}", entryCount, e.getMessage());
            throw new IOException("IO error while processing tar stream at entry " + entryCount + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while processing tar stream at entry {}: {}", entryCount, e.getMessage());
            throw new IOException("Unexpected error while processing tar stream at entry " + entryCount + ": " + e.getMessage(), e);
        }
    }

    /**
     * Using proper JSON parsing to parse manifest.json or index.json from Docker/OCI tar file.
     * Supports both Docker manifest.json format and OCI index.json format.
     */
    private List<ManifestInfo> parseManifestJson(InputStream inputStream) throws IOException {
        List<ManifestInfo> manifests = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(inputStream);

            // Check if this is OCI index.json format
            if (root.has("manifests")) {
                log.info("Detected OCI index.json format");
                // This is an OCI index, look for the OCI layout structure
                // For now, just log this and return empty - need to add proper OCI parsing
                log.warn("OCI layout detected but not fully implemented yet. Please use Docker save format instead.");
                return manifests; // Return empty for now
            }
            // Docker manifest.json format (array of manifest objects)
            else if (root.isArray()) {
                log.info("Detected Docker manifest.json format");
                for (JsonNode manifestNode : root) {
                    ManifestInfo info = new ManifestInfo();

                    // Extract Config digest
                    if (manifestNode.has("Config")) {
                        String config = manifestNode.get("Config").asText();
                        // Remove file extension and extract just the digest
                        // Config might be "abc...def.json" or "blobs/sha256/abc...def.json"
                        info.config = extractDigestFromFilename(config.replace(".json", ""));
                        log.debug("Config file: {}, extracted digest: {}", config, info.config);
                    }

                    // Extract RepoTags array
                    if (manifestNode.has("RepoTags")) {
                        JsonNode repoTagsNode = manifestNode.get("RepoTags");
                        info.repoTags = new String[repoTagsNode.size()];
                        for (int i = 0; i < repoTagsNode.size(); i++) {
                            info.repoTags[i] = repoTagsNode.get(i).asText();
                        }
                        log.debug("RepoTags: {}", Arrays.toString(info.repoTags));
                    }

                    // Extract Layers array
                    if (manifestNode.has("Layers")) {
                        JsonNode layersNode = manifestNode.get("Layers");
                        info.layers = new String[layersNode.size()];
                        for (int i = 0; i < layersNode.size(); i++) {
                            String layerFile = layersNode.get(i).asText();
                            // Remove file extension and extract just the digest
                            // Layer might be "abc...def.tar.gz" or "blobs/sha256/abc...def.tar.gz" or "abc...def.tar.zst"
                            String layerNoExt = layerFile.replace(".tar.gz", "").replace(".tar.zst", "").replace(".tar", "").replace(".layer", "");
                            String layerDigest = extractDigestFromFilename(layerNoExt);
                            info.layers[i] = layerDigest;
                            log.debug("Layer file: {}, extracted digest: {}", layerFile, layerDigest);
                        }
                    }

                    // Extract config size if available
                    if (manifestNode.has("ConfigSize")) {
                        info.configSize = manifestNode.get("ConfigSize").asLong();
                    }

                    manifests.add(info);
                    log.debug("Added manifest with {} layers", info.layers != null ? info.layers.length : 0);
                }
                log.info("Parsed {} manifests from tar file", manifests.size());
            } else {
                log.warn("Unknown JSON format in manifest/index file");
            }
        } catch (Exception e) {
            log.error("Failed to parse manifest.json", e);
            throw new IOException("Failed to parse manifest.json: " + e.getMessage(), e);
        }

        return manifests;
    }

    /**
     * Creates a blob info object.
     */
    private BlobInfo createBlobInfo(String digest, long size) {
        BlobInfo info = new BlobInfo();
        info.digest = digest;
        info.size = size;
        return info;
    }

    /**
     * Builds a proper Docker/OCI manifest JSON from the parsed manifest info.
     */
    private String buildManifestJson(TarManifest tarManifest) {
        try {
            // Build the config section
            String configJson = String.format("""
                "config": {
                    "digest": \"%s\",
                    "mediaType": "application/vnd.docker.container.image.v1+json",
                    "size": 0
                }""", tarManifest.configDigest);

            // Build the layers array
            StringBuilder layersJson = new StringBuilder();
            for (String layerDigest : tarManifest.layerDigests) {
                if (layersJson.length() > 0) {
                    layersJson.append(",");
                }
                layersJson.append(String.format("""
                    {
                        "digest": \"%s\",
                        "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
                        "size": 0
                    }""", layerDigest));
            }

            // Build the complete manifest
            return String.format("""
                {
                    "schemaVersion": 2,
                    "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
                    "config": {
                        "digest": \"%s\",
                        "mediaType": "application/vnd.docker.container.image.v1+json",
                        "size": 0
                    },
                    "layers": [%s]
                }""",
                tarManifest.configDigest,
                layersJson.toString()
            );
        } catch (Exception e) {
            log.error("Failed to build manifest JSON", e);
            return "{\"error\":\"failed to build manifest\"}";
        }
    }

    /**
     * Reads the content of a tar entry into a byte array.
     */
    private byte[] readEntryContent(TarArchiveInputStream tarInput, ArchiveEntry entry) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalBytes = 0;
        long entrySize = entry.getSize();

        log.debug("Reading entry content for {} (expected size: {} bytes)", entry.getName(), entrySize);

        try {
            while ((bytesRead = tarInput.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                if (totalBytes >= entrySize && entrySize > 0) {
                    break; // We've read all the content
                }
            }
        } catch (IOException e) {
            log.error("IO error reading entry {}: read {} of {} bytes - {}",
                entry.getName(), totalBytes, entrySize, e.getMessage());
            throw e;
        }

        log.debug("Successfully read {} bytes for entry {} (expected: {})",
            totalBytes, entry.getName(), entrySize);

        return baos.toByteArray();
    }

    /**
     * Extracts the SHA256 digest from a filename like "blobs/sha256/abc123..."
     */
    private String extractDigestFromFilename(String filename) {
        // Handle different formats:
        // ./blobs/sha256/abc123...
        // blobs/sha256/abc123...
        // abc123... (already just the digest)

        if (filename.contains("sha256/")) {
            String[] parts = filename.split("sha256/");
            if (parts.length > 1) {
                String digest = parts[1];
                // Remove any trailing slash or path
                if (digest.contains("/")) {
                    digest = digest.substring(0, digest.indexOf("/"));
                }
                return digest;
            }
        }

        // If no path structure, assume it's already just the digest
        return filename;
    }


    /**
     * Saves parsed data to storage including actual blob content.
     */
    private void saveToStorage(TarParseResult result) {
        log.info("Saving {} repositories, {} manifests, and {} blobs to storage",
                result.repositories.size(), result.manifests.size(), result.blobs.size());

        // Save repositories
        for (String repositoryName : result.repositories) {
            Repository repository = repositoryStorage.findByName(repositoryName);
            if (repository == null) {
                repository = new Repository();
                repository.name = repositoryName;
                repositoryStorage.persist(repository);
                log.info("Created repository: {}", repositoryName);
            }
        }

        // Save manifest metadata with actual manifest content
        for (TarManifest tarManifest : result.manifests) {
            log.info("Found manifest: {}:{}", tarManifest.repository, tarManifest.tag);

            // Check if tag already exists
            var existingManifest = manifestStorage.findByRepositoryAndTag(tarManifest.repository, tarManifest.tag);
            if (existingManifest != null) {
                log.info("Tag '{}' already exists in repository '{}'. Deleting old manifest before saving new one.", tarManifest.tag, tarManifest.repository);
                manifestStorage.delete(existingManifest.id);
            }

            // Build the actual manifest JSON content
            String manifestContent = buildManifestJson(tarManifest);

            // Calculate digest of the manifest content using DigestService
            String manifestDigest = digestService.calculateDigest(manifestContent);

            // Store manifest
            Manifest manifest = new Manifest();
            manifest.repositoryName = tarManifest.repository;
            manifest.tag = tarManifest.tag;
            manifest.digest = manifestDigest;
            manifest.configDigest = tarManifest.configDigest;
            manifest.layerDigests = tarManifest.layerDigests;
            manifest.mediaType = "application/vnd.docker.distribution.manifest.v2+json";
            manifest.content = manifestContent;
            manifest.contentLength = (long) manifestContent.getBytes().length;
            manifestStorage.persist(manifest);
        }

        // Save blob metadata and actual content
        for (BlobInfo blobInfo : result.blobs) {
            Blob existingBlob = blobStorage.findByDigest(blobInfo.digest);
            if (existingBlob == null) {
                Blob blob = new Blob();
                blob.digest = blobInfo.digest;
                blob.contentLength = blobInfo.size;
                blob.mediaType = "application/vnd.docker.image.rootfs.diff.tar.gzip";
                blobStorage.persist(blob);
                log.info("Created blob metadata for: {}", blobInfo.digest);
            }

            // Store actual blob content
            String digestWithoutPrefix = blobInfo.digest.replace("sha256:", "");

            // Try to find blob content - it might be stored with just the digest or full path
            byte[] blobContent = result.blobData.get(digestWithoutPrefix);

            if (blobContent == null) {
                // Try with the path prefix (for OCI layout)
                String fullPathKey = "blobs/sha256/" + digestWithoutPrefix;
                blobContent = result.blobData.get(fullPathKey);
                if (blobContent != null) {
                    log.debug("Found blob content using full path key: {}", fullPathKey);
                }
            }

            if (blobContent != null) {
                try {
                    // Store the blob content using the blob storage service
                    // The storeBlob method expects the full digest with prefix
                    String storedDigest = storageService.storeBlob(new ByteArrayInputStream(blobContent), blobInfo.digest);
                    log.info("Stored {} bytes for blob: {} (verified digest: {})", blobContent.length, blobInfo.digest, storedDigest);
                } catch (Exception e) {
                    log.error("Failed to store blob content for {}: {}", blobInfo.digest, e.getMessage(), e);
                }
            } else {
                log.warn("No content found for blob: {}. Available keys: {}", blobInfo.digest, result.blobData.keySet());
            }
        }
    }

    // Inner classes for parsing
    static class TarParseResult {
        public Set<String> repositories = new HashSet<>();
        public List<TarManifest> manifests = new ArrayList<>();
        public List<BlobInfo> blobs = new ArrayList<>();
        public Map<String, byte[]> blobData = new HashMap<>(); // Actual blob content
    }

    static class TarManifest {
        public String repository;
        public String tag;
        public String configDigest;
        public List<String> layerDigests;
    }

    static class BlobInfo {
        public String digest;
        public long size;
    }

    static class ManifestInfo {
        public String config;
        public long configSize;
        public String[] repoTags;
        public String[] layers;
    }

    /**
     * Response DTO for upload endpoint (excludes binary blob data)
     */
    static class UploadResponse {
        public Set<String> repositories;
        public List<TarManifest> manifests;
        public List<BlobInfo> blobs;

        public UploadResponse(Set<String> repositories, List<TarManifest> manifests, List<BlobInfo> blobs) {
            this.repositories = repositories;
            this.manifests = manifests;
            this.blobs = blobs;
        }
    }
}
