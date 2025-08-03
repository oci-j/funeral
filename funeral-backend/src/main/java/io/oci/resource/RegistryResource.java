package io.oci.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/v2")
public class RegistryResource {

    @GET
    @Path("/")
    public Response checkVersion() {
        return Response.ok().build();
    }
}
