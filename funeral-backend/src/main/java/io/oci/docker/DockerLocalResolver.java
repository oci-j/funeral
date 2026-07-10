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
        return containerdFileResolver.resolveManifest(
                reference,
                repositoryName
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
        return containerdFileResolver.resolveBlob(
                digest
        );
    }
}
