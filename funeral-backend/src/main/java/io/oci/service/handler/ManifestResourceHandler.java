package io.oci.service.handler;

import io.oci.annotation.CommentDELETE;
import io.oci.annotation.CommentGET;
import io.oci.annotation.CommentHEAD;
import io.oci.annotation.CommentHeaderParam;
import io.oci.annotation.CommentPUT;
import io.oci.annotation.CommentPath;
import io.oci.annotation.CommentPathParam;
import io.oci.dto.ErrorResponse;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.service.DigestService;
import io.oci.util.JsonUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@CommentPath("/v2/{name}/manifests")
@ApplicationScoped
public class ManifestResourceHandler {

    @Inject
    DigestService digestService;

    @CommentHEAD
    @CommentPath("/{reference}")
    public Response headManifest(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("reference") String reference
    ) {
        Repository repo = Repository.findByName(repositoryName);
        if (repo == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("NAME_UNKNOWN", "repository name not known to registry", repositoryName)
                    )))
                    .build();
        }

        Manifest manifest;
        if (reference.startsWith("sha256:")) {
            manifest = Manifest.findByRepositoryAndDigest(repositoryName, reference);
        } else {
            manifest = Manifest.findByRepositoryAndTag(repositoryName, reference);
        }

        if (manifest == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("MANIFEST_UNKNOWN", "manifest unknown", reference)
                    )))
                    .build();
        }

        return Response.ok()
                .header("Content-Type", manifest.mediaType)
                .header("Docker-Content-Digest", manifest.digest)
                .header("Content-Length", manifest.contentLength)
                .build();
    }


    @CommentGET
    @CommentPath("/{reference}")
    public Response getManifest(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("reference") String reference
    ) {

        Repository repo = Repository.findByName(repositoryName);
        if (repo == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("NAME_UNKNOWN", "repository name not known to registry", repositoryName)
                    )))
                    .build();
        }

        Manifest manifest;
        if (reference.startsWith("sha256:")) {
            manifest = Manifest.findByRepositoryAndDigest(repositoryName, reference);
        } else {
            manifest = Manifest.findByRepositoryAndTag(repositoryName, reference);
        }

        if (manifest == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("MANIFEST_UNKNOWN", "manifest unknown", reference)
                    )))
                    .build();
        }

        return Response.ok(manifest.content)
                .header("Content-Type", manifest.mediaType)
                .header("Docker-Content-Digest", manifest.digest)
                .header("Content-Length", manifest.contentLength)
                .build();
    }

    @CommentPUT
    @CommentPath("/{reference}")
    public Response putManifest(@CommentPathParam("name") String repositoryName,
                                @CommentPathParam("reference") String reference,
                                @CommentHeaderParam("Content-Type") String contentType,
                                InputStream inputStream
    ) {
        String manifestContent = null;
        try {
            manifestContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (manifestContent.isBlank()) {
            return Response.status(400)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("MANIFEST_INVALID", "manifest invalid", "empty manifest")
                    )))
                    .build();
        }

        Repository repo = Repository.findByName(repositoryName);
        if (repo == null) {
            repo = new Repository(repositoryName);
            repo.persist();
        }
        Manifest manifest = null;
        try {
            manifest = JsonUtil.fromJson(manifestContent, Manifest.class);
        } catch (Exception ignored) {
            manifest = new Manifest();
        }
        if (manifest.digest == null) {
            // just as a fallback, calculate digest from content
            // TODO I know this is wrong but we just do it for now.
            manifest.digest = digestService.calculateDigest(manifestContent);
        }

        // Check if manifest already exists
        Manifest existingManifest = Manifest.findByRepositoryAndDigest(repositoryName, manifest.digest);
        if (existingManifest != null) {
            // Update tag if reference is not a digest
            if (!reference.startsWith("sha256:")) {
                existingManifest.tag = reference;
                existingManifest.update();
            }
            return Response.status(201)
                    .header("Location", "/v2/" + repositoryName + "/manifests/" + manifest.digest)
                    .header("Docker-Content-Digest", manifest.digest)
                    .build();
        }


        manifest.repositoryId = repo.id;
        manifest.repositoryName = repositoryName;
        manifest.mediaType = contentType != null ? contentType : "application/vnd.docker.distribution.manifest.v2+json";
        manifest.content = manifestContent;
        manifest.contentLength = (long) manifestContent.getBytes().length;
        if (!reference.startsWith("sha256:")) {
            manifest.tag = reference;
        }

        manifest.persist();

        // Update repository timestamp
        repo.updateTimestamp();
        repo.update();

        Response.ResponseBuilder responseBuilder = Response.status(201)
                .header("Location", "/v2/" + repositoryName + "/manifests/" + manifest.digest)
                .header("Docker-Content-Digest", manifest.digest);
        if (manifest.subject != null && StringUtils.isNotBlank(manifest.subject.digest)) {
            responseBuilder = responseBuilder.header("OCI-Subject", manifest.subject.digest);
        }
        return responseBuilder.build();
    }

    @CommentDELETE
    @CommentPath("/{reference}")
    public Response deleteManifest(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("reference") String reference
    ) {

        Repository repo = Repository.findByName(repositoryName);
        if (repo == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("NAME_UNKNOWN", "repository name not known to registry", repositoryName)
                    )))
                    .build();
        }

        Manifest manifest;
        if (reference.startsWith("sha256:")) {
            manifest = Manifest.findByRepositoryAndDigest(repositoryName, reference);
        } else {
            manifest = Manifest.findByRepositoryAndTag(repositoryName, reference);
        }

        if (manifest == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("MANIFEST_UNKNOWN", "manifest unknown", reference)
                    )))
                    .build();
        }

        manifest.delete();
        return Response.status(202).build();
    }
}
