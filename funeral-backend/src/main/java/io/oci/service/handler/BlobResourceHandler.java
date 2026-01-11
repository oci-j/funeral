package io.oci.service.handler;

import io.oci.annotation.CommentDELETE;
import io.oci.annotation.CommentGET;
import io.oci.annotation.CommentHEAD;
import io.oci.annotation.CommentHeaderParam;
import io.oci.annotation.CommentPATCH;
import io.oci.annotation.CommentPOST;
import io.oci.annotation.CommentPUT;
import io.oci.annotation.CommentPath;
import io.oci.annotation.CommentPathParam;
import io.oci.annotation.CommentQueryParam;
import io.oci.dto.ErrorResponse;
import io.oci.exception.WithResponseException;
import io.oci.model.Blob;
import io.oci.model.Repository;
import io.oci.service.AbstractStorageService;
import io.oci.service.BlobStorage;
import io.oci.service.RepositoryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommentPath("/v2/{name}/blobs")
@ApplicationScoped
public class BlobResourceHandler {

    @Inject
    @Named("repositoryStorage")
    RepositoryStorage repositoryStorage;

    @Inject
    @Named("blobStorage")
    BlobStorage blobStorage;

    private static final Logger log = LoggerFactory.getLogger(BlobResourceHandler.class);

    @Inject
    @Named("storage")
    AbstractStorageService storageService;

