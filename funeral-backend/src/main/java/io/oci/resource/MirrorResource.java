package io.oci.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.oci.dto.ErrorResponse;
import io.oci.model.Blob;
import io.oci.model.ImageReference;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.registry.client.AuthContext;
import io.oci.registry.client.ManifestResponse;
import io.oci.registry.client.RegistryAuthenticationException;
import io.oci.registry.client.RegistryClient;
import io.oci.registry.client.RegistryImageNotFoundException;
import io.oci.service.AbstractStorageService;
import io.oci.service.BlobStorage;
import io.oci.service.ManifestStorage;
import io.oci.service.RepositoryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource for mirroring/pulling images from external OCI registries. This allows users to pull images from external
 * registries without using docker CLI.
 */
@Path(
    "/funeral_addition/mirror"
)
@ApplicationScoped
public class MirrorResource {

    private static final Logger log = LoggerFactory.getLogger(
            MirrorResource.class
    );

    @Inject
    @Named(
        "manifestStorage"
    )
    ManifestStorage manifestStorage;

    @Inject
    @Named(
        "blobStorage"
    )
    BlobStorage blobStorage;

    @Inject
    @Named(
        "repositoryStorage"
    )
    RepositoryStorage repositoryStorage;

    @Inject
    @Named(
        "storage"
    )
    AbstractStorageService storageService;

    @Inject
    RegistryClient registryClient;

    /**
     * Mirror/Pull image from external registry
     *
     * @param sourceImage Full source image with tag (e.g., docker.io/library/nginx:latest)
     * @param targetRepository Optional target repository name (defaults to source repo name)
     * @param targetTag Optional target tag (defaults to source tag)
     * @param username Optional username for authentication
     * @param password Optional password for authentication
     * @param protocol Optional protocol (http or https, defaults to https)
     * @param insecure Allow insecure HTTPS connections
     * @return Mirror result
     */
    @POST
    @Path(
        "/pull"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    @Consumes(
        MediaType.APPLICATION_FORM_URLENCODED
    )
    public Response mirrorImage(
            @FormParam(
                "sourceImage"
            )
            String sourceImage,
            @FormParam(
                "targetRepository"
            )
            String targetRepository,
            @FormParam(
                "targetTag"
            )
            String targetTag,
            @FormParam(
                "username"
            )
            String username,
            @FormParam(
                "password"
            )
            String password,
            @FormParam(
                "protocol"
            )
            @DefaultValue(
                "https"
            )
            String protocol,
            @FormParam(
                "insecure"
            )
            boolean insecure
    ) {
        log.info(
                "Mirror request for image: {}",
                sourceImage
        );

        if (sourceImage == null || sourceImage.trim().isEmpty()) {
            return createErrorResponse(
                    Response.Status.BAD_REQUEST,
                    "BAD_REQUEST",
                    "Source image is required",
                    null
            );
        }

        String normalizedProtocol = protocol != null ? protocol.trim().toLowerCase() : "https";
        if (!"https".equals(
                normalizedProtocol
        ) && !"http".equals(
                normalizedProtocol
        )) {
            return createErrorResponse(
                    Response.Status.BAD_REQUEST,
                    "INVALID_PROTOCOL",
                    "Protocol must be 'http' or 'https'",
                    null
            );
        }

        ImageReference sourceRef;
        try {
            sourceRef = ImageReference.parse(
                    sourceImage.trim()
            );
        }
        catch (IllegalArgumentException e) {
            log.warn(
                    "Invalid source image format: {}",
                    sourceImage,
                    e
            );
            return createErrorResponse(
                    Response.Status.BAD_REQUEST,
                    "INVALID_IMAGE_FORMAT",
                    "Invalid source image format: " + sourceImage,
                    null
            );
        }

        String finalTargetRepo = targetRepository != null ? targetRepository : sourceRef.repository;
        String finalTargetTag = targetTag != null ? targetTag : sourceRef.tag;

        log.info(
                "Mirroring from {} to {}:{}",
                sourceImage,
                finalTargetRepo,
                finalTargetTag
        );

        AuthContext auth = new AuthContext(
                username,
                password,
                normalizedProtocol,
                insecure
        );

        try {
            ManifestResponse manifest = registryClient.pullManifest(
                    sourceRef,
                    auth
            );

            log.info(
                    "Pulling {} blobs from source registry",
                    manifest.layerDigests.size() + 1
            );
            pullAndStoreBlobs(
                    sourceRef,
                    manifest,
                    auth
            );

            storeManifest(
                    finalTargetRepo,
                    finalTargetTag,
                    manifest
            );

            createRepository(
                    finalTargetRepo
            );

            MirrorResult result = new MirrorResult();
            result.success = true;
            result.sourceImage = sourceImage;
            result.targetRepository = finalTargetRepo;
            result.targetTag = finalTargetTag;
            result.manifestDigest = manifest.digest;
            result.blobsCount = manifest.layerDigests.size() + 1;

            return Response.ok(
                    result
            ).build();

        }
        catch (HttpTimeoutException e) {
            log.error(
                    "Network timeout while mirroring image: {}. This may be due to network restrictions or proxy settings.",
                    sourceImage,
                    e
            );
            return createErrorResponse(
                    Response.Status.BAD_GATEWAY,
                    "NETWORK_TIMEOUT",
                    "Failed to connect to the external registry. Please check your network settings or try with a different registry.",
                    sourceImage
            );
        }
        catch (RegistryAuthenticationException e) {
            log.error(
                    "Authentication failed for image: {}",
                    sourceImage,
                    e
            );
            return createErrorResponse(
                    Response.Status.fromStatusCode(
                            e.getStatusCode()
                    ),
                    "AUTHENTICATION_FAILED",
                    "Authentication failed for the external registry: " + e.getMessage(),
                    sourceImage
            );
        }
        catch (RegistryImageNotFoundException e) {
            log.error(
                    "Image not found while mirroring: {}",
                    sourceImage,
                    e
            );
            return createErrorResponse(
                    Response.Status.NOT_FOUND,
                    "IMAGE_NOT_FOUND",
                    "Image not found in the external registry: " + e.getMessage(),
                    sourceImage
            );
        }
        catch (IllegalArgumentException e) {
            log.warn(
                    "Bad request while mirroring image: {}",
                    sourceImage,
                    e
            );
            return createErrorResponse(
                    Response.Status.BAD_REQUEST,
                    "BAD_REQUEST",
                    e.getMessage(),
                    sourceImage
            );
        }
        catch (Exception e) {
            log.error(
                    "Failed to mirror image: {}",
                    sourceImage,
                    e
            );
            return createErrorResponse(
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "MIRROR_FAILED",
                    "Failed to mirror image: " + e.getMessage(),
                    sourceImage
            );
        }
    }

    private void pullAndStoreBlobs(
            ImageReference ref,
            ManifestResponse manifest,
            AuthContext auth
    )
            throws IOException {
        if (manifest.configDigest != null && !manifest.configDigest.isEmpty()) {
            log.info(
                    "Pulling config blob: {}",
                    manifest.configDigest
            );
            try (
                    InputStream inputStream = registryClient.pullBlob(
                            ref,
                            manifest.configDigest,
                            auth
                    )) {
                storeBlob(
                        manifest.configDigest,
                        inputStream,
                        manifest.configSize
                );
            }
        }

        for (String layerDigest : manifest.layerDigests) {
            log.info(
                    "Pulling layer blob: {}",
                    layerDigest
            );
            Long expectedSize = manifest.layerSizes.get(
                    layerDigest
            );
            try (
                    InputStream inputStream = registryClient.pullBlob(
                            ref,
                            layerDigest,
                            auth
                    )) {
                storeBlob(
                        layerDigest,
                        inputStream,
                        expectedSize
                );
            }
        }
    }

    private void storeBlob(
            String digest,
            InputStream data,
            Long expectedSize
    )
            throws IOException {
        try {
            if (storageService.blobExists(
                    digest
            )) {
                log.info(
                        "Blob already exists: {}",
                        digest
                );
                return;
            }

            String storedDigest = storageService.storeBlob(
                    digest,
                    data,
                    expectedSize != null ? expectedSize : -1
            );

            Blob blob = blobStorage.findByDigest(
                    digest
            );
            if (blob == null) {
                blob = new Blob();
                blob.digest = digest;
                blob.contentLength = expectedSize;
                blob.mediaType = digest.startsWith(
                        "sha256:"
                ) && digest.equals(
                        storedDigest
                )
                        ? "application/vnd.docker.container.image.v1+json"
                        : "application/vnd.docker.image.rootfs.diff.tar.gzip";
                blobStorage.persist(
                        blob
                );
                log.info(
                        "Stored blob: {}",
                        digest
                );
            }
        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to store blob: " + digest,
                    e
            );
        }
    }

