package io.oci.resource;

import com.fasterxml.jackson.jr.ob.JSON;
import io.oci.dto.ErrorResponse;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.service.DigestService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Path("/v2/{name}/manifests")
public class ManifestResource {

    @Inject
    DigestService digestService;

    @HEAD
    @Path("/{reference}")
    public Response headManifest(@PathParam("name") String repositoryName,
                                 @PathParam("reference") String reference) {
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


    @GET
    @Path("/{reference}")
    public Response getManifest(@PathParam("name") String repositoryName,
                                @PathParam("reference") String reference) {

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

    @PUT
    @Path("/{reference}")
    public Response putManifest(@PathParam("name") String repositoryName,
                                @PathParam("reference") String reference,
                                @HeaderParam("Content-Type") String contentType,
                                String manifestContent) {

        if (manifestContent == null || manifestContent.isBlank()) {
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
        String digest = null;
        try {
            Map<String, Object> manifestMap = JSON.std.mapFrom(manifestContent);
            digest = manifestMap.get("digest").toString();
        } catch (Exception e) {
        }
        if (digest == null) {
            // just as a fallback, calculate digest from content
            // TODO I know this is wrong but we just do it for now.
            digest = digestService.calculateDigest(manifestContent);
        }

        // Check if manifest already exists
        Manifest existingManifest = Manifest.findByRepositoryAndDigest(repositoryName, digest);
        if (existingManifest != null) {
            // Update tag if reference is not a digest
            if (!reference.startsWith("sha256:")) {
                existingManifest.tag = reference;
                existingManifest.update();
            }
            return Response.status(201)
                    .header("Location", "/v2/" + repositoryName + "/manifests/" + digest)
                    .header("Docker-Content-Digest", digest)
                    .build();
        }

        Manifest manifest = new Manifest();
        manifest.repositoryId = repo.id;
        manifest.repositoryName = repositoryName;
        manifest.digest = digest;
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

        return Response.status(201)
                .header("Location", "/v2/" + repositoryName + "/manifests/" + digest)
                .header("Docker-Content-Digest", digest)
                .build();
    }

    @DELETE
    @Path("/{reference}")

    public Response deleteManifest(@PathParam("name") String repositoryName,
                                   @PathParam("reference") String reference) {

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
