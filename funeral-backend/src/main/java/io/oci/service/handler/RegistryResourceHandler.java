package io.oci.service.handler;

import io.oci.annotation.CommentGET;
import io.oci.annotation.CommentPath;
import io.oci.dto.RepositoryInfo;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@CommentPath("/v2")
@ApplicationScoped
public class RegistryResourceHandler {

    @CommentGET
    @CommentPath("/")
    public Response checkVersion() {
        return Response.ok()
                .header("Docker-Distribution-API-Version", "registry/2.0")
                .build();
    }

    @CommentGET
    @CommentPath("/repositories")
    public Response listRepositories() {
        List<Repository> repos = Repository.listAll();

        // Group by name and keep only the one with max updatedAt
        List<RepositoryInfo> repoList = repos.stream()
                .collect(Collectors.groupingBy(
                        repo -> repo.name,
                        Collectors.collectingAndThen(
                                Collectors.maxBy((r1, r2) -> r1.updatedAt.compareTo(r2.updatedAt)),
                                optRepo -> optRepo.orElse(null)
                        )
                ))
                .values()
                .stream()
                .filter(repo -> repo != null)
                .map(repo -> {
                    long tagCount = Manifest.countByRepository(repo.name);
                    return new RepositoryInfo(repo.name, repo.createdAt, repo.updatedAt, tagCount);
                })
                .collect(Collectors.toList());

        return Response.ok(repoList).build();
    }

}
