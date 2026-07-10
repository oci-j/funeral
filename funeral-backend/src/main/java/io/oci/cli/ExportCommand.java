package io.oci.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.cli.client.FuneralClient;
import io.oci.cli.config.CliConfig;
import io.oci.cli.config.ConfigManager;
import io.oci.cli.config.RegistryAlias;
import io.oci.cli.oci.DockerCliAdapter;
import io.oci.cli.oci.DockerTarConverter;
import io.oci.cli.oci.ImagePackager;
import io.oci.cli.oci.ImageReference;
import io.oci.cli.oci.LocalStorageAdapter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "export",
        description = "Export an image from local storage / Docker / OCI layout to one or more registries"
)
public class ExportCommand implements Callable<Integer> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @CommandLine.Parameters(
            index = "0",
            description = "Source image reference"
    )
    String sourceRef;

    @CommandLine.Option(
            names = {
                    "--to"
            },
            required = true,
            description = "Target image reference(s) (repeatable)"
    )
    List<String> targets;

    @CommandLine.Option(
            names = {
                    "--from"
            },
            defaultValue = "local",
            description = "Source type: local, docker, oci"
    )
    String fromType;

    @CommandLine.Option(
            names = {
                    "--oci-dir"
            },
            description = "OCI layout source directory (for --from oci)"
    )
    Path ociDir;

    @CommandLine.Option(
            names = {
                    "--storage"
            },
            defaultValue = "/tmp/funeral-storage",
            description = "Local storage path (for --from local)"
    )
    String storagePath;

    @CommandLine.Option(
            names = {
                    "--server"
            },
            description = "Target server URL (overrides alias for all targets)"
    )
    String serverUrl;

    @CommandLine.Option(
            names = {
                    "--use-docker"
            },
            description = "Always use docker push for target registries"
    )
    boolean useDocker;

    @Override
    public Integer call() throws Exception {
        ImageReference source = ImageReference.parse(
                sourceRef
        );
        SourceBundle bundle = resolveSource(
                source
        );

        DockerCliAdapter docker = new DockerCliAdapter();
        for (String target : targets) {
            ImageReference targetRef = ImageReference.parse(
                    target
            );
            System.out.println(
                    "Exporting to " + targetRef
            );
            Path tarFile = Files.createTempFile(
                    "funeral-export",
                    ".tar.gz"
            );
            try {
                ImagePackager.packageToDockerTar(
                        bundle.manifestBytes,
                        bundle.mediaType,
                        bundle.reader,
                        targetRef,
                        tarFile
                );
                if (useDocker || !shouldUseFuneralUpload(
                        targetRef
                )) {
                    docker.loadImage(
                            tarFile
                    );
                    docker.pushImage(
                            targetRef.toString()
                    );
                }
                else {
                    FuneralClient client = CliHelper.createClient(
                            targetRef,
                            serverUrl
                    );
                    client.uploadDockerTar(
                            tarFile
                    );
                }
                System.out.println(
                        "Exported to " + targetRef
                );
            }
            finally {
                Files.deleteIfExists(
                        tarFile
                );
            }
        }
        return 0;
    }

    private SourceBundle resolveSource(
            ImageReference ref
    )
            throws IOException,
            InterruptedException {
        switch (fromType.toLowerCase()) {
            case "local":
                return resolveLocalSource(
                        ref
                );
            case "oci":
                return resolveOciSource(
                        ref
                );
            case "docker":
                return resolveDockerSource(
                        ref
                );
            default:
                throw new IllegalArgumentException(
                        "Unknown source type: " + fromType
                );
        }
    }

    private SourceBundle resolveLocalSource(
            ImageReference ref
    )
            throws IOException,
            InterruptedException {
        LocalStorageAdapter local = new LocalStorageAdapter(
                storagePath
        );
        if (!local.isAvailable()) {
            throw new IllegalStateException(
                    "Local storage not available: " + storagePath
            );
        }
        byte[] manifestBytes = local.readManifest(
                ref.repository,
                ref.reference()
        );
        if (manifestBytes == null) {
            throw new IllegalArgumentException(
                    "Image not found in local storage: " + ref
            );
        }
        String mediaType = local.readManifestMediaType(
                ref.repository,
                ref.reference()
        );
        if (mediaType == null) {
            mediaType = "application/vnd.oci.image.manifest.v1+json";
        }
        return new SourceBundle(
                manifestBytes,
                mediaType,
                local::readBlob
        );
    }

    private SourceBundle resolveOciSource(
            ImageReference ref
    )
            throws IOException {
        if (ociDir == null) {
            throw new IllegalArgumentException(
                    "--oci-dir is required for OCI source"
            );
        }
        JsonNode index = MAPPER.readTree(
                ociDir.resolve(
                        "index.json"
                ).toFile()
        );
        JsonNode descriptors = index.get(
                "manifests"
        );
        if (descriptors == null || !descriptors.isArray()) {
            throw new IOException(
                    "Invalid OCI index: no manifests"
            );
        }
        JsonNode descriptor = findMatchingDescriptor(
                descriptors,
                ref
        );
        String digest = descriptor.get(
                "digest"
        ).asText();
        String mediaType = descriptor.has(
                "mediaType"
        )
                ? descriptor.get(
                        "mediaType"
                ).asText()
                : "application/vnd.oci.image.manifest.v1+json";
        byte[] manifestBytes = readLayoutBlob(
                ociDir,
                digest
        );
        return new SourceBundle(
                manifestBytes,
                mediaType,
                d -> readLayoutBlob(
                        ociDir,
                        d
                )
        );
    }

    private SourceBundle resolveDockerSource(
            ImageReference ref
    )
            throws IOException,
            InterruptedException {
        DockerCliAdapter docker = new DockerCliAdapter();
        Path tempTar = Files.createTempFile(
                "funeral-docker-source",
                ".tar.gz"
        );
        Path tempOciDir = Files.createTempDirectory(
                "funeral-docker-source-oci"
        );
        try {
            docker.saveImage(
                    ref.toString(),
                    tempTar
            );
            DockerTarConverter.tarToOciLayout(
                    tempTar,
                    ref,
                    tempOciDir
            );
            return resolveOciSource(
                    ref
            );
        }
        finally {
            Files.deleteIfExists(
                    tempTar
            );
            deleteRecursively(
                    tempOciDir
            );
        }
    }

    private JsonNode findMatchingDescriptor(
            JsonNode descriptors,
            ImageReference ref
    ) {
        List<String> candidates = List.of(
                ref.toString(),
                ref.repository + ":" + ref.tag,
                ref.repository
        );
        Iterator<JsonNode> it = descriptors.elements();
        JsonNode first = null;
        while (it.hasNext()) {
            JsonNode descriptor = it.next();
            if (first == null) {
                first = descriptor;
            }
            JsonNode annotations = descriptor.get(
                    "annotations"
            );
            if (annotations != null) {
                JsonNode name = annotations.get(
                        "org.opencontainers.image.ref.name"
                );
                if (name != null) {
                    String value = name.asText();
                    for (String candidate : candidates) {
                        if (candidate.equals(
                                value
                        )) {
                            return descriptor;
                        }
                    }
                }
            }
        }
        if (first == null) {
            throw new IllegalArgumentException(
                    "No manifest found in OCI layout"
            );
        }
        return first;
    }

    private static byte[] readLayoutBlob(
            Path layoutDir,
            String digest
    )
            throws IOException {
        String hex = digest.replace(
                "sha256:",
                ""
        );
        Path path = layoutDir.resolve(
                "blobs/sha256"
        )
                .resolve(
                        hex
                );
        return Files.readAllBytes(
                path
        );
    }

    private static void deleteRecursively(
            Path path
    )
            throws IOException {
        if (!Files.exists(
                path
        )) {
            return;
        }
        if (Files.isDirectory(
                path
        )) {
            for (Path child : Files.list(
                    path
            ).toList()) {
                deleteRecursively(
                        child
                );
            }
        }
        Files.delete(
                path
        );
    }

    private boolean shouldUseFuneralUpload(
            ImageReference ref
    ) {
        if (serverUrl != null && !serverUrl.isBlank()) {
            return true;
        }
        CliConfig config = new ConfigManager().load();
        RegistryAlias alias = config.aliases.get(
                ref.registry
        );
        return alias != null && alias.serverUrl != null && !alias.serverUrl.isBlank();
    }

    private static class SourceBundle {

        final byte[] manifestBytes;

        final String mediaType;

        final ImagePackager.BlobReader reader;

        SourceBundle(
                byte[] manifestBytes,
                String mediaType,
                ImagePackager.BlobReader reader
        ) {
            this.manifestBytes = manifestBytes;
            this.mediaType = mediaType;
            this.reader = reader;
        }
    }
}
