package io.oci.resource;

import java.util.List;

import io.oci.model.Blob;
import io.oci.model.Manifest;
import io.oci.service.BlobStorage;
import io.oci.service.ManifestStorage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(
    "/debug"
)
public class DebugResource {

    private static final Logger log = LoggerFactory.getLogger(
            DebugResource.class
    );

    @Inject
    @Named(
        "manifestStorage"
    )
    ManifestStorage manifestStorage;

    @Inject
    @Named(
        "blobStorage"
    )
    BlobStorage blobStorage;

    @GET
    @Path(
        "/manifest/{repository}/{tag}"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getManifestDebug(
            @PathParam(
                "repository"
            )
            String repository,
            @PathParam(
                "tag"
            )
            String tag
    ) {
        Manifest manifest = manifestStorage.findByRepositoryAndTag(
                repository,
                tag
        );
        if (manifest == null) {
            return Response.status(
                    404
            )
                    .entity(
                            "Manifest not found"
                    )
                    .build();
        }

        return Response.ok()
                .entity(
                        manifest.content
                )
                .type(
                        MediaType.APPLICATION_JSON
                )
                .build();
    }

    @GET
    @Path(
        "/blob/{digest}"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getBlobDebug(
            @PathParam(
                "digest"
            )
            String digest
    ) {
        Blob blob = blobStorage.findByDigest(
                digest
        );
        if (blob == null) {
            return Response.status(
                    404
            )
                    .entity(
                            "Blob not found"
                    )
                    .build();
        }

        String debugInfo = String.format(
                "{\n" + "  \"digest\": \"%s\",\n" + "  \"contentLength\": %d,\n" + "  \"mediaType\": \"%s\"\n" + "}",
                blob.digest,
                blob.contentLength,
                blob.mediaType
        );

        return Response.ok()
                .entity(
                        debugInfo
                )
                .type(
                        MediaType.APPLICATION_JSON
                )
                .build();
    }

    @GET
    @Path(
        "/manifests/list"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response listAllManifests() {
        List<Manifest> manifests = manifestStorage.listAll();
        StringBuilder sb = new StringBuilder(
                "["
        );
        for (Manifest manifest : manifests) {
            sb.append(
                    String.format(
                            "{\n" + "  \"repository\": \"%s\",\n" + "  \"tag\": \"%s\",\n"
                                    + "  \"configDigest\": \"%s\",\n" + "  \"digest\": \"%s\",\n"
                                    + "  \"contentLength\": %d\n" + "},",
                            manifest.repositoryName,
                            manifest.tag,
                            manifest.configDigest,
                            manifest.digest,
                            manifest.contentLength
                    )
            );
        }
        if (sb.charAt(
                sb.length() - 1
        ) == ',') {
            sb.setLength(
                    sb.length() - 1
            );
        }
        sb.append(
                "]"
        );

        return Response.ok()
                .entity(
                        sb.toString()
                )
                .type(
                        MediaType.APPLICATION_JSON
                )
                .build();
    }
}
