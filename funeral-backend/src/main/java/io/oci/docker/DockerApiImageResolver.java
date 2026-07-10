package io.oci.docker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import io.oci.docker.util.DeletingOnCloseInputStream;
import io.oci.service.DigestService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DockerApiImageResolver {

    private static final Logger log = LoggerFactory.getLogger(
            DockerApiImageResolver.class
    );

    @Inject
    Provider<DockerClient> dockerClientProvider;

    @Inject
    DockerImageNameResolver nameResolver;

    @Inject
    DigestService digestService;

    @ConfigProperty(
            name = "oci.docker-local.enabled",
            defaultValue = "false"
    )
    boolean enabled;

    public Optional<ResolvedManifest> resolveManifest(
            String repositoryName,
            String reference
    ) {
        if (!enabled) {
            return Optional.empty();
        }
        if (reference != null && reference.startsWith(
                "sha256:"
        )) {
            return Optional.empty();
        }
        try {
            List<Image> images = dockerClientProvider.get().listImagesCmd().exec();
            String imageName = nameResolver.resolve(
                    repositoryName,
                    reference,
                    images
            );
            if (imageName == null) {
                return Optional.empty();
            }
            Path tarFile = exportImage(
                    imageName
            );
            try {
                ResolvedManifest parsed = DockerSaveTarParser.parseManifest(
                        tarFile,
                        imageName
                );
                String digest = digestService.calculateDigest(
                        parsed.bytes
                );
                return Optional.of(
                        new ResolvedManifest(
                                parsed.bytes,
                                parsed.mediaType,
                                digest
                        )
                );
            }
            finally {
                Files.deleteIfExists(
                        tarFile
                );
            }
        }
        catch (Exception e) {
            log.warn(
                    "Failed to resolve manifest for {}:{} via Docker API: {}",
                    repositoryName,
                    reference,
                    e.getMessage()
            );
            return Optional.empty();
        }
    }

    public Optional<ResolvedBlob> resolveBlob(
            String digest,
            String repositoryName
    ) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            List<Image> images = dockerClientProvider.get().listImagesCmd().exec();
            String imageName = nameResolver.resolveByRepositoryName(
                    repositoryName,
                    images
            );
            if (imageName == null) {
                return Optional.empty();
            }
            Path tarFile = exportImage(
                    imageName
            );
            Optional<ResolvedBlob> blob = DockerSaveTarParser.openBlob(
                    tarFile,
                    digest
            );
            if (blob.isPresent()) {
                ResolvedBlob resolved = blob.get();
                InputStream wrapped = new DeletingOnCloseInputStream(
                        resolved.stream,
                        tarFile
                );
                return Optional.of(
                        new ResolvedBlob(
                                wrapped,
                                resolved.size
                        )
                );
            }
            Files.deleteIfExists(
                    tarFile
            );
            return Optional.empty();
        }
        catch (Exception e) {
            log.warn(
                    "Failed to resolve blob {} for {} via Docker API: {}",
                    digest,
                    repositoryName,
                    e.getMessage()
            );
            return Optional.empty();
        }
    }

    private Path exportImage(
            String imageName
    )
            throws IOException {
        Path tempFile = Files.createTempFile(
                "docker-save-",
                ".tar"
        );
        try (
                InputStream is = dockerClientProvider.get()
                        .saveImageCmd(
                                imageName
                        )
                        .exec();
                OutputStream os = Files.newOutputStream(
                        tempFile
                )) {
            is.transferTo(
                    os
            );
        }
        return tempFile;
    }
}
