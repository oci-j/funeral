package io.oci.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import io.oci.dto.ArtifactDescriptor;
import io.oci.dto.ReferrersResponse;
import io.oci.model.Manifest;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;

@Path("/v2/{name}/_oci/referrers")
public class ReferrerResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReferrers(@PathParam("name") String repositoryName,
                                 @QueryParam("digest") String digest,
                                 @QueryParam("artifactType") String artifactType) {

        if (digest == null || digest.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("digest query parameter is required").build();
        }

        List<Manifest> referrerManifests;
        if (artifactType != null && !artifactType.isBlank()) {
            referrerManifests = Manifest.findBySubjectDigestAndArtifactType(repositoryName, digest, artifactType);
        } else {
            referrerManifests = Manifest.findBySubjectDigest(repositoryName, digest);
        }

        List<ArtifactDescriptor> descriptors = referrerManifests.stream()
                .map(m -> new ArtifactDescriptor(m.mediaType, m.artifactType, m.digest, m.contentLength))
                .collect(Collectors.toList());

        return Response.ok(new ReferrersResponse(descriptors)).build();
    }
}
