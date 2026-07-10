package io.oci.docker;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DockerLocalResolver {

    @Inject
    DockerApiImageResolver apiImageResolver;

    @Inject
    ContainerdFileResolver containerdFileResolver;

    @Inject
    Overlay2FileResolver overlay2FileResolver;

    public Optional<ResolvedManifest> resolveManifest(
            String repositoryName,
            String reference
    ) {
        Optional<ResolvedManifest> api = apiImageResolver.resolveManifest(
                repositoryName,
                reference
        );
        if (api.isPresent()) {
            return api;
        }
        Optional<ResolvedManifest> containerd = containerdFileResolver.resolveManifest(
                reference,
                repositoryName
        );
        if (containerd.isPresent()) {
            return containerd;
        }
        return overlay2FileResolver.resolveManifest(
                repositoryName,
                reference
        );
    }

    public Optional<ResolvedBlob> resolveBlob(
            String digest,
            String repositoryName
    ) {
        Optional<ResolvedBlob> api = apiImageResolver.resolveBlob(
                digest,
                repositoryName
        );
        if (api.isPresent()) {
            return api;
        }
        Optional<ResolvedBlob> containerd = containerdFileResolver.resolveBlob(
                digest
        );
        if (containerd.isPresent()) {
            return containerd;
        }
        return overlay2FileResolver.resolveBlob(
                digest,
                repositoryName
        );
    }
}
