package io.oci.service.handler;

import io.oci.annotation.CommentGET;
import io.oci.annotation.CommentPath;
import io.oci.annotation.CommentPathParam;
import io.oci.annotation.CommentQueryParam;
import io.oci.dto.ArtifactDescriptor;
import io.oci.dto.ReferrersResponse;
import io.oci.model.Manifest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;

@CommentPath("/v2/{name}/_oci/referrers/{digest}")
@ApplicationScoped
public class ReferrerResourceHandler {

    @CommentGET
    public Response getReferrers(
            @CommentPathParam("name") String repositoryName,
            @CommentPathParam("digest") String digest,
            @CommentQueryParam("artifactType") String artifactType
    ) {

        if (digest == null || digest.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("digest query parameter is required").build();
        }

//        boolean oCIFiltersAppliedArtifactType;
//        List<Manifest> referrerManifests;
//        if (artifactType != null && !artifactType.isBlank()) {
//            referrerManifests = Manifest.findBySubjectDigestAndArtifactType(repositoryName, digest, artifactType);
//            oCIFiltersAppliedArtifactType = true;
//            OCI-Filters-Applied: artifactType
//        } else {
//            referrerManifests = Manifest.findBySubjectDigest(repositoryName, digest);
//            oCIFiltersAppliedArtifactType = false;
//        }

        List<Manifest> referrerManifests = Manifest.findBySubjectDigest(repositoryName, digest);

        List<ArtifactDescriptor> descriptors = referrerManifests.stream()
                .map(
                        m -> new ArtifactDescriptor(
                        m.mediaType,
                        m.artifactType,
                        m.digest,
                        m.contentLength,
                        m.annotations
                )
                )
                .collect(Collectors.toList());

        return Response
                .ok(new ReferrersResponse(descriptors))
                .header(
                        "Content-Type",
                        "application/vnd.oci.image.index.v1+json"
                )
                .build();
    }
}
