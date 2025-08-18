package io.oci.resource;

import io.oci.service.handler.BlobResourceHandler;
import io.oci.service.handler.ManifestResourceHandler;
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
    private static final String MANIFESTS_PATH = "/manifests/";
    private static final String BLOBS_PATH = "/blobs/";
    private static final String TAGS_PATH = "/tags/";
    private static final String UPLOADS_PATH = "/uploads/";

    @Inject
    RegistryResourceHandler registryResourceHandler;

    @Inject
    ManifestResourceHandler manifestResourceHandler;

    @Inject
    TagResourceHandler tagResourceHandler;

    @Inject
    BlobResourceHandler blobResourceHandler;

    @HEAD
    @Path("/{fullPath:.*}")
    public Response fullPathHead(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders
    ) {
        return handleRequest(fullPath, "HEAD", uriInfo, httpHeaders, null);
    }


    @GET
    @Path("/{fullPath:.*}")
    public Response fullPathGet(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders
    ) {
        return handleRequest(fullPath, "GET", uriInfo, httpHeaders, null);
    }

    @POST
    @Path("/{fullPath:.*}")
    public Response fullPathPost(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            InputStream inputStream
    ) {
        return handleRequest(fullPath, "POST", uriInfo, httpHeaders, inputStream);
    }

    @PUT
    @Path("/{fullPath:.*}")
    public Response fullPathPut(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            InputStream inputStream
    ) {
        return handleRequest(fullPath, "PUT", uriInfo, httpHeaders, inputStream);
    }

    @DELETE
    @Path("/{fullPath:.*}")
    public Response fullPathDelete(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders
    ) {
        return handleRequest(fullPath, "DELETE", uriInfo, httpHeaders, null);
    }


    @PATCH
    @Path("/{fullPath:.*}")
    public Response fullPathPatch(
            @PathParam("fullPath") String fullPath,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            InputStream inputStream
    ) {
        return handleRequest(fullPath, "PATCH", uriInfo, httpHeaders, inputStream);
    }

    private Response handleRequest(String fullPath, String method, UriInfo uriInfo, HttpHeaders httpHeaders, InputStream inputStream) {
        // Handle registry-level requests
        if ("GET".equals(method)) {
            switch (fullPath) {
                case "":
                    return registryResourceHandler.checkVersion();
                case "repositories":
                    return registryResourceHandler.listRepositories();
            }
        }

        // Parse path segments
        PathInfo pathInfo = parsePath(fullPath);
        if (pathInfo == null) {
            logError(method, fullPath);
            return Response.status(404).build();
        }

        // Route to appropriate handler
        return routeRequest(pathInfo, method, uriInfo, httpHeaders, inputStream);
    }

    private PathInfo parsePath(String fullPath) {
        int manifestsIndex = fullPath.lastIndexOf(MANIFESTS_PATH);
        if (manifestsIndex != -1) {
            String suffix = fullPath.substring(manifestsIndex + MANIFESTS_PATH.length());
            if (!StringUtils.contains(suffix, '/')) {
                return new PathInfo(PathType.MANIFEST, fullPath.substring(0, manifestsIndex), suffix, null);
            }
        }

        int blobsIndex = fullPath.lastIndexOf(BLOBS_PATH);
        if (blobsIndex != -1) {
            String name = fullPath.substring(0, blobsIndex);
            String suffix = fullPath.substring(blobsIndex + BLOBS_PATH.length());
            
            if (suffix.startsWith("uploads")) {
                String uploadPart = suffix.substring("uploads".length());
                if (uploadPart.isEmpty() || uploadPart.equals("/")) {
                    return new PathInfo(PathType.BLOB_UPLOAD_START, name, null, null);
                }
                if (uploadPart.startsWith("/")) {
                    uploadPart = uploadPart.substring(1);
                    if (!StringUtils.contains(uploadPart, '/')) {
                        return new PathInfo(PathType.BLOB_UPLOAD_COMPLETE, name, uploadPart, null);
                    }
                    SplitAtFirstUtil.SplitAtFirstResult split = SplitAtFirstUtil.splitAtFirstIndex(uploadPart, "/");
                    return new PathInfo(PathType.BLOB_UPLOAD_CHUNK, name, split.first(), split.second());
                }
            } else {
                return new PathInfo(PathType.BLOB, name, suffix, null);
            }
        }

        int tagsIndex = fullPath.lastIndexOf(TAGS_PATH);
        if (tagsIndex != -1) {
            String name = fullPath.substring(0, tagsIndex);
            String suffix = fullPath.substring(tagsIndex + TAGS_PATH.length());
            if ("list".equals(suffix)) {
                return new PathInfo(PathType.TAG_LIST, name, null, null);
            }
        }

        return null;
    }

    private Response routeRequest(PathInfo pathInfo, String method, UriInfo uriInfo, HttpHeaders httpHeaders, InputStream inputStream) {
        switch (pathInfo.type) {
            case MANIFEST:
                return handleManifestRequest(pathInfo, method, httpHeaders, inputStream);
            case BLOB:
                return handleBlobRequest(pathInfo, method);
            case BLOB_UPLOAD_START:
                return handleBlobUploadStart(pathInfo, method, uriInfo, inputStream);
            case BLOB_UPLOAD_COMPLETE:
                return handleBlobUploadComplete(pathInfo, method, uriInfo, httpHeaders, inputStream);
            case BLOB_UPLOAD_CHUNK:
                return handleBlobUploadChunk(pathInfo, method, uriInfo, httpHeaders, inputStream);
            case TAG_LIST:
                return handleTagListRequest(pathInfo, method, uriInfo);
            default:
                logError(method, pathInfo.fullPath);
                return Response.status(404).build();
        }
    }

    private Response handleManifestRequest(PathInfo pathInfo, String method, HttpHeaders httpHeaders, InputStream inputStream) {
        String name = pathInfo.repository;
        String reference = pathInfo.reference;
        
        switch (method) {
            case "HEAD":
                return manifestResourceHandler.headManifest(name, reference);
            case "GET":
                return manifestResourceHandler.getManifest(name, reference);
            case "PUT":
                return manifestResourceHandler.putManifest(name, reference, httpHeaders.getHeaderString("Content-Type"), inputStream);
            case "DELETE":
                return manifestResourceHandler.deleteManifest(name, reference);
            default:
                return Response.status(405).build();
        }
    }

    private Response handleBlobRequest(PathInfo pathInfo, String method) {
        String name = pathInfo.repository;
        String digest = pathInfo.reference;
        
        switch (method) {
            case "HEAD":
                return blobResourceHandler.headBlob(name, digest);
            case "GET":
                return blobResourceHandler.getBlob(name, digest);
            case "DELETE":
                return blobResourceHandler.deleteBlob(name, digest);
            default:
                return Response.status(405).build();
        }
    }

    private Response handleBlobUploadStart(PathInfo pathInfo, String method, UriInfo uriInfo, InputStream inputStream) {
        if (!"POST".equals(method)) {
            return Response.status(405).build();
        }
        
        String digest = getQueryParam(uriInfo, "digest");
        String mount = getQueryParam(uriInfo, "mount");
        String from = getQueryParam(uriInfo, "from");
        
        return blobResourceHandler.startBlobUpload(pathInfo.repository, digest, mount, from, inputStream);
    }

    private Response handleBlobUploadComplete(PathInfo pathInfo, String method, UriInfo uriInfo, HttpHeaders httpHeaders, InputStream inputStream) {
        String digest = getQueryParam(uriInfo, "digest");
        
        switch (method) {
            case "POST":
                return blobResourceHandler.completeBlobUpload(pathInfo.repository, pathInfo.reference, digest, inputStream);
            case "PUT":
                return blobResourceHandler.completeBlobUploadPut(pathInfo.repository, pathInfo.reference, digest, inputStream);
            case "GET":
                return blobResourceHandler.completeBlobUploadChunkGet(pathInfo.repository, pathInfo.reference);
            default:
                return Response.status(405).build();
        }
    }

    private Response handleBlobUploadChunk(PathInfo pathInfo, String method, UriInfo uriInfo, HttpHeaders httpHeaders, InputStream inputStream) {
        switch (method) {
            case "PATCH":
                return blobResourceHandler.completeBlobUploadChunkPatch(
                        pathInfo.repository,
                        pathInfo.reference,
                        pathInfo.chunkIndex,
                        httpHeaders.getHeaderString("Content-Range"),
                        inputStream
                );
            case "PUT":
                String digest = getQueryParam(uriInfo, "digest");
                return blobResourceHandler.completeBlobUploadChunkPut(
                        pathInfo.repository,
                        pathInfo.reference,
                        pathInfo.chunkIndex,
                        digest,
                        inputStream
                );
            default:
                return Response.status(405).build();
        }
    }

    private Response handleTagListRequest(PathInfo pathInfo, String method, UriInfo uriInfo) {
        if (!"GET".equals(method)) {
            return Response.status(405).build();
        }
        
        String last = getQueryParam(uriInfo, "last");
        Integer n = getQueryParamInt(uriInfo, "n", 100);
        
        return tagResourceHandler.listTags(pathInfo.repository, n, last);
    }

    private String getQueryParam(UriInfo uriInfo, String param) {
        return uriInfo.getQueryParameters().getFirst(param);
    }

    private Integer getQueryParamInt(UriInfo uriInfo, String param, int defaultValue) {
        String value = uriInfo.getQueryParameters().getFirst(param);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private void logError(String method, String fullPath) {
        log.error("404 not found : {} /v2/{}", method, fullPath);
    }

    private enum PathType {
        MANIFEST, BLOB, BLOB_UPLOAD_START, BLOB_UPLOAD_COMPLETE, BLOB_UPLOAD_CHUNK, TAG_LIST
    }

    private static class PathInfo {
        final PathType type;
        final String repository;
        final String reference;
        final String chunkIndex;
        final String fullPath;

        PathInfo(PathType type, String repository, String reference, String chunkIndex) {
            this.type = type;
            this.repository = repository;
            this.reference = reference;
            this.chunkIndex = chunkIndex;
            this.fullPath = repository + "/" + type + "/" + reference;
        }
    }
}
