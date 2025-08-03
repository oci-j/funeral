package io.oci.resource;

import io.oci.dto.RepositoryInfo;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/v2")
public class RegistryResource {

    @GET
    @Path("/")
    public Response checkVersion() {
        return Response.ok().build();
    }

    @GET
    @Path("/repositories")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listRepositories() {
        List<Repository> repos = Repository.listAll();

        List<RepositoryInfo> repoList = repos.stream()
            .map(repo -> {
                long tagCount = Manifest.countByRepository(repo.name);
                return new RepositoryInfo(repo.name, repo.createdAt, repo.updatedAt, tagCount);
            })
            .collect(Collectors.toList());

        return Response.ok(repoList).build();
    }
}
