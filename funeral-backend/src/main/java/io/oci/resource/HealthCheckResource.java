package io.oci.resource;

import io.oci.dto.HealthCheckResponse;
import io.oci.service.HealthCheckService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(
    "/funeral_addition/health"
)
public class HealthCheckResource {

    private static final Logger log = LoggerFactory.getLogger(
            HealthCheckResource.class
    );

    @Inject
    HealthCheckService healthCheckService;

    @GET
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getHealth() {
        try {
            HealthCheckResponse healthResponse = healthCheckService.checkHealth();

            // Return 200 if overall status is UP, otherwise 503 Service Unavailable
            int statusCode = "UP".equals(
                    healthResponse.status
            ) ? Response.Status.OK.getStatusCode() : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();

            return Response.status(
                    statusCode
            )
                    .entity(
                            healthResponse
                    )
                    .type(
                            MediaType.APPLICATION_JSON
                    )
                    .build();

        }
        catch (Exception e) {
            log.error(
                    "Health check failed unexpectedly",
                    e
            );
            return Response.status(
                    Response.Status.INTERNAL_SERVER_ERROR
            )
                    .entity(
                            "{\"error\":\"Health check failed: " + e.getMessage() + "\"}"
                    )
                    .type(
                            MediaType.APPLICATION_JSON
                    )
                    .build();
        }
    }

    @GET
    @Path(
        "/live"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getLiveness() {
        // Liveness probe - always returns UP if the service is running
        return Response.ok(
                "{\"status\":\"UP\"}"
        )
                .type(
                        MediaType.APPLICATION_JSON
                )
                .build();
    }

    @GET
    @Path(
        "/ready"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getReadiness() {
        try {
            HealthCheckResponse healthResponse = healthCheckService.checkHealth();

            // Readiness probe - returns UP only if all services are healthy
            int statusCode = "UP".equals(
                    healthResponse.status
            ) ? Response.Status.OK.getStatusCode() : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();

            return Response.status(
                    statusCode
            )
                    .entity(
                            healthResponse
                    )
                    .type(
                            MediaType.APPLICATION_JSON
                    )
                    .build();

        }
        catch (Exception e) {
            log.error(
                    "Readiness check failed unexpectedly",
                    e
            );
            return Response.status(
                    Response.Status.SERVICE_UNAVAILABLE
            )
                    .entity(
                            "{\"status\":\"DOWN\",\"error\":\"" + e.getMessage() + "\"}"
                    )
                    .type(
                            MediaType.APPLICATION_JSON
                    )
                    .build();
        }
    }
}
