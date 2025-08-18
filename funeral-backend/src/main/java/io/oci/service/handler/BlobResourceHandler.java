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
import io.oci.service.S3StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.oci.util.StringValidationUtil;

@CommentPath("/v2/{name}/blobs")
@ApplicationScoped
public class BlobResourceHandler {

    private static final Logger log = LoggerFactory.getLogger(BlobResourceHandler.class);
    private static final int CHUNK_MIN_LENGTH = 1 << 24; // 16MB
    private static final String OCI_REGISTRY_BUCKET = "oci-registry";
    private static final String BLOBS_PREFIX = "blobs/";

    @Inject
    S3StorageService storageService;

    @CommentHEAD
    @CommentPath("/{digest}")
    public Response headBlob(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("digest") String digest
    ) {
        return getBlobInfo(digest, false, null);
    }

    @CommentGET
    @CommentPath("/{digest}")
    public Response getBlob(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("digest") String digest
    ) {
        return getBlobInfo(digest, true, repositoryName);
    }

    private Response getBlobInfo(String digest, boolean includeContent, String repositoryName) {
        try {
            if (!validateDigest(digest)) {
                return createErrorResponse("DIGEST_INVALID", "invalid digest format", digest);
            }
            
            long size = storageService.getBlobSize(digest);
            if (size < 0) {
                return createErrorResponse("BLOB_UNKNOWN", "blob unknown to registry", digest);
            }

            Response.ResponseBuilder responseBuilder = Response.ok()
                    .header("Content-Length", size)
                    .header("Docker-Content-Digest", digest)
                    .header("OCI-Chunk-Min-Length", CHUNK_MIN_LENGTH);

            if (includeContent) {
                InputStream blobStream = storageService.getBlobStream(digest);
                if (blobStream == null) {
                    return createErrorResponse("BLOB_UNKNOWN", "blob unknown to registry", digest);
                }
                responseBuilder.entity(blobStream);
            }

            return responseBuilder.build();

        } catch (IOException e) {
            log.error("Error accessing blob: {}", digest, e);
            return Response.status(500).build();
        } catch (Exception e) {
            log.error("Unexpected error accessing blob: {}", digest, e);
            return createErrorResponse("BLOB_UNKNOWN", "blob unknown to registry", digest);
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
        try {
            // Check for mount request
            if (StringUtils.isNotBlank(mount) && StringUtils.isNotBlank(digest)) {
                if (canMountBlob(digest)) {
                    String targetRepository = StringUtils.isNotBlank(from) ? from : repositoryName;
                    return createMountResponse(targetRepository, digest);
                }
            }

            // Ensure repository exists
            ensureRepositoryExists(repositoryName);

            String uploadUuid = UUID.randomUUID().toString();

            // If digest provided, complete upload immediately
            if (StringUtils.isNotBlank(digest)) {
                return completeBlobUpload(repositoryName, uploadUuid, digest, uploadStream);
            }

            return createUploadStartResponse(repositoryName, uploadUuid);

        } catch (Exception e) {
            log.error("Error starting blob upload for repository: {}", repositoryName, e);
            return Response.status(500).build();
        }
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
            Blob existingBlob = Blob.findByDigest(actualDigest);
            if (existingBlob == null) {
                Blob blob = createBlobMetadata(actualDigest);
                blob.persist();
            }

            return createBlobUploadCompleteResponse(repositoryName, actualDigest);

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
            long bytesWritten = storageService.storeTempChunk(
                    uploadStream,
                    uploadUuid,
                    index
            );
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
                    .header("OCI-Chunk-Min-Length", CHUNK_MIN_LENGTH)
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
                    .header("OCI-Chunk-Min-Length", CHUNK_MIN_LENGTH)
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
            S3StorageService.CalculateTempChunkResult calculateTempChunkResult = storageService.calculateTempChunks(uploadUuid);
            String location = "/v2/" + repositoryName + "/blobs/uploads/" + uploadUuid + "/" + calculateTempChunkResult.index() + "_" + calculateTempChunkResult.bytesWritten();
            return Response.status(204)
                    .header("Location", location)
                    .header("Range", "0-" + (calculateTempChunkResult.bytesWritten() - 1))
                    .header("OCI-Chunk-Min-Length", CHUNK_MIN_LENGTH)
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
        try {
            if (!validateDigest(digest)) {
                return createErrorResponse("DIGEST_INVALID", "invalid digest format", digest);
            }

            Blob blob = Blob.findByDigest(digest);
            if (blob == null) {
                return createErrorResponse("BLOB_UNKNOWN", "blob unknown to registry", digest);
            }

            storageService.deleteBlob(digest);
            blob.delete();
            return Response.status(202).build();
        } catch (IOException e) {
            log.error("Error deleting blob: {}", digest, e);
            return Response.status(500).build();
        }
    }

    private boolean validateDigest(String digest) {
        return StringValidationUtil.isValidDigest(digest);
    }

    private boolean canMountBlob(String digest) {
        try {
            return storageService.getBlobSize(digest) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureRepositoryExists(String repositoryName) {
        Repository repo = Repository.findByName(repositoryName);
        if (repo == null) {
            repo = new Repository(repositoryName);
            repo.persist();
        }
    }

    private Response createMountResponse(String repositoryName, String digest) {
        return Response.status(201)
                .header("Location", "/v2/" + repositoryName + "/blobs/" + digest)
                .header("OCI-Chunk-Min-Length", CHUNK_MIN_LENGTH)
                .build();
    }

    private Response createUploadStartResponse(String repositoryName, String uploadUuid) {
        String location = "/v2/" + repositoryName + "/blobs/uploads/" + uploadUuid;
        return Response.status(202)
                .header("Location", location)
                .header("Docker-Upload-UUID", uploadUuid)
                .header("OCI-Chunk-Min-Length", CHUNK_MIN_LENGTH)
                .build();
    }

    private Response createErrorResponse(String code, String message, String detail) {
        return Response.status(404)
                .entity(new ErrorResponse(List.of(
                        new ErrorResponse.Error(code, message, detail)
                )))
                .build();
    }

    private Blob createBlobMetadata(String digest) throws IOException {
        Blob blob = new Blob();
        blob.digest = digest;
        blob.contentLength = storageService.getBlobSize(digest);
        blob.s3Key = BLOBS_PREFIX + digest.replace(":", "/");
        blob.s3Bucket = OCI_REGISTRY_BUCKET;
        return blob;
    }

    private Response createBlobUploadCompleteResponse(String repositoryName, String digest) {
        String location = "/v2/" + repositoryName + "/blobs/" + digest;
        return Response.status(201)
                .header("Location", location)
                .header("Docker-Content-Digest", digest)
                .header("OCI-Chunk-Min-Length", CHUNK_MIN_LENGTH)
                .build();
    }

}
