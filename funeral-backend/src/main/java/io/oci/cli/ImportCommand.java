package io.oci.cli;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.cli.client.FuneralClient;
import io.oci.cli.oci.DockerCliAdapter;
import io.oci.cli.oci.ImagePackager;
import io.oci.cli.oci.ImageReference;
import io.oci.cli.oci.LocalStorageAdapter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "import",
        description = "Import an image from a registry to local storage / Docker / OCI layout"
)
public class ImportCommand implements Callable<Integer> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @CommandLine.Parameters(
            index = "0",
            description = "Image reference to import"
    )
    String imageRef;

    @CommandLine.Option(
            names = {
                    "-t", "--to"
            },
            defaultValue = "local",
            description = "Output type: local, docker, oci"
    )
    String outputType;

    @CommandLine.Option(
            names = {
                    "--oci-dir"
            },
            description = "OCI layout output directory"
    )
    Path ociDir;

    @CommandLine.Option(
            names = {
                    "--storage"
            },
            defaultValue = "/tmp/funeral-storage",
            description = "Local storage path"
    )
    String storagePath;

    @CommandLine.Option(
            names = {
                    "--server"
            },
            description = "Remote registry server URL (overrides alias)"
    )
    String serverUrl;

    @Override
    public Integer call() throws Exception {
        ImageReference ref = ImageReference.parse(
                imageRef
        );
        FuneralClient client = CliHelper.createClient(
                ref,
                serverUrl
        );

        System.out.println(
                "Fetching manifest for " + ref
        );
        HttpResponse<byte[]> response = client.getManifestResponse(
                ref.repository,
                ref.reference()
        );
        byte[] manifestBytes = response.body();
        String mediaType = response.headers()
                .firstValue(
                        "Content-Type"
                )
                .map(
                        s -> s.split(
                                ";"
                        )[0].trim()
                )
                .orElse(
                        "application/vnd.oci.image.manifest.v1+json"
                );

        byte[] imageManifestBytes = manifestBytes;
        String imageMediaType = mediaType;
        if (isIndexMediaType(
                mediaType
        )) {
            JsonNode index = MAPPER.readTree(
                    manifestBytes
            );
            JsonNode manifests = index.get(
                    "manifests"
            );
            if (manifests != null && manifests.isArray()) {
                Iterator<JsonNode> it = manifests.elements();
                while (it.hasNext()) {
                    JsonNode descriptor = it.next();
                    String descMediaType = descriptor.has(
                            "mediaType"
                    )
                            ? descriptor.get(
                                    "mediaType"
                            ).asText()
                            : "application/vnd.oci.image.manifest.v1+json";
                    if (isImageManifestMediaType(
                            descMediaType
                    )) {
                        String digest = descriptor.get(
                                "digest"
                        ).asText();
                        imageManifestBytes = client.getBlob(
                                ref.repository,
                                digest
                        );
                        imageMediaType = descMediaType;
                        break;
                    }
                }
            }
        }

        ImagePackager.BlobReader reader = digest -> client.getBlob(
                ref.repository,
                digest
        );

        switch (outputType.toLowerCase()) {
            case "local":
                importToLocal(
                        ref,
                        imageManifestBytes,
                        imageMediaType,
                        reader
                );
                break;
            case "docker":
                importToDocker(
                        ref,
                        imageManifestBytes,
                        imageMediaType,
                        reader
                );
                break;
            case "oci":
                importToOci(
                        imageManifestBytes,
                        imageMediaType,
                        reader
                );
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown output type: " + outputType
                );
        }

        System.out.println(
                "Import complete"
        );
        return 0;
    }

    private void importToLocal(
            ImageReference ref,
            byte[] manifestBytes,
            String mediaType,
            ImagePackager.BlobReader reader
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
        local.ensureRepository(
                ref.repository
        );
        local.writeManifest(
                ref.repository,
                ref.reference(),
                manifestBytes,
                mediaType
        );

        JsonNode manifest = MAPPER.readTree(
                manifestBytes
        );
        JsonNode config = manifest.get(
                "config"
        );
        if (config != null) {
            String digest = config.get(
                    "digest"
            ).asText();
            local.writeBlob(
                    digest,
                    reader.read(
                            digest
                    )
            );
        }
        JsonNode layers = manifest.get(
                "layers"
        );
        if (layers != null && layers.isArray()) {
            for (JsonNode layer : layers) {
                String digest = layer.get(
                        "digest"
                ).asText();
                local.writeBlob(
                        digest,
                        reader.read(
                                digest
                        )
                );
            }
        }
        System.out.println(
                "Stored image in local storage " + storagePath
        );
    }

    private void importToDocker(
            ImageReference ref,
            byte[] manifestBytes,
            String mediaType,
            ImagePackager.BlobReader reader
    )
            throws IOException,
            InterruptedException {
        Path tarFile = Files.createTempFile(
                "funeral-import",
                ".tar.gz"
        );
        try {
            ImagePackager.packageToDockerTar(
                    manifestBytes,
                    mediaType,
                    reader,
                    ref,
                    tarFile
            );
            DockerCliAdapter docker = new DockerCliAdapter();
            docker.loadImage(
                    tarFile
            );
            System.out.println(
                    "Loaded image into Docker"
            );
        }
        finally {
            Files.deleteIfExists(
                    tarFile
            );
        }
    }

    private void importToOci(
            byte[] manifestBytes,
            String mediaType,
            ImagePackager.BlobReader reader
    )
            throws IOException,
            InterruptedException {
        if (ociDir == null) {
            throw new IllegalArgumentException(
                    "--oci-dir is required for OCI output"
            );
        }
        Files.createDirectories(
                ociDir
        );
        ImagePackager.packageToOciLayout(
                manifestBytes,
                mediaType,
                reader,
                ociDir
        );
        System.out.println(
                "Wrote OCI layout to " + ociDir
        );
    }

    private boolean isIndexMediaType(
            String mediaType
    ) {
        return "application/vnd.oci.image.index.v1+json".equals(
                mediaType
        ) || "application/vnd.docker.distribution.manifest.list.v2+json".equals(
                mediaType
        );
    }

    private boolean isImageManifestMediaType(
            String mediaType
    ) {
        return "application/vnd.oci.image.manifest.v1+json".equals(
                mediaType
        ) || "application/vnd.docker.distribution.manifest.v2+json".equals(
                mediaType
        );
    }
}
