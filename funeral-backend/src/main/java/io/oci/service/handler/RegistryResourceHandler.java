package io.oci.service.handler;

import io.oci.annotation.CommentGET;
import io.oci.annotation.CommentPath;
import io.oci.dto.RepositoryInfo;
import io.oci.service.FileManifestStorage;
import io.oci.service.FileRepositoryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@CommentPath("/v2")
@ApplicationScoped
public class RegistryResourceHandler {

    @Inject
    FileRepositoryStorage repositoryStorage;

    @Inject
    FileManifestStorage manifestStorage;

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
        var repos = repositoryStorage.listAll();

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
                    long tagCount = manifestStorage.countByRepository(repo.name);
                    return new RepositoryInfo(repo.name, repo.createdAt, repo.updatedAt, tagCount);
                })
                .sorted(java.util.Comparator.comparing(r -> r.name))
                .collect(Collectors.toList());

        return Response.ok(repoList).build();
    }

}
