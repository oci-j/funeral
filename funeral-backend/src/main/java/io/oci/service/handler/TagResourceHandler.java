package io.oci.service.handler;

import io.oci.annotation.CommentDefaultValue;
import io.oci.annotation.CommentGET;
import io.oci.annotation.CommentPath;
import io.oci.annotation.CommentPathParam;
import io.oci.annotation.CommentQueryParam;
import io.oci.dto.ErrorResponse;
import io.oci.dto.TagsResponse;
import io.oci.service.FileManifestStorage;
import io.oci.service.FileRepositoryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.List;

@CommentPath("/v2/{name}/tags")
@ApplicationScoped
public class TagResourceHandler {

    @Inject
    FileRepositoryStorage repositoryStorage;

    @Inject
    FileManifestStorage manifestStorage;

    @CommentGET
    @CommentPath("/list")
    public Response listTags(
            @CommentPathParam("name") String repositoryName,
            @CommentQueryParam("n")
            @CommentDefaultValue("100") int limit,
            @CommentQueryParam("last") String last
    ) {
        var repo = repositoryStorage.findByName(repositoryName);
        if (repo == null) {
            return Response.status(404)
                    .entity(new ErrorResponse(List.of(
                            new ErrorResponse.Error("NAME_UNKNOWN", "repository name not known to registry", repositoryName)
                    )))
                    .build();
        }

        List<String> tags = manifestStorage.findTagsByRepository(
                repositoryName,
                last,
                limit
        );
        return Response.ok(new TagsResponse(repositoryName, tags)).build();
    }
}