    private void storeManifest(
            String repository,
            String tag,
            ManifestResponse manifest
    )
            throws IOException {
        try {
            var existingManifest = manifestStorage.findByRepositoryAndTag(
                    repository,
                    tag
            );
            if (existingManifest != null) {
                log.info(
                        "Tag '{}' already exists in repository '{}'. Overwriting.",
                        tag,
                        repository
                );
                manifestStorage.delete(
                        existingManifest.id
                );
            }

            Manifest newManifest = new Manifest();
            newManifest.repositoryName = repository;
            newManifest.tag = tag;
            newManifest.digest = manifest.digest;
            newManifest.configDigest = manifest.configDigest;
            newManifest.layerDigests = manifest.layerDigests;
            newManifest.mediaType = "application/vnd.docker.distribution.manifest.v2+json";
            newManifest.content = manifest.json;
            newManifest.contentLength = (long) manifest.json.getBytes(
                    StandardCharsets.UTF_8
            ).length;
            manifestStorage.persist(
                    newManifest
            );

            log.info(
                    "Stored manifest: {}:{} with digest {}",
                    repository,
                    tag,
                    manifest.digest
            );

        }
        catch (Exception e) {
            throw new IOException(
                    "Failed to store manifest",
                    e
            );
        }
    }

    private void createRepository(
            String repositoryName
    ) {
        Repository repository = repositoryStorage.findByName(
                repositoryName
        );
        if (repository == null) {
            repository = new Repository();
            repository.name = repositoryName;
            repositoryStorage.persist(
                    repository
            );
            log.info(
                    "Created repository: {}",
                    repositoryName
            );
        }
    }

    private Response createErrorResponse(
            Response.Status status,
            String code,
            String message,
            String detail
    ) {
        return Response.status(
                status
        )
                .entity(
                        new ErrorResponse(
                                List.of(
                                        new ErrorResponse.Error(
                                                code,
                                                message,
                                                detail
                                        )
                                )
                        )
                )
                .type(
                        MediaType.APPLICATION_JSON
                )
                .build();
    }

    static class MirrorResult {
        public boolean success;

        public String sourceImage;

        public String targetRepository;

        public String targetTag;

        public String manifestDigest;

        public int blobsCount;
    }
}
