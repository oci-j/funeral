package io.oci.resource;

import io.oci.dto.ErrorResponse;
import io.oci.model.Blob;
import io.oci.model.Repository;
import io.oci.service.StorageService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Path("/v2/{name}/blobs")
public class BlobResource {

    @Inject
    StorageService storageService;

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
    @Transactional
    public Response startBlobUpload(@PathParam("name") String repositoryName) {

        Repository repo = Repository.find("name", repositoryName).firstResult();
        if (repo == null) {
            repo = new Repository();
            repo.name = repositoryName;
            repo.persist();
        }

        String uploadUuid = UUID.randomUUID().toString();
        String location = "/v2/" + repositoryName + "/blobs/uploads/" + uploadUuid;

        return Response.status(202)
            .header("Location", location)
            .header("Range", "0-0")
            .header("Docker-Upload-UUID", uploadUuid)
            .build();
    }

    @PUT
    @Path("/uploads/{uuid}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public Response completeBlobUpload(@PathParam("name") String repositoryName,
                                     @PathParam("uuid") String uploadUuid,
                                     @QueryParam("digest") String expectedDigest,
                                     InputStream uploadStream) {

        if (expectedDigest == null) {
            return Response.status(400)
                .entity(new ErrorResponse(List.of(
                    new ErrorResponse.Error("DIGEST_INVALID", "provided digest did not match uploaded content", "")
                )))
                .build();
        }

        try {
            String actualDigest = storageService.storeBlob(uploadStream, expectedDigest);

            // Store blob metadata
            Blob existingBlob = Blob.find("digest", actualDigest).firstResult();
            if (existingBlob == null) {
                Blob blob = new Blob();
                blob.digest = actualDigest;
                blob.contentLength = storageService.getBlobSize(actualDigest);
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

    @DELETE
    @Path("/{digest}")
    @Transactional
    public Response deleteBlob(@PathParam("name") String repositoryName,
                             @PathParam("digest") String digest) {

        Blob blob = Blob.find("digest", digest).firstResult();
        if (blob == null) {
            return Response.status(404)
                .entity(new ErrorResponse(List.of(
                    new ErrorResponse.Error("BLOB_UNKNOWN", "blob unknown to registry", digest)
                )))
                .build();
        }

        blob.delete();
        return Response.status(202).build();
    }
}