    @CommentHEAD
    @CommentPath("/{digest}")
    public Response headBlob(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("digest") String digest
    ) {
        try {
            long size = storageService.getBlobSize(digest);
            return Response.ok()
                    .header("Content-Length", size)
                    .header("Docker-Content-Digest", digest)
                    .build();
        } catch (Exception e) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("BLOB_UNKNOWN", "blob unknown to registry", digest)
                    )))
                    .build();
        }
    }

    @CommentGET
    @CommentPath("/{digest}")
    public Response getBlob(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("digest") String digest
    ) {

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

    @CommentPOST
    @CommentPath("/uploads/")
    public Response startBlobUpload(
            @CommentPathParam("name") String repositoryName,
            @CommentQueryParam("digest") String digest,
            @CommentQueryParam("mount") String mount,
            @CommentQueryParam("from") String from,
            InputStream uploadStream
    ) {
        if (StringUtils.isNotBlank(mount)) {
            try {
                long blobSize = storageService.getBlobSize(digest);
                if (blobSize > 0) {
                    String responseLocationRepository = StringUtils.isNotBlank(from) ? from : repositoryName;
                    return Response.status(201)
                            .header("Location", "/v2/" + responseLocationRepository + "/blobs/" + digest)
                            .header("OCI-Chunk-Min-Length", 1 << 24)
                            .build();
                }
            } catch (Exception e) {
            }
        }

        var repo = repositoryStorage.findByName(repositoryName);
        if (repo == null) {
            repo = new Repository(repositoryName);
            repositoryStorage.persist(repo);
        }

        String uploadUuid = UUID.randomUUID().toString();

        if (StringUtils.isNotBlank(digest)) {
            return completeBlobUpload(
                    repositoryName,
                    uploadUuid,
                    digest,
                    uploadStream
            );
        }

        String location = "/v2/" + repositoryName + "/blobs/uploads/" + uploadUuid;

        return Response.status(202)
                .header("Location", location)
                .header("Docker-Upload-UUID", uploadUuid)
                .header("OCI-Chunk-Min-Length", 1 << 24)
                .build();
    }

    @CommentPOST
    @CommentPath("/uploads/{uuid}")
    public Response completeBlobUpload(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("uuid") String uploadUuid,
            @CommentQueryParam("digest") String expectedDigest,
            InputStream uploadStream
    ) {
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
            Blob existingBlob = blobStorage.findByDigest(actualDigest);
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
                    .header("OCI-Chunk-Min-Length", 1 << 24)
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

    @CommentPUT
    @CommentPath("/uploads/{uuid}")
    public Response completeBlobUploadPut(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("uuid") String uploadUuid,
            @CommentQueryParam("digest") String expectedDigest,
            InputStream uploadStream
    ) {
        return completeBlobUpload(
                repositoryName,
                uploadUuid,
                expectedDigest,
                uploadStream
        );
    }

    @CommentPATCH
    @CommentPath("/uploads/{uuid}")
    public Response completeBlobUploadChunkPatch(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("uuid") String uploadUuid,
            @CommentHeaderParam("Content-Range") String contentRange,
            InputStream uploadStream
    ) {
        return this.completeBlobUploadChunkPatch(repositoryName, uploadUuid, "0_0", contentRange, uploadStream);
    }

    @CommentPATCH
    @CommentPath("/uploads/{uuid}/{index_and_start_bytes}")
    public Response completeBlobUploadChunkPatch(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("uuid") String uploadUuid,
            @CommentPathParam("index_and_start_bytes") String indexAndStartBytes,
            @CommentHeaderParam("Content-Range") String contentRange,
            InputStream uploadStream
    ) {
        try {
            String[] split = StringUtils.split(indexAndStartBytes, '_');
            int index = Integer.parseInt(split[0]);
            long bytesWritten = storageService.storeTempChunk(uploadStream, uploadUuid, index);
            long startBytes = Long.parseLong(split[1]);
            long endBytes = startBytes + bytesWritten;
            if (StringUtils.isNotBlank(contentRange)) {
                try {
                    String[] contentRangeSplit = StringUtils.split(contentRange, '-');
                    if (contentRangeSplit.length != 2) {
                        return Response.status(416).build();
                    }
                    if (startBytes != Long.parseLong(contentRangeSplit[0])) {
                        return Response.status(416).build();
                    }
                    if (endBytes - 1 != Long.parseLong(contentRangeSplit[1])) {
                        return Response.status(416).build();
                    }
                } catch (Exception e) {
                    return Response.status(416).build();
                }
            }
            String location = "/v2/" + repositoryName + "/blobs/uploads/" + uploadUuid + "/" + (index + 1) + "_" + endBytes;
            return Response.status(202)
                    .header("Location", location)
                    .header("Range", "0-" + (endBytes - 1))
                    .header("OCI-Chunk-Min-Length", 1 << 24)
                    .build();
        } catch (WithResponseException e) {
            log.error("completeBlobUploadChunkPatch failed WithResponseException", e);
            return e.getResponse();
        } catch (Exception e) {
            log.error("completeBlobUploadChunkPatch failed", e);
            return Response.status(500).build();
        }
    }

    @CommentPUT
    @CommentPath("/uploads/{uuid}/{index_and_start_bytes}")
    public Response completeBlobUploadChunkPut(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("uuid") String uploadUuid,
            @CommentPathParam("index_and_start_bytes") String indexAndStartBytes,
            @CommentQueryParam("digest") String digest,
            InputStream uploadStream
    ) {
        try {
            String[] split = StringUtils.split(indexAndStartBytes, '_');
            int index = Integer.parseInt(split[0]);

            storageService.storeTempChunk(uploadStream, uploadUuid, index);
            storageService.mergeTempChunks(uploadUuid, index, digest);

            //TODO actualDigest actual
            String actualDigest = digest;

            // Store blob metadata
            Blob existingBlob = blobStorage.findByDigest(actualDigest);
            if (existingBlob == null) {
                Blob blob = new Blob();
                blob.digest = actualDigest;
                blob.contentLength = storageService.getBlobSize(actualDigest);
                blobStorage.persist(blob);
            }

            String location = "/v2/" + repositoryName + "/blobs/" + actualDigest;
            return Response.status(201)
                    .header("Location", location)
                    .header("Docker-Content-Digest", actualDigest)
                    .header("OCI-Chunk-Min-Length", 1 << 24)
                    .build();
        } catch (WithResponseException e) {
            log.error("completeBlobUploadChunkPatch failed WithResponseException", e);
            return e.getResponse();
        } catch (Exception e) {
            log.error("completeBlobUploadChunkPatch failed", e);
            return Response.status(500).build();
        }
    }

    @CommentGET
    @CommentPath("/uploads/{uuid}/")
    public Response completeBlobUploadChunkGet(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("uuid") String uploadUuid
    ) {
        try {
            AbstractStorageService.CalculateTempChunkResult result = storageService.calculateTempChunks(uploadUuid);
            int index = result.index();
            long bytesWritten = result.bytesWritten();

            String location = "/v2/" + repositoryName + "/blobs/uploads/" + uploadUuid + "/" + index + "_" + bytesWritten;
            return Response.status(204)
                    .header("Location", location)
                    .header("Range", "0-" + (bytesWritten - 1))
                    .header("OCI-Chunk-Min-Length", 1 << 24)
                    .build();
        } catch (Exception e) {
            log.error("completeBlobUploadChunkPatch failed", e);
            return Response.status(500).build();
        }
    }

    @CommentDELETE
    @CommentPath("/{digest}")
    public Response deleteBlob(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("digest") String digest
    ) {

        Blob blob = blobStorage.findByDigest(digest);
        if (blob == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("BLOB_UNKNOWN", "blob unknown to registry", digest)
                    )))
                    .build();
        }

        try {
            storageService.deleteBlob(digest);
            blobStorage.delete(blob.id);
            return Response.status(202).build();
        } catch (IOException e) {
            return Response.status(500).build();
        }
    }

}
