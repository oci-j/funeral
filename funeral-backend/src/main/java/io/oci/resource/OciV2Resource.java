package io.oci.resource;

import io.oci.annotation.CommentPathParam;
import io.oci.annotation.CommentQueryParam;
import io.oci.service.handler.BlobResourceHandler;
import io.oci.service.handler.ManifestResourceHandler;
import io.oci.service.handler.ReferrerResourceHandler;
import io.oci.service.handler.RegistryResourceHandler;
import io.oci.service.handler.TagResourceHandler;
import io.oci.util.SplitAtFirstUtil;
import jakarta.inject.Inject;
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
import java.io.InputStream;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/v2")
public class OciV2Resource {

    private static final Logger log = LoggerFactory.getLogger(OciV2Resource.class);

    private static final Pattern TAG_PATTERN = Pattern.compile("[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}");

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

    @HEAD
    @Path("/{fullPath:.*}")
    public Response fullPathHead(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders
    ) {
        {
            /// @see ManifestResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/manifests/");
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(lastIndexOfTags + "/manifests/".length());
                if (!StringUtils.contains(suffix, '/')) {
                    String name = fullPath.substring(0, lastIndexOfTags);
                    return manifestResourceHandler.headManifest(
                            name,
                            suffix
                    );
                }
            }
        }
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/blobs/");
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(lastIndexOfTags + "/blobs/".length());
                String name = fullPath.substring(0, lastIndexOfTags);
                return blobResourceHandler.headBlob(
                        name,
                        suffix
                );
            }
        }

        log.error("404 not found : HEAD /v2/{}", fullPath);
        return Response.status(404).build();
    }


    @GET
    @Path("/{fullPath:.*}")
    public Response fullPathGet(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders
    ) {
        {
            /// @see RegistryResourceHandler
            switch (fullPath) {
                case "":
                    return registryResourceHandler.checkVersion();
                case "repositories":
                    return registryResourceHandler.listRepositories();
                default:
                    //pass
            }
        }
        {
            /// @see ManifestResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/manifests/");
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(lastIndexOfTags + "/manifests/".length());
                if (!StringUtils.contains(suffix, '/')) {
                    String name = fullPath.substring(0, lastIndexOfTags);
                    return manifestResourceHandler.getManifest(
                            name,
                            suffix
                    );
                }
            }
        }
        {
            /// @see TagResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/tags/");
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(lastIndexOfTags + "/tags/".length());
                switch (suffix) {
                    case "list":
                        String name = fullPath.substring(0, lastIndexOfTags);
                        return tagResourceHandler.listTags(
                                name,
                                uriInfo.getQueryParameters().getFirst("n") != null ? Integer.parseInt(uriInfo.getQueryParameters().getFirst("n")) : 100,
                                uriInfo.getQueryParameters().getFirst("last")
                        );
                    default:
                        //pass
                }
            }
        }
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/blobs/");
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(0, lastIndexOfTags);
                String suffix = fullPath.substring(lastIndexOfTags + "/blobs/".length());
                if (suffix.startsWith("uploads/")) {
                    // Handle blob uploads
                    String uploadUuid = suffix.substring("uploads/".length());
                    if (!StringUtils.contains(uploadUuid, '/')) {
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
            int lastIndexOfTags = fullPath.lastIndexOf("/referrers/");
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(0, lastIndexOfTags);
                String digest = fullPath.substring(lastIndexOfTags + "/referrers/".length());
                return referrerResourceHandler.getReferrers(
                        name,
                        digest,
                        uriInfo.getQueryParameters().getFirst("artifactType")
                );
            }
        }
        log.error("404 not found : GET /v2/{}", fullPath);
        return Response.status(404).build();
    }

    @POST
    @Path("/{fullPath:.*}")
    public Response fullPathPost(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            InputStream inputStream
    ) {
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/blobs/");
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(0, lastIndexOfTags);
                String suffix = fullPath.substring(lastIndexOfTags + "/blobs/".length());
                switch (suffix) {
                    case "uploads":
                        return blobResourceHandler.startBlobUpload(
                                name,
                                uriInfo.getQueryParameters().getFirst("digest") != null ? uriInfo.getQueryParameters().getFirst("digest") : null,
                                uriInfo.getQueryParameters().getFirst("mount") != null ? uriInfo.getQueryParameters().getFirst("mount") : null,
                                uriInfo.getQueryParameters().getFirst("from") != null ? uriInfo.getQueryParameters().getFirst("from") : null,
                                inputStream
                        );
                    default:
                        //pass
                }
                if (suffix.startsWith("uploads/")) {
                    String suffix2 = suffix.substring("uploads/".length());
                    if (!StringUtils.contains(suffix2, '/')) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUpload(
                                name,
                                suffix2,
                                uriInfo.getQueryParameters().getFirst("digest") != null ? uriInfo.getQueryParameters().getFirst("digest") : null,
                                inputStream
                        );
                    }
                }
            }
        }
        log.error("404 not found : POST /v2/{}", fullPath);
        return Response.status(404).build();
    }

    @PUT
    @Path("/{fullPath:.*}")
    public Response fullPathPut(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            InputStream inputStream
    ) {
        {
            /// @see ManifestResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/manifests/");
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(lastIndexOfTags + "/manifests/".length());
                if (!StringUtils.contains(suffix, '/')) {
                    String name = fullPath.substring(0, lastIndexOfTags);
                    return manifestResourceHandler.putManifest(
                            name,
                            suffix,
                            httpHeaders.getHeaderString("Content-Type"),
                            inputStream
                    );
                }
            }
        }

        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/blobs/");
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(0, lastIndexOfTags);
                String suffix = fullPath.substring(lastIndexOfTags + "/blobs/".length());
                if (suffix.startsWith("uploads/")) {
                    String suffix2 = suffix.substring("uploads/".length());
                    if (!StringUtils.contains(suffix2, '/')) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUploadPut(
                                name,
                                suffix2,
                                uriInfo.getQueryParameters().getFirst("digest") != null ? uriInfo.getQueryParameters().getFirst("digest") : null,
                                inputStream
                        );
                    }
                    SplitAtFirstUtil.SplitAtFirstResult splitAtFirstResult = SplitAtFirstUtil.splitAtFirstIndex(suffix2, "/");
                    if (!StringUtils.contains(splitAtFirstResult.first(), '/')) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUploadChunkPut(
                                name,
                                splitAtFirstResult.first(),
                                splitAtFirstResult.second(),
                                uriInfo.getQueryParameters().getFirst("digest") != null ? uriInfo.getQueryParameters().getFirst("digest") : null,
                                inputStream
                        );
                    }
                }

            }
        }

        log.error("404 not found : PUT /v2/{}", fullPath);
        return Response.status(404).build();
    }

    @DELETE
    @Path("/{fullPath:.*}")
    public Response fullPathDelete(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders
    ) {
        {
            /// @see ManifestResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/manifests/");
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(lastIndexOfTags + "/manifests/".length());
                if (!StringUtils.contains(suffix, '/')) {
                    String name = fullPath.substring(0, lastIndexOfTags);
                    return manifestResourceHandler.deleteManifest(
                            name,
                            suffix
                    );
                }
            }
        }
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/blobs/");
            if (lastIndexOfTags != -1) {
                String suffix = fullPath.substring(lastIndexOfTags + "/blobs/".length());
                String name = fullPath.substring(0, lastIndexOfTags);
                return blobResourceHandler.deleteBlob(
                        name,
                        suffix
                );
            }
        }
        log.error("404 not found : DELETE /v2/{}", fullPath);
        return Response.status(404).build();
    }


    @PATCH
    @Path("/{fullPath:.*}")
    public Response fullPathPatch(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            InputStream inputStream
    ) {
        {
            /// @see BlobResourceHandler
            int lastIndexOfTags = fullPath.lastIndexOf("/blobs/");
            if (lastIndexOfTags != -1) {
                String name = fullPath.substring(0, lastIndexOfTags);
                String suffix = fullPath.substring(lastIndexOfTags + "/blobs/".length());
                if (suffix.startsWith("uploads/")) {
                    String suffix2 = suffix.substring("uploads/".length());
                    if (!StringUtils.contains(suffix2, '/')) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUploadChunkPatch(
                                name,
                                suffix2,
                                httpHeaders.getHeaderString("Content-Range"),
                                inputStream
                        );
                    }
                    SplitAtFirstUtil.SplitAtFirstResult splitAtFirstResult = SplitAtFirstUtil.splitAtFirstIndex(suffix2, "/");
                    if (!StringUtils.contains(splitAtFirstResult.first(), '/')) {
                        // Handle specific upload ID
                        return blobResourceHandler.completeBlobUploadChunkPatch(
                                name,
                                splitAtFirstResult.first(),
                                splitAtFirstResult.second(),
                                httpHeaders.getHeaderString("Content-Range"),
                                inputStream
                        );
                    }
                }

            }
        }

        log.error("404 not found : PUT /v2/{}", fullPath);
        return Response.status(404).build();
    }

}
