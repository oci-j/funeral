package io.oci.resource;

import io.oci.dto.ErrorResponse;
import io.oci.model.Blob;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.service.AbstractStorageService;
import io.oci.service.BlobStorage;
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
@Path("/admin/upload")
@ApplicationScoped
@Authenticated
public class DockerTarResource {

    private static final Logger log = LoggerFactory.getLogger(DockerTarResource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    @Named("manifestStorage")
    ManifestStorage manifestStorage;

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
        try {
            // Save uploaded file to temporary location
            tempFile = saveToTempFile(fileInputStream);

            // Parse the tar file
            TarParseResult result = parseDockerTar(tempFile);

            // Save to storage
            saveToStorage(result);

            return Response.ok(result).build();

        } catch (Exception e) {
            log.error("Failed to process Docker tar file", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("UPLOAD_FAILED", "Failed to process Docker tar file", e.getMessage())
                    )))
                    .build();
        } finally {
            // Clean up temp file
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Saves input stream to a temporary file.
     */
    private File saveToTempFile(InputStream inputStream) throws IOException {
        File tempFile = File.createTempFile("docker-tar-", ".tar");
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
     * Parses Docker tar file and extracts image metadata.
     */
    private TarParseResult parseDockerTar(File tarFile) throws IOException {
        TarParseResult result = new TarParseResult();
        result.repositories = new HashSet<>();
        result.manifests = new ArrayList<>();
        result.blobs = new ArrayList<>();

        List<ManifestInfo> manifestList = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(tarFile);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(fis)) {

            ArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                log.debug("Processing tar entry: {}", entryName);

                // Read manifest.json
                if (entryName.equals("manifest.json")) {
                    manifestList.addAll(parseManifestJson(tarInput));
                }
            }
        }

        // Process manifests and create database entries
        for (ManifestInfo manifestInfo : manifestList) {
            // Each manifest represents an image with potentially multiple tags
            for (String repoTag : manifestInfo.repoTags) {
                String[] parts = repoTag.split(":");
                String repositoryName = parts[0];
                String tag = parts.length > 1 ? parts[1] : "latest";

                result.repositories.add(repositoryName);

                // Save manifest info
                TarManifest tarManifest = new TarManifest();
                tarManifest.repository = repositoryName;
                tarManifest.tag = tag;
                tarManifest.configDigest = "sha256:" + manifestInfo.config;
                tarManifest.layerDigests = Arrays.stream(manifestInfo.layers)
                        .map(layer -> "sha256:" + layer)
                        .collect(Collectors.toList());
                result.manifests.add(tarManifest);

                // Track config as a blob
                result.blobs.add(createBlobInfo("sha256:" + manifestInfo.config, manifestInfo.configSize));
            }

            // Track layers as blobs
            for (String layer : manifestInfo.layers) {
                result.blobs.add(createBlobInfo("sha256:" + layer, 0)); // Size unknown from manifest
            }
        }

        return result;
    }

    /**
     * Using proper JSON parsing to parse manifest.json from Docker tar file.
     * This replaces the error-prone manual string parsing with a more reliable approach.
     */
    private List<ManifestInfo> parseManifestJson(InputStream inputStream) throws IOException {
        List<ManifestInfo> manifests = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(inputStream);
            if (root.isArray()) {
                for (JsonNode manifestNode : root) {
                    ManifestInfo info = new ManifestInfo();

                    // Extract Config digest
                    if (manifestNode.has("Config")) {
                        String config = manifestNode.get("Config").asText();
                        // Remove file extension to get just the digest
                        info.config = config.replace(".json", "");
                    }

                    // Extract RepoTags array
                    if (manifestNode.has("RepoTags")) {
                        JsonNode repoTagsNode = manifestNode.get("RepoTags");
                        info.repoTags = new String[repoTagsNode.size()];
                        for (int i = 0; i < repoTagsNode.size(); i++) {
                            info.repoTags[i] = repoTagsNode.get(i).asText();
                        }
                    }

                    // Extract Layers array
                    if (manifestNode.has("Layers")) {
                        JsonNode layersNode = manifestNode.get("Layers");
                        info.layers = new String[layersNode.size()];
                        for (int i = 0; i < layersNode.size(); i++) {
                            String layerFile = layersNode.get(i).asText();
                            // Remove file extension to get just the digest
                            info.layers[i] = layerFile.replace(".tar.gz", "").replace(".tar", "").replace(".layer", "");
                        }
                    }

                    // Extract config size if available
                    if (manifestNode.has("ConfigSize")) {
                        info.configSize = manifestNode.get("ConfigSize").asLong();
                    }

                    manifests.add(info);
                }
                log.info("Parsed {} manifests from tar file", manifests.size());
            } else {
                log.warn("Manifest.json is not an array as expected");
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
     * Saves parsed data to storage.
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

        // Save manifest metadata
        for (TarManifest tarManifest : result.manifests) {
            log.info("Found manifest: {}:{}", tarManifest.repository, tarManifest.tag);

            // Store manifest
            Manifest manifest = new Manifest();
            manifest.repositoryName = tarManifest.repository;
            manifest.tag = tarManifest.tag;
            manifest.digest = tarManifest.configDigest;
            manifest.configDigest = tarManifest.configDigest;
            manifest.layerDigests = tarManifest.layerDigests;
            manifest.mediaType = "application/vnd.docker.distribution.manifest.v2+json";
            manifestStorage.persist(manifest);
        }

        // Save blob metadata
        for (BlobInfo blobInfo : result.blobs) {
            Blob existingBlob = blobStorage.findByDigest(blobInfo.digest);
            if (existingBlob == null) {
                Blob blob = new Blob();
                blob.digest = blobInfo.digest;
                blob.contentLength = blobInfo.size;
                blob.mediaType = "application/vnd.docker.image.rootfs.diff.tar.gzip";
                blobStorage.persist(blob);
            }
        }
    }

    // Inner classes for parsing
    static class TarParseResult {
        public Set<String> repositories = new HashSet<>();
        public List<TarManifest> manifests = new ArrayList<>();
        public List<BlobInfo> blobs = new ArrayList<>();
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
}
