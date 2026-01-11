package io.oci.resource;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;

import io.oci.dto.ErrorResponse;
import io.oci.service.ManifestStorage;
import io.oci.service.RepositoryStorage;
import io.oci.service.handler.BlobResourceHandler;
import io.oci.service.handler.ManifestResourceHandler;
import io.oci.service.handler.ReferrerResourceHandler;
import io.oci.service.handler.RegistryResourceHandler;
import io.oci.service.handler.TagResourceHandler;
import io.oci.service.handler.TokenResourceHandler;
import io.oci.util.SplitAtFirstUtil;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(
    "/v2"
)
public class OciV2Resource {

    private static final Logger log = LoggerFactory.getLogger(
            OciV2Resource.class
    );

    private static final Pattern TAG_PATTERN = Pattern.compile(
            "[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}"
    );

    @Inject
    RegistryResourceHandler registryResourceHandler;

    @Inject
    ManifestResourceHandler manifestResourceHandler;

    @Inject
    TagResourceHandler tagResourceHandler;

    @Inject
    BlobResourceHandler blobResourceHandler;

    @Inject
    ReferrerResourceHandler referrerResourceHandler;

    @Inject
    TokenResourceHandler tokenResourceHandler;

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

    @HEAD
    @Path(
        "/{fullPath:.*}"
    )
    public Response fullPathHead(
            @PathParam(
                "fullPath"
            )
            String fullPath,
            @Context
            UriInfo uriInfo,
            @Context
            HttpHeaders httpHeaders
    ) {
        {
            /// @see ManifestResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/manifests/"
            );
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/manifests/".length()
                );
                if (!StringUtils.contains(
                        suffix,
                        '/'
                )) {
                    String name = fullPath.substring(
                            0,
                            lastIndexOfTags
                    );
                    return manifestResourceHandler.headManifest(
                            name,
                            suffix
                    );
                }
            }
        }
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/blobs/"
            );
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/blobs/".length()
                );
                String name = fullPath.substring(
                        0,
                        lastIndexOfTags
                );
                return blobResourceHandler.headBlob(
                        name,
                        suffix
                );
            }
        }

        log.error(
                "404 not found : HEAD /v2/{}",
                fullPath
        );
        return Response.status(
                404
        ).build();
    }

    @GET
    @Path(
        "/{fullPath:.*}"
    )
    public Response fullPathGet(
            @PathParam(
                "fullPath"
            )
            String fullPath,
            @Context
            UriInfo uriInfo,
            @Context
            HttpHeaders httpHeaders
    ) {
        {
            /// @see RegistryResourceHandler
            switch (fullPath) {
                case "":
                    return registryResourceHandler.checkVersion();
                case "repositories":
                    return registryResourceHandler.listRepositories();
                default:
                    // pass
            }
        }
        {
            /// @see ManifestResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/manifests/"
            );
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/manifests/".length()
                );
                if (!StringUtils.contains(
                        suffix,
                        '/'
                )) {
                    String name = fullPath.substring(
                            0,
                            lastIndexOfTags
                    );
                    return manifestResourceHandler.getManifest(
                            name,
                            suffix
                    );
                }
                else if (suffix.endsWith(
                        "/info"
                ) && suffix.indexOf(
                        '/'
                ) == suffix.length() - 5) {
                    // Handle /manifests/{reference}/info endpoint
                    String name = fullPath.substring(
                            0,
                            lastIndexOfTags
                    );
                    String reference = suffix.substring(
                            0,
                            suffix.length() - 5
                    );
                    return manifestResourceHandler.getManifestInfo(
                            name,
                            reference
                    );
                }
            }
        }
        {
            /// @see TagResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/tags/"
            );
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/tags/".length()
                );
                switch (suffix) {
                    case "list":
                        String name = fullPath.substring(
                                0,
                                lastIndexOfTags
                        );
                        return tagResourceHandler.listTags(
                                name,
                                uriInfo.getQueryParameters()
                                        .getFirst(
                                                "n"
                                        ) != null
                                                ? Integer.parseInt(
                                                        uriInfo.getQueryParameters()
                                                                .getFirst(
                                                                        "n"
                                                                )
                                                )
                                                : 100,
                                uriInfo.getQueryParameters()
                                        .getFirst(
                                                "last"
                                        )
                        );
                    default:
                        // pass
                }
            }
        }
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/blobs/"
            );
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(
                        0,
                        lastIndexOfTags
                );
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/blobs/".length()
                );
                if (suffix.startsWith(
                        "uploads/"
                )) {
                    // Handle blob uploads
                    String uploadUuid = suffix.substring(
                            "uploads/".length()
                    );
                    if (!StringUtils.contains(
                            uploadUuid,
                            '/'
                    )) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUploadChunkGet(
                                name,
                                uploadUuid
                        );
                    }
                }
                return blobResourceHandler.getBlob(
                        name,
                        suffix
                );
            }
        }
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/referrers/"
            );
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(
                        0,
                        lastIndexOfTags
                );
                String digest = fullPath.substring(
                        lastIndexOfTags + "/referrers/".length()
                );
                return referrerResourceHandler.getReferrers(
                        name,
                        digest,
                        uriInfo.getQueryParameters()
                                .getFirst(
                                        "artifactType"
                                )
                );
            }
        }
        {
            /// @see TagResourceHandler
            if (fullPath.equals(
                    "token/"
            ) || fullPath.equals(
                    "token"
            )) {
                return tokenResourceHandler.getToken(
                        uriInfo.getQueryParameters()
                                .getFirst(
                                        "service"
                                ),
                        uriInfo.getQueryParameters()
                                .getFirst(
                                        "scope"
                                ),
                        uriInfo.getQueryParameters()
                                .getFirst(
                                        "account"
                                ),
                        httpHeaders
                );
            }
        }
        log.error(
                "404 not found : GET /v2/{}",
                fullPath
        );
        return Response.status(
                404
        ).build();
    }

    @POST
    @Path(
        "/{fullPath:.*}"
    )
    public Response fullPathPost(
            @PathParam(
                "fullPath"
            )
            String fullPath,
            @Context
            UriInfo uriInfo,
            @Context
            HttpHeaders httpHeaders,
            InputStream inputStream
    ) {
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/blobs/"
            );
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(
                        0,
                        lastIndexOfTags
                );
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/blobs/".length()
                );
                switch (suffix) {
                    case "uploads":
                        return blobResourceHandler.startBlobUpload(
                                name,
                                uriInfo.getQueryParameters()
                                        .getFirst(
                                                "digest"
                                        ) != null
                                                ? uriInfo.getQueryParameters()
                                                        .getFirst(
                                                                "digest"
                                                        )
                                                : null,
                                uriInfo.getQueryParameters()
                                        .getFirst(
                                                "mount"
                                        ) != null
                                                ? uriInfo.getQueryParameters()
                                                        .getFirst(
                                                                "mount"
                                                        )
                                                : null,
                                uriInfo.getQueryParameters()
                                        .getFirst(
                                                "from"
                                        ) != null
                                                ? uriInfo.getQueryParameters()
                                                        .getFirst(
                                                                "from"
                                                        )
                                                : null,
                                inputStream
                        );
                    default:
                        // pass
                }
                if (suffix.startsWith(
                        "uploads/"
                )) {
                    String suffix2 = suffix.substring(
                            "uploads/".length()
                    );
                    if (!StringUtils.contains(
                            suffix2,
                            '/'
                    )) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUpload(
                                name,
                                suffix2,
                                uriInfo.getQueryParameters()
                                        .getFirst(
                                                "digest"
                                        ) != null
                                                ? uriInfo.getQueryParameters()
                                                        .getFirst(
                                                                "digest"
                                                        )
                                                : null,
                                inputStream
                        );
                    }
                }
            }
        }
        {
            /// @see TagResourceHandler
            if (fullPath.equals(
                    "token/"
            ) || fullPath.equals(
                    "token"
            )) {
                return tokenResourceHandler.postToken(
                        uriInfo.getQueryParameters()
                                .getFirst(
                                        "service"
                                ),
                        uriInfo.getQueryParameters()
                                .getFirst(
                                        "scope"
                                ),
                        uriInfo.getQueryParameters()
                                .getFirst(
                                        "account"
                                ),
                        httpHeaders
                );
            }
        }
        log.error(
                "404 not found : POST /v2/{}",
                fullPath
        );
        return Response.status(
                404
        ).build();
    }

    @PUT
    @Path(
        "/{fullPath:.*}"
    )
    public Response fullPathPut(
            @PathParam(
                "fullPath"
            )
            String fullPath,
            @Context
            UriInfo uriInfo,
            @Context
            HttpHeaders httpHeaders,
            InputStream inputStream
    ) {
        {
            /// @see ManifestResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/manifests/"
            );
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/manifests/".length()
                );
                if (!StringUtils.contains(
                        suffix,
                        '/'
                )) {
                    String name = fullPath.substring(
                            0,
                            lastIndexOfTags
                    );
                    return manifestResourceHandler.putManifest(
                            name,
                            suffix,
                            httpHeaders.getHeaderString(
                                    "Content-Type"
                            ),
                            inputStream
                    );
                }
            }
        }

        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/blobs/"
            );
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(
                        0,
                        lastIndexOfTags
                );
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/blobs/".length()
                );
                if (suffix.startsWith(
                        "uploads/"
                )) {
                    String suffix2 = suffix.substring(
                            "uploads/".length()
                    );
                    if (!StringUtils.contains(
                            suffix2,
                            '/'
                    )) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUploadPut(
                                name,
                                suffix2,
                                uriInfo.getQueryParameters()
                                        .getFirst(
                                                "digest"
                                        ) != null
                                                ? uriInfo.getQueryParameters()
                                                        .getFirst(
                                                                "digest"
                                                        )
                                                : null,
                                inputStream
                        );
                    }
                    SplitAtFirstUtil.SplitAtFirstResult splitAtFirstResult = SplitAtFirstUtil.splitAtFirstIndex(
                            suffix2,
                            "/"
                    );
                    if (!StringUtils.contains(
                            splitAtFirstResult.first(),
                            '/'
                    )) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUploadChunkPut(
                                name,
                                splitAtFirstResult.first(),
                                splitAtFirstResult.second(),
                                uriInfo.getQueryParameters()
                                        .getFirst(
                                                "digest"
                                        ) != null
                                                ? uriInfo.getQueryParameters()
                                                        .getFirst(
                                                                "digest"
                                                        )
                                                : null,
                                inputStream
                        );
                    }
                }

            }
        }

        log.error(
                "404 not found : PUT /v2/{}",
                fullPath
        );
        return Response.status(
                404
        ).build();
    }

    @DELETE
    @Path(
        "/{fullPath:.*}"
    )
    public Response fullPathDelete(
            @PathParam(
                "fullPath"
            )
            String fullPath,
            @Context
            UriInfo uriInfo,
            @Context
            HttpHeaders httpHeaders
    ) {
        {
            // Handle manifest deletion at /v2/{name}/manifests/{reference}
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/manifests/"
            );
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/manifests/".length()
                );
                if (!StringUtils.contains(
                        suffix,
                        '/'
                )) {
                    String name = fullPath.substring(
                            0,
                            lastIndexOfTags
                    );
                    return manifestResourceHandler.deleteManifest(
                            name,
                            suffix
                    );
                }
            }
        }
        {
            // Handle blob deletion at /v2/{name}/blobs/{digest}
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/blobs/"
            );
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/blobs/".length()
                );
                String name = fullPath.substring(
                        0,
                        lastIndexOfTags
                );
                return blobResourceHandler.deleteBlob(
                        name,
                        suffix
                );
            }
        }
        {
            // Handle repository deletion at /v2/{name}
            // Repository names can contain slashes (e.g., "ubuntu/ubuntu")
            // Remove trailing slash if present
            String repositoryName = fullPath.endsWith(
                    "/"
            )
                    ? fullPath.substring(
                            0,
                            fullPath.length() - 1
                    )
                    : fullPath;

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

            // Delete all manifests (tags) for this repository
            List<String> tags = manifestStorage.findTagsByRepository(
                    repositoryName,
                    null,
                    Integer.MAX_VALUE
            );
            for (String tag : tags) {
                var manifest = manifestStorage.findByRepositoryAndTag(
                        repositoryName,
                        tag
                );
                if (manifest != null) {
                    manifestStorage.delete(
                            manifest.id
                    );
                }
            }

            // Delete the repository
            repositoryStorage.deleteByName(
                    repositoryName
            );

            log.info(
                    "Deleted repository: {} with {} tags",
                    repositoryName,
                    tags.size()
            );
            return Response.status(
                    202
            ).build();
        }
    }

    @PATCH
    @Path(
        "/{fullPath:.*}"
    )
    public Response fullPathPatch(
            @PathParam(
                "fullPath"
            )
            String fullPath,
            @Context
            UriInfo uriInfo,
            @Context
            HttpHeaders httpHeaders,
            InputStream inputStream
    ) {
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf(
                    "/blobs/"
            );
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(
                        0,
                        lastIndexOfTags
                );
                String suffix = fullPath.substring(
                        lastIndexOfTags + "/blobs/".length()
                );
                if (suffix.startsWith(
                        "uploads/"
                )) {
                    String suffix2 = suffix.substring(
                            "uploads/".length()
                    );
                    if (!StringUtils.contains(
                            suffix2,
                            '/'
                    )) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUploadChunkPatch(
                                name,
                                suffix2,
                                httpHeaders.getHeaderString(
                                        "Content-Range"
                                ),
                                inputStream
                        );
                    }
                    SplitAtFirstUtil.SplitAtFirstResult splitAtFirstResult = SplitAtFirstUtil.splitAtFirstIndex(
                            suffix2,
                            "/"
                    );
                    if (!StringUtils.contains(
                            splitAtFirstResult.first(),
                            '/'
                    )) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUploadChunkPatch(
                                name,
                                splitAtFirstResult.first(),
                                splitAtFirstResult.second(),
                                httpHeaders.getHeaderString(
                                        "Content-Range"
                                ),
                                inputStream
                        );
                    }
                }

            }
        }

        log.error(
                "404 not found : PUT /v2/{}",
                fullPath
        );
        return Response.status(
                404
        ).build();
    }

}
