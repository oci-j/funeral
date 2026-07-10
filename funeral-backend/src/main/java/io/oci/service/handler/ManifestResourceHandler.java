package io.oci.service.handler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.oci.annotation.CommentDELETE;
import io.oci.annotation.CommentGET;
import io.oci.annotation.CommentHEAD;
import io.oci.annotation.CommentHeaderParam;
import io.oci.annotation.CommentPUT;
import io.oci.annotation.CommentPath;
import io.oci.annotation.CommentPathParam;
import io.oci.docker.DockerLocalResolver;
import io.oci.docker.ResolvedManifest;
import io.oci.dto.ErrorResponse;
import io.oci.dto.ManifestInfo;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.service.DigestService;
import io.oci.service.ManifestStorage;
import io.oci.service.RepositoryStorage;
import io.oci.util.JsonUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

@CommentPath(
    "/v2/{name}/manifests"
)
@ApplicationScoped
public class ManifestResourceHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
            ManifestResourceHandler.class
    );

    @Inject
    DigestService digestService;

    @Inject
    @Named(
        "repositoryStorage"
    )
    RepositoryStorage repositoryStorage;

    @Inject
    @Named(
        "manifestStorage"
    )
    ManifestStorage manifestStorage;

    @Inject
    DockerLocalResolver dockerLocalResolver;

    @CommentHEAD
    @CommentPath(
        "/{reference}"
    )
    public Response headManifest(
            @CommentPathParam(
                "name"
            )
            String repositoryName,
            @CommentPathParam(
                "reference"
            )
            String reference
    ) {
        Manifest manifest;
        if (reference.startsWith(
                "sha256:"
        )) {
            manifest = manifestStorage.findByRepositoryAndDigest(
                    repositoryName,
                    reference
            );
        }
        else {
            manifest = manifestStorage.findByRepositoryAndTag(
                    repositoryName,
                    reference
            );
        }

        if (manifest != null) {
            return Response.ok()
                    .header(
                            "Content-Type",
                            manifest.mediaType
                    )
                    .header(
                            "Docker-Content-Digest",
                            manifest.digest
                    )
                    .header(
                            "Content-Length",
                            manifest.contentLength
                    )
                    .build();
        }

        Optional<ResolvedManifest> fallback = dockerLocalResolver.resolveManifest(
                repositoryName,
                reference
        );
        if (fallback.isPresent()) {
            ResolvedManifest resolved = fallback.get();
            return Response.ok()
                    .header(
                            "Content-Type",
                            resolved.mediaType
                    )
                    .header(
                            "Docker-Content-Digest",
                            resolved.digest
                    )
                    .header(
                            "Content-Length",
                            resolved.bytes.length
                    )
                    .build();
        }

        var repo = repositoryStorage.findByName(
                repositoryName
        );
        if (repo == null) {
            return Response.status(
                    404
            )
                    .entity(
                            new ErrorResponse(
                                    List.of(
                                            new ErrorResponse.Error(
                                                    "NAME_UNKNOWN",
                                                    "repository name not known to registry",
                                                    repositoryName
                                            )
                                    )
                            ).toJson()
                    )
                    .type(
                            "application/json"
                    )
                    .build();
        }

        return Response.status(
                404
        )
                .entity(
                        new ErrorResponse(
                                List.of(
                                        new ErrorResponse.Error(
                                                "MANIFEST_UNKNOWN",
                                                "manifest unknown",
                                                reference
                                        )
                                )
                        )
                )
                .build();
    }

    @CommentGET
    @CommentPath(
        "/{reference}"
    )
    public Response getManifest(
            @CommentPathParam(
                "name"
            )
            String repositoryName,
            @CommentPathParam(
                "reference"
            )
            String reference
    ) {
        Manifest manifest;
        if (reference.startsWith(
                "sha256:"
        )) {
            manifest = manifestStorage.findByRepositoryAndDigest(
                    repositoryName,
                    reference
            );
        }
        else {
            manifest = manifestStorage.findByRepositoryAndTag(
                    repositoryName,
                    reference
            );
        }

        if (manifest != null) {
            return Response.ok(
                    manifest.content
            )
                    .header(
                            "Content-Type",
                            manifest.mediaType
                    )
                    .header(
                            "Docker-Content-Digest",
                            manifest.digest
                    )
                    .header(
                            "Content-Length",
                            manifest.contentLength
                    )
                    .build();
        }

        Optional<ResolvedManifest> fallback = dockerLocalResolver.resolveManifest(
                repositoryName,
                reference
        );
        if (fallback.isPresent()) {
            ResolvedManifest resolved = fallback.get();
            return Response.ok(
                    resolved.bytes
            )
                    .header(
                            "Content-Type",
                            resolved.mediaType
                    )
                    .header(
                            "Docker-Content-Digest",
                            resolved.digest
                    )
                    .header(
                            "Content-Length",
                            resolved.bytes.length
                    )
                    .build();
        }

        var repo = repositoryStorage.findByName(
                repositoryName
        );
        if (repo == null) {
            return Response.status(
                    404
            )
                    .entity(
                            new ErrorResponse(
                                    List.of(
                                            new ErrorResponse.Error(
                                                    "NAME_UNKNOWN",
                                                    "repository name not known to registry",
                                                    repositoryName
                                            )
                                    )
                            ).toJson()
                    )
                    .type(
                            "application/json"
                    )
                    .build();
        }

        return Response.status(
                404
        )
                .entity(
                        new ErrorResponse(
                                List.of(
                                        new ErrorResponse.Error(
                                                "MANIFEST_UNKNOWN",
                                                "manifest unknown",
                                                reference
                                        )
                                )
                        )
                )
                .build();
    }

    @CommentGET
    @CommentPath(
        "/{reference}/info"
    )
    public Response getManifestInfo(
            @CommentPathParam(
                "name"
            )
            String repositoryName,
            @CommentPathParam(
                "reference"
            )
            String reference
    ) {
        var repo = repositoryStorage.findByName(
                repositoryName
        );
        if (repo == null) {
            return Response.status(
                    404
            )
                    .entity(
                            new ErrorResponse(
                                    List.of(
                                            new ErrorResponse.Error(
                                                    "NAME_UNKNOWN",
                                                    "repository name not known to registry",
                                                    repositoryName
                                            )
                                    )
                            ).toJson()
                    )
                    .type(
                            "application/json"
                    )
                    .build();
        }

        Manifest manifest;
        if (reference.startsWith(
                "sha256:"
        )) {
            manifest = manifestStorage.findByRepositoryAndDigest(
                    repositoryName,
                    reference
            );
        }
        else {
            manifest = manifestStorage.findByRepositoryAndTag(
                    repositoryName,
                    reference
            );
        }

        if (manifest == null) {
            return Response.status(
                    404
            )
                    .entity(
                            new ErrorResponse(
                                    List.of(
                                            new ErrorResponse.Error(
                                                    "MANIFEST_UNKNOWN",
                                                    "manifest unknown",
                                                    reference
                                            )
                                    )
                            )
                    )
                    .build();
        }

        ManifestInfo manifestInfo = new ManifestInfo(
                manifest.digest,
                manifest.mediaType,
                manifest.contentLength,
                manifest.tag,
                manifest.artifactType,
                manifest.createdAt,
                manifest.updatedAt
        );

        return Response.ok(
                manifestInfo
        ).build();
    }

    @CommentPUT
    @CommentPath(
        "/{reference}"
    )
    public Response putManifest(
            @CommentPathParam(
                "name"
            )
            String repositoryName,
            @CommentPathParam(
                "reference"
            )
            String reference,
            @CommentHeaderParam(
                "Content-Type"
            )
            String contentType,
            InputStream inputStream
    ) {
        byte[] manifestBytes;
        try {
            manifestBytes = inputStream.readAllBytes();
        }
        catch (IOException e) {
            throw new RuntimeException(
                    e
            );
        }
        if (manifestBytes.length == 0) {
            return Response.status(
                    400
            )
                    .entity(
                            new ErrorResponse(
                                    List.of(
                                            new ErrorResponse.Error(
                                                    "MANIFEST_INVALID",
                                                    "manifest invalid",
                                                    "empty manifest"
                                            )
                                    )
                            ).toJson()
                    )
                    .build();
        }

        String manifestContent = new String(
                manifestBytes,
                StandardCharsets.UTF_8
        );
        String manifestDigest = digestService.calculateDigest(
                manifestBytes
        );
        if (reference.startsWith(
                "sha256:"
        ) && !reference.equals(
                manifestDigest
        )) {
            return Response.status(
                    400
            )
                    .entity(
                            new ErrorResponse(
                                    List.of(
                                            new ErrorResponse.Error(
                                                    "DIGEST_INVALID",
                                                    "provided digest did not match uploaded content",
                                                    reference
                                            )
                                    )
                            ).toJson()
                    )
                    .build();
        }

        var repo = repositoryStorage.findByName(
                repositoryName
        );
        if (repo == null) {
            repo = new Repository(
                    repositoryName
            );
            repositoryStorage.persist(
                    repo
            );
        }
        Manifest manifest = null;
        try {
            manifest = JsonUtil.fromJson(
                    manifestContent,
                    Manifest.class
            );
            log.info(
                    "Successfully parsed manifest for repository: {}, digest: {}, tag: {}",
                    repositoryName,
                    manifestDigest,
                    manifest.tag
            );
        }
        catch (Exception e) {
            log.warn(
                    "Failed to parse manifest JSON for repository {}: {}",
                    repositoryName,
                    e.getMessage()
            );
            log.warn(
                    "Manifest content (first 200 chars): {}",
                    manifestContent.substring(
                            0,
                            Math.min(
                                    200,
                                    manifestContent.length()
                            )
                    )
            );
            manifest = new Manifest();
        }
        // Check if manifest already exists
        Manifest existingManifest = manifestStorage.findByRepositoryAndDigest(
                repositoryName,
                manifestDigest
        );
        if (existingManifest != null) {
            // Update tag if reference is not a digest
            if (!reference.startsWith(
                    "sha256:"
            )) {
                existingManifest.tag = reference;
                manifestStorage.persist(
                        existingManifest
                );
            }
            return Response.status(
                    201
            )
                    .header(
                            "Location",
                            "/v2/" + repositoryName + "/manifests/" + manifestDigest
                    )
                    .header(
                            "Docker-Content-Digest",
                            manifestDigest
                    )
                    .build();
        }

        manifest.digest = manifestDigest;
        manifest.repositoryId = repo.id;
        manifest.repositoryName = repositoryName;
        manifest.mediaType = contentType != null ? contentType : "application/vnd.docker.distribution.manifest.v2+json";
        manifest.content = manifestContent;
        manifest.contentLength = (long) manifestBytes.length;

        // Extract OCI fields from manifest content
        try {
            var parsed = JsonUtil.fromJson(
                    manifestContent,
                    Map.class
            );

            // Extract config map for reuse
            Object config = parsed.get(
                    "config"
            );
            Map<?, ?> configMap = null;
            if (config instanceof Map) {
                configMap = (Map<?, ?>) config;
            }

            // Extract artifactType, falling back to config.mediaType
            if (manifest.artifactType == null) {
                Object artifactType = parsed.get(
                        "artifactType"
                );
                if (artifactType != null) {
                    manifest.artifactType = artifactType.toString();
                }
                else if (configMap != null) {
                    Object configMediaType = configMap.get(
                            "mediaType"
                    );
                    if (configMediaType != null) {
                        manifest.artifactType = configMediaType.toString();
                    }
                }
            }

            // Extract config digest
            if (manifest.configDigest == null && configMap != null) {
                Object digest = configMap.get(
                        "digest"
                );
                if (digest != null) {
                    manifest.configDigest = digest.toString();
                }
            }

            // Extract tag from annotations if available
            if (manifest.tag == null) {
                Object annotations = parsed.get(
                        "annotations"
                );
                if (annotations instanceof Map) {
                    Object version = ((Map<?, ?>) annotations).get(
                            "org.opencontainers.image.version"
                    );
                    if (version != null) {
                        manifest.tag = version.toString();
                    }
                }
            }

            // Extract layer digests
            if (manifest.layerDigests == null) {
                Object layers = parsed.get(
                        "layers"
                );
                if (layers instanceof List) {
                    List<String> digests = new ArrayList<>();
                    for (Object layer : (List<?>) layers) {
                        if (layer instanceof Map) {
                            Object digest = ((Map<?, ?>) layer).get(
                                    "digest"
                            );
                            if (digest != null) {
                                digests.add(
                                        digest.toString()
                                );
                            }
                        }
                    }
                    manifest.layerDigests = digests;
                }
            }
        }
        catch (Exception ignored) {
        }

        // Always set tag from reference if not a digest
        if (manifest.tag == null && !reference.startsWith(
                "sha256:"
        )) {
            manifest.tag = reference;
        }

        log.info(
                "About to persist manifest for repository: {}, digest: {}, tag: {}, configDigest: {}",
                manifest.repositoryName,
                manifest.digest,
                manifest.tag,
                manifest.configDigest
        );
        manifestStorage.persist(
                manifest
        );
        log.info(
                "Manifest persisted successfully"
        );

        // Update repository timestamp
        repo.updateTimestamp();
        repositoryStorage.persist(
                repo
        );

        Response.ResponseBuilder responseBuilder = Response.status(
                201
        )
                .header(
                        "Location",
                        "/v2/" + repositoryName + "/manifests/" + manifestDigest
                )
                .header(
                        "Docker-Content-Digest",
                        manifestDigest
                );
        if (manifest.subject != null && StringUtils.isNotBlank(
                manifest.subject.digest
        )) {
            responseBuilder = responseBuilder.header(
                    "OCI-Subject",
                    manifest.subject.digest
            );
        }
        return responseBuilder.build();
    }

    @CommentDELETE
    @CommentPath(
        "/{reference}"
    )
    public Response deleteManifest(
            @CommentPathParam(
                "name"
            )
            String repositoryName,
            @CommentPathParam(
                "reference"
            )
            String reference
    ) {

        var repo = repositoryStorage.findByName(
                repositoryName
        );
        if (repo == null) {
            return Response.status(
                    404
            )
                    .entity(
                            new ErrorResponse(
                                    List.of(
                                            new ErrorResponse.Error(
                                                    "NAME_UNKNOWN",
                                                    "repository name not known to registry",
                                                    repositoryName
                                            )
                                    )
                            ).toJson()
                    )
                    .type(
                            "application/json"
                    )
                    .build();
        }

        Manifest manifest;
        if (reference.startsWith(
                "sha256:"
        )) {
            manifest = manifestStorage.findByRepositoryAndDigest(
                    repositoryName,
                    reference
            );
        }
        else {
            manifest = manifestStorage.findByRepositoryAndTag(
                    repositoryName,
                    reference
            );
        }

        if (manifest == null) {
            return Response.status(
                    404
            )
                    .entity(
                            new ErrorResponse(
                                    List.of(
                                            new ErrorResponse.Error(
                                                    "MANIFEST_UNKNOWN",
                                                    "manifest unknown",
                                                    reference
                                            )
                                    )
                            )
                    )
                    .build();
        }

        manifestStorage.delete(
                manifest.id
        );
        return Response.status(
                202
        ).build();
    }
}
