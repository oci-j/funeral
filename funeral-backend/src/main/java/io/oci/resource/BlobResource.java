package io.oci.resource;

import io.oci.dto.ErrorResponse;
import io.oci.model.Blob;
import io.oci.model.Repository;
import io.oci.service.S3StorageService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/v2/{name}/blobs")
public class BlobResource {

    private static final Logger log = LoggerFactory.getLogger(BlobResource.class);
    @Inject
    S3StorageService storageService;

    @HEAD
    @Path("/{digest}")
    public Response headBlob(@PathParam("name") String repositoryName,
                             @PathParam("digest") String digest) {

        if (!storageService.blobExists(digest)) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("BLOB_UNKNOWN", "blob unknown to registry", digest)
                    )))
                    .build();
        }

        try {
            long size = storageService.getBlobSize(digest);
            return Response.ok()
                    .header("Content-Length", size)
                    .header("Docker-Content-Digest", digest)
                    .build();
        } catch (IOException e) {
            return Response.status(500).build();
        }
    }

    @GET
    @Path("/{digest}")
    public Response getBlob(@PathParam("name") String repositoryName,
                            @PathParam("digest") String digest) {

        try {
            InputStream blobStream = storageService.getBlobStream(digest);
            if (blobStream == null) {
                return Response.status(404)
                        .entity(new ErrorResponse(List.of(
                                new ErrorResponse.Error("BLOB_UNKNOWN", "blob unknown to registry", digest)
                        )))
                        .build();
            }

            long size = storageService.getBlobSize(digest);
            return Response.ok(blobStream)
                    .header("Content-Length", size)
                    .header("Docker-Content-Digest", digest)
                    .build();

        } catch (IOException e) {
            return Response.status(500).build();
        }
    }

    @POST
    @Path("/uploads/")
    public Response startBlobUpload(
            @PathParam("name") String repositoryName,
            @QueryParam("digest") String digest,
            @QueryParam("mount") String mount,
            @QueryParam("from") String from,
            InputStream uploadStream
    ) {
        if (digest != null && !digest.isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("UNSUPPORTED", "digest in /uploads/", "")
                    )))
                    .build();
        }
        if (mount != null && !mount.isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("UNSUPPORTED", "mount in /uploads/", "")
                    )))
                    .build();
        }
        if (from != null && !from.isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("UNSUPPORTED", "from in /uploads/", "")
                    )))
                    .build();
        }

        Repository repo = Repository.findByName(repositoryName);
        if (repo == null) {
            repo = new Repository(repositoryName);
            repo.persist();
        }


        String uploadUuid = UUID.randomUUID().toString();
        String location = "/v2/" + repositoryName + "/blobs/uploads/" + uploadUuid;

        return Response.status(202)
                .header("Location", location)
                .header("Docker-Upload-UUID", uploadUuid)
                .build();
    }

    @POST
    @Path("/uploads/{uuid}")
    public Response completeBlobUpload(@PathParam("name") String repositoryName,
                                       @PathParam("uuid") String uploadUuid,
                                       @QueryParam("digest") String expectedDigest,
                                       InputStream uploadStream) {
        log.info("Completing blob upload for repository: {}, UUID: {}", repositoryName, uploadUuid);
        if (expectedDigest == null) {
            log.info("digest is null");
            return Response.status(400)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("DIGEST_INVALID", "provided digest did not match uploaded content", "")
                    )))
                    .build();
        }

        try {
            String actualDigest = storageService.storeBlob(uploadStream, expectedDigest);

            // Store blob metadata
            Blob existingBlob = Blob.findByDigest(actualDigest);
            if (existingBlob == null) {
                Blob blob = new Blob();
                blob.digest = actualDigest;
                blob.contentLength = storageService.getBlobSize(actualDigest);
                blob.s3Key = "blobs/" + actualDigest.replace(":", "/");
                blob.s3Bucket = "oci-registry"; // Should be configurable
                blob.persist();
            }

            String location = "/v2/" + repositoryName + "/blobs/" + actualDigest;
            return Response.status(201)
                    .header("Location", location)
                    .header("Docker-Content-Digest", actualDigest)
                    .build();

        } catch (IllegalArgumentException e) {
            return Response.status(400)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("DIGEST_INVALID", "provided digest did not match uploaded content", expectedDigest)
                    )))
                    .build();
        } catch (IOException e) {
            return Response.status(500).build();
        }
    }

    @PATCH
    @Path("/uploads/{uuid}")
    public Response completeBlobUploadChunkPatch(@PathParam("name") String repositoryName,
                                                 @PathParam("uuid") String uploadUuid,
                                                 @HeaderParam("Content-Range") String contentRange,
                                                 InputStream uploadStream) {
        return this.completeBlobUploadChunkPatch(repositoryName, uploadUuid, "0_0", contentRange, uploadStream);
    }

    @PATCH
    @Path("/uploads/{uuid}/{index_and_start_bytes}")
    public Response completeBlobUploadChunkPatch(@PathParam("name") String repositoryName,
                                                 @PathParam("uuid") String uploadUuid,
                                                 @PathParam("index_and_start_bytes") String indexAndStartBytes,
                                                 @HeaderParam("Content-Range") String contentRange,
                                                 InputStream uploadStream) {
        try {
            String[] split = StringUtils.split(indexAndStartBytes, '_');
            int index = Integer.parseInt(split[0]);
            long bytesWritten = storageService.storeTempChunk(
                    uploadStream,
                    uploadUuid,
                    index
            );
            long startBytes = Long.parseLong(split[1]);
            long endBytes = startBytes + bytesWritten;
            String location = "/v2/" + repositoryName + "/blobs/uploads/" + uploadUuid + "/" + (index + 1) + "_" + endBytes;
            return Response.status(202)
                    .header("Location", location)
                    .header("Range", "0-" + endBytes)
                    .build();
        } catch (Exception e) {
            log.error("completeBlobUploadChunkPatch failed", e);
            return Response.status(500).build();
        }
    }

    @PUT
    @Path("/uploads/{uuid}/{index_and_start_bytes}")
    public Response completeBlobUploadChunkPut(@PathParam("name") String repositoryName,
                                               @PathParam("uuid") String uploadUuid,
                                               @PathParam("index_and_start_bytes") String indexAndStartBytes,
                                               @QueryParam("digest") String digest,
                                               InputStream uploadStream) {
        try {
            String[] split = StringUtils.split(indexAndStartBytes, '_');
            int index = Integer.parseInt(split[0]);
            storageService.storeTempChunk(
                    uploadStream,
                    uploadUuid,
                    index
            );

            storageService.mergeTempChunks(
                    uploadUuid,
                    index,
                    digest
            );

            //TODO actualDigest actual
            String actualDigest = digest;

            // Store blob metadata
            Blob existingBlob = Blob.findByDigest(actualDigest);
            if (existingBlob == null) {
                Blob blob = new Blob();
                blob.digest = actualDigest;
                blob.contentLength = storageService.getBlobSize(actualDigest);
                blob.s3Key = "blobs/" + actualDigest.replace(":", "/");
                blob.s3Bucket = "oci-registry"; // Should be configurable
                blob.persist();
            }

            String location = "/v2/" + repositoryName + "/blobs/" + actualDigest;
            return Response.status(201)
                    .header("Location", location)
                    .header("Docker-Content-Digest", actualDigest)
                    .build();
        } catch (Exception e) {
            log.error("completeBlobUploadChunkPatch failed", e);
            return Response.status(500).build();
        }
    }

    @DELETE
    @Path("/{digest}")
    public Response deleteBlob(@PathParam("name") String repositoryName,
                               @PathParam("digest") String digest) {

        Blob blob = Blob.findByDigest(digest);
        if (blob == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("BLOB_UNKNOWN", "blob unknown to registry", digest)
                    )))
                    .build();
        }

        try {
            storageService.deleteBlob(digest);
            blob.delete();
            return Response.status(202).build();
        } catch (IOException e) {
            return Response.status(500).build();
        }
    }
}
