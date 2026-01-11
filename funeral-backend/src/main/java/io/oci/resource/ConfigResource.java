package io.oci.resource;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(
    "/funeral_addition/config"
)
@ApplicationScoped
public class ConfigResource {

    private static final Logger log = LoggerFactory.getLogger(
            ConfigResource.class
    );

    @ConfigProperty(
            name = "oci.auth.enabled",
            defaultValue = "true"
    )
    boolean authEnabled;

    @ConfigProperty(
            name = "oci.auth.allow-anonymous-pull",
            defaultValue = "false"
    )
    boolean allowAnonymousPull;

    @ConfigProperty(
            name = "oci.auth.realm",
            defaultValue = "http://localhost:8911/v2/token"
    )
    String authRealm;

    @GET
    @Path(
        "/auth"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getAuthConfig() {
        log.debug(
                "Fetching auth configuration"
        );

        Map<String, Object> config = new HashMap<>();
        config.put(
                "enabled",
                authEnabled
        );
        config.put(
                "allowAnonymousPull",
                allowAnonymousPull
        );
        config.put(
                "realm",
                authRealm
        );

        return Response.ok(
                config
        ).build();
    }

    @GET
    @Path(
        "/all"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getAllConfig() {
        log.debug(
                "Fetching all configuration"
        );

        Map<String, Object> config = new HashMap<>();

        // Auth config
        Map<String, Object> authConfig = new HashMap<>();
        authConfig.put(
                "enabled",
                authEnabled
        );
        authConfig.put(
                "allowAnonymousPull",
                allowAnonymousPull
        );
        authConfig.put(
                "realm",
                authRealm
        );
        config.put(
                "auth",
                authConfig
        );

        return Response.ok(
                config
        ).build();
    }
}
