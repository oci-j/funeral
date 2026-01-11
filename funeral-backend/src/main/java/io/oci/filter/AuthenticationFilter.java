package io.oci.filter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Collection;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    @Inject
    JsonWebToken jwt;

    @ConfigProperty(name = "oci.auth.enabled", defaultValue = "true")
    boolean authEnabled;

    @ConfigProperty(name = "oci.auth.realm", defaultValue = "http://localhost:8911/v2/token")
    String authRealm;

    @ConfigProperty(name = "oci.auth.service", defaultValue = "funeral-registry")
    String authServiceName;

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!authEnabled) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        if (path.equals("v2/token") || path.equals("/v2/token")) {
            return;
        }

        if (!path.startsWith("/v2") && !path.startsWith("v2")) {
            return;
        }

//        if (!WRITE_METHODS.contains(method) &&
//                !("v2".equals(path) || "/v2".equals(path) || "v2/".equals(path) || "/v2/".equals(path))
//        ) {
//            return;
//        }

        String authHeader = requestContext.getHeaderString("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            abortWithUnauthorized(requestContext, path);
            return;
        }

        if (jwt == null || jwt.getSubject() == null) {
            abortWithUnauthorized(requestContext, path);
            return;
        }

        Object actionsClaim = jwt.getClaim("actions");
        if (actionsClaim == null) {
            abortWithForbidden(requestContext);
            return;
        }

        if (WRITE_METHODS.contains(method)) {
            boolean hasPush = false;
            if (actionsClaim instanceof java.util.Collection<?> actions) {
                hasPush = actions.contains("push");
                if (!hasPush) {
                    for (Object action : actions) {
                        if (action instanceof JsonString && ((JsonString) action).getString().equals("push")) {
                            hasPush = true;
                            break;
                        }
                    }
                }
            } else if (actionsClaim instanceof String actionsStr) {
                hasPush = actionsStr.contains("push");
            }

            if (!hasPush) {
                abortWithForbidden(requestContext);
            }
        }
    }

    private void abortWithUnauthorized(ContainerRequestContext requestContext, String path) {
        String scope = buildScope(path);

        String wwwAuthenticate = String.format(
                "Bearer realm=\"%s\",service=\"%s\"%s",
                authRealm,
                authServiceName,
                scope != null ? ",scope=\"" + scope + "\"" : ""
        );

        requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .header("WWW-Authenticate", wwwAuthenticate)
                        .header("Docker-Distribution-API-Version", "registry/2.0")
                        .build()
        );
    }

    private void abortWithForbidden(ContainerRequestContext requestContext) {
        requestContext.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                        .header("Docker-Distribution-API-Version", "registry/2.0")
                        .entity("{\"errors\":[{\"code\":\"DENIED\",\"message\":\"requested access to the resource is denied\"}]}")
                        .build()
        );
    }

    private String buildScope(String path) {
        String cleanPath = path.replaceFirst("^/?v2/?", "");
        if (cleanPath.isEmpty()) {
            return null;
        }

        int blobsIndex = cleanPath.lastIndexOf("/blobs/");
        int manifestsIndex = cleanPath.lastIndexOf("/manifests/");
        int tagsIndex = cleanPath.lastIndexOf("/tags/");

        int maxIndex = Math.max(Math.max(blobsIndex, manifestsIndex), tagsIndex);
        if (maxIndex > 0) {
            String repoName = cleanPath.substring(0, maxIndex);
            return "repository:" + repoName + ":pull,push";
        }

        return null;
    }
}
