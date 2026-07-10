package io.oci.docker;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<String, ResolvedManifest> manifestCache = new ConcurrentHashMap<>();

    public Optional<ResolvedManifest> resolveManifest(
            String repositoryName,
            String reference
    ) {
        if (reference != null && reference.startsWith(
                "sha256:"
        )) {
            ResolvedManifest cached = manifestCache.get(
                    reference
            );
            if (cached != null) {
                return Optional.of(
                        cached
                );
            }
        }

        Optional<ResolvedManifest> api = apiImageResolver.resolveManifest(
                repositoryName,
                reference
        );
        if (api.isPresent()) {
            cache(
                    api.get()
            );
            return api;
        }
        Optional<ResolvedManifest> containerd = containerdFileResolver.resolveManifest(
                repositoryName,
                reference
        );
        if (containerd.isPresent()) {
            cache(
                    containerd.get()
            );
            return containerd;
        }
        Optional<ResolvedManifest> overlay2 = overlay2FileResolver.resolveManifest(
                repositoryName,
                reference
        );
        if (overlay2.isPresent()) {
            cache(
                    overlay2.get()
            );
        }
        return overlay2;
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

    private void cache(
            ResolvedManifest manifest
    ) {
        if (manifest != null && manifest.digest != null) {
            manifestCache.put(
                    manifest.digest,
                    manifest
            );
        }
    }
}
