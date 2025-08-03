package io.oci.resource;

import io.oci.dto.ErrorResponse;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.service.DigestService;
import io.oci.service.StorageService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/v2/{name}/manifests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ManifestResource {

    @Inject
    DigestService digestService;

    @Inject
    StorageService storageService;

    @GET
    @Path("/{reference}")
    public Response getManifest(@PathParam("name") String repositoryName,
                                @PathParam("reference") String reference) {

        Repository repo = Repository.find("name", repositoryName).firstResult();
        if (repo == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("NAME_UNKNOWN", "repository name not known to registry", repositoryName)
                    )))
                    .build();
        }

        Manifest manifest;
        if (reference.startsWith("sha256:")) {
            manifest = Manifest.find("repository = ?1 and digest = ?2", repo, reference).firstResult();
        } else {
            manifest = Manifest.find("repository = ?1 and tag = ?2", repo, reference).firstResult();
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
    @Transactional
    public Response putManifest(@PathParam("name") String repositoryName,
                                @PathParam("reference") String reference,
                                @HeaderParam("Content-Type") String contentType,
                                String manifestContent) {

        if (manifestContent == null || manifestContent.trim().isEmpty()) {
            return Response.status(400)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("MANIFEST_INVALID", "manifest invalid", "empty manifest")
                    )))
                    .build();
        }

        Repository repo = Repository.find("name", repositoryName).firstResult();
        if (repo == null) {
            repo = new Repository();
            repo.name = repositoryName;
            repo.persist();
        }

        String digest = digestService.calculateDigest(manifestContent);

        // Check if manifest already exists
        Manifest existingManifest = Manifest.find("digest", digest).firstResult();
        if (existingManifest != null) {
            // Update tag if reference is not a digest
            if (!reference.startsWith("sha256:")) {
                existingManifest.tag = reference;
                existingManifest.persist();
            }
            return Response.status(201)
                    .header("Location", "/v2/" + repositoryName + "/manifests/" + digest)
                    .header("Docker-Content-Digest", digest)
                    .build();
        }

        Manifest manifest = new Manifest();
        manifest.repository = repo;
        manifest.digest = digest;
        manifest.mediaType = contentType != null ? contentType : "application/vnd.docker.distribution.manifest.v2+json";
        manifest.content = manifestContent;
        manifest.contentLength = (long) manifestContent.getBytes().length;

        if (!reference.startsWith("sha256:")) {
            manifest.tag = reference;
        }

        manifest.persist();

        return Response.status(201)
                .header("Location", "/v2/" + repositoryName + "/manifests/" + digest)
                .header("Docker-Content-Digest", digest)
                .build();
    }

    @DELETE
    @Path("/{reference}")
    @Transactional
    public Response deleteManifest(@PathParam("name") String repositoryName,
                                   @PathParam("reference") String reference) {

        Repository repo = Repository.find("name", repositoryName).firstResult();
        if (repo == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("NAME_UNKNOWN", "repository name not known to registry", repositoryName)
                    )))
                    .build();
        }

        Manifest manifest;
        if (reference.startsWith("sha256:")) {
            manifest = Manifest.find("repository = ?1 and digest = ?2", repo, reference).firstResult();
        } else {
            manifest = Manifest.find("repository = ?1 and tag = ?2", repo, reference).firstResult();
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

    @GET
    @Path("/repositories")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listRepositories() {
        List<Repository> repos = Repository.listAll();

        List<Map<String, Object>> repoList = repos.stream()
                .map(repo -> {
                    Map<String, Object> repoInfo = new HashMap<>();
                    repoInfo.put("name", repo.name);
                    repoInfo.put("createdAt", repo.createdAt);
                    repoInfo.put("updatedAt", repo.updatedAt);

                    // Count tags for this repository
                    long tagCount = Manifest.count("repository = ?1 and tag is not null", repo);
                    repoInfo.put("tagCount", tagCount);

                    return repoInfo;
                })
                .collect(Collectors.toList());

        return Response.ok(repoList).build();
    }

}
