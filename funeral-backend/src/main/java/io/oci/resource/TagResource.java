package io.oci.resource;

import io.oci.dto.ErrorResponse;
import io.oci.dto.TagsResponse;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/v2/{name}/tags")
@Produces(MediaType.APPLICATION_JSON)
public class TagResource {

    @GET
    @Path("/list")
    public Response listTags(@PathParam("name") String repositoryName,
                           @QueryParam("n") @DefaultValue("100") int limit,
                           @QueryParam("last") String last) {

        Repository repo = Repository.find("name", repositoryName).firstResult();
        if (repo == null) {
            return Response.status(404)
                .entity(new ErrorResponse(List.of(
                    new ErrorResponse.Error("NAME_UNKNOWN", "repository name not known to registry", repositoryName)
                )))
                .build();
        }

        List<String> tags = Manifest.find("repository = ?1 and tag is not null", repo)
            .project(String.class)
            .list();

        // Apply pagination if needed
        if (last != null) {
            int lastIndex = tags.indexOf(last);
            if (lastIndex >= 0 && lastIndex + 1 < tags.size()) {
                tags = tags.subList(lastIndex + 1, Math.min(tags.size(), lastIndex + 1 + limit));
            }
        } else {
            tags = tags.subList(0, Math.min(tags.size(), limit));
        }

        return Response.ok(new TagsResponse(repositoryName, tags)).build();
    }
}
