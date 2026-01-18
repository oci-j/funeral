package io.oci.filter;

import java.util.Set;

import io.oci.model.User;
import io.oci.service.RepositoryPermissionStorage;
import io.oci.service.UserStorage;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.JsonString;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Priority(
    Priorities.AUTHENTICATION
)
@ApplicationScoped
public class AuthenticationFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(
            AuthenticationFilter.class
    );

    @Inject
    JsonWebToken jwt;

    @Inject
    @Named(
        "userStorage"
    )
    UserStorage userStorage;

    @Inject
    @Named(
        "repositoryPermissionStorage"
    )
    RepositoryPermissionStorage permissionStorage;

    @ConfigProperty(
            name = "oci.auth.enabled",
            defaultValue = "true"
    )
    boolean authEnabled;

    @ConfigProperty(
            name = "oci.auth.realm",
            defaultValue = "http://localhost:8911/v2/token"
    )
    String authRealm;

    @ConfigProperty(
            name = "oci.auth.service",
            defaultValue = "funeral-registry"
    )
    String authServiceName;

    @ConfigProperty(
            name = "oci.auth.allow-anonymous-pull",
            defaultValue = "false"
    )
    boolean allowAnonymousPull;

    private static final Set<String> WRITE_METHODS = Set.of(
            "POST",
            "PUT",
            "PATCH",
            "DELETE"
    );

    @Override
    public void filter(
            ContainerRequestContext requestContext
    ) {
        if (!authEnabled) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        if (path.equals(
                "v2/token"
        ) || path.equals(
                "/v2/token"
        )) {
            return;
        }

        if (!path.startsWith(
                "/v2"
        ) && !path.startsWith(
                "v2"
        ) && !path.startsWith(
                "/funeral_addition/write/upload"
        ) && !path.startsWith(
                "funeral_addition/write/upload"
        ) && !path.startsWith(
                "/funeral_addition/admin"
        ) && !path.startsWith(
                "funeral_addition/admin"
        )) {
            return;
        }

        String authHeader = requestContext.getHeaderString(
                "Authorization"
        );

        if (authHeader == null || !authHeader.startsWith(
                "Bearer "
        )) {
            abortWithUnauthorized(
                    requestContext,
                    path
            );
            return;
        }

        String username = jwt.getSubject();

        if (username == null) {
            abortWithUnauthorized(
                    requestContext,
                    path
            );
            return;
        }

        if (!(this.allowAnonymousPull && "anonymous".equals(
                username
        ))) {
            User user = userStorage.findByUsername(
                    username
            );

            if (user == null || !user.enabled) {
                abortWithUnauthorized(
                        requestContext,
                        path
                );
                return;
            }
        }

        // Get repository name from path
        String repositoryName = extractRepositoryName(
                path
        );
        if (repositoryName != null) {
            // Check repository permissions based on authentication status
            if (WRITE_METHODS.contains(
                    method
            )) {
                // Check push permission - requires authentication
                if (!permissionStorage.hasPushPermission(
                        username,
                        repositoryName
                )) {
                    abortWithForbidden(
                            requestContext,
                            "push",
                            repositoryName
                    );
                    return;
                }
            }
            else {
                // Check pull permission
                // Authenticated user - check permissions
                if (!(this.allowAnonymousPull && "anonymous".equals(
                        username
                ))) {
                    if (!permissionStorage.hasPullPermission(
                            username,
                            repositoryName
                    )) {
                        abortWithForbidden(
                                requestContext,
                                "pull",
                                repositoryName
                        );
                        return;
                    }
                }
            }
        }

        // Additional scope check for backward compatibility
        Object actionsClaim = jwt.getClaim(
                "actions"
        );
        if (actionsClaim == null && repositoryName == null) {
            abortWithForbidden(
                    requestContext
            );
            return;
        }

        if (repositoryName == null && WRITE_METHODS.contains(
                method
        )) {
            boolean hasPush = false;
            if (actionsClaim instanceof java.util.Collection<?> actions) {
                hasPush = actions.contains(
                        "push"
                );
                if (!hasPush) {
                    for (Object action : actions) {
                        if (action instanceof JsonString && ((JsonString) action).getString()
                                .equals(
                                        "push"
                                )) {
                            hasPush = true;
                            break;
                        }
                    }
                }
            }
            else if (actionsClaim instanceof String actionsStr) {
                hasPush = actionsStr.contains(
                        "push"
                );
            }

            if (!hasPush) {
                abortWithForbidden(
                        requestContext
                );
            }
        }
    }

    private String extractRepositoryName(
            String path
    ) {
        if (!path.startsWith(
                "/v2"
        ) && !path.startsWith(
                "v2"
        )) {
            return null;
        }

        String cleanPath = path.replaceFirst(
                "^/?v2/?",
                ""
        );
        if (cleanPath.isEmpty()) {
            return null;
        }

        int blobsIndex = cleanPath.lastIndexOf(
                "/blobs/"
        );
        int manifestsIndex = cleanPath.lastIndexOf(
                "/manifests/"
        );
        int tagsIndex = cleanPath.lastIndexOf(
                "/tags/"
        );

        int maxIndex = Math.max(
                Math.max(
                        blobsIndex,
                        manifestsIndex
                ),
                tagsIndex
        );
        if (maxIndex > 0) {
            return cleanPath.substring(
                    0,
                    maxIndex
            );
        }

        // Check if it's a repository deletion request
        if (!cleanPath.contains(
                "/"
        )) {
            return cleanPath;
        }

        return null;
    }

    private void abortWithUnauthorized(
            ContainerRequestContext requestContext,
            String path
    ) {
        String scope = buildScope(
                path
        );

        String wwwAuthenticate = String.format(
                "Bearer realm=\"%s\",service=\"%s\"%s",
                authRealm,
                authServiceName,
                scope != null ? ",scope=\"" + scope + "\"" : ""
        );

        log.warn(
                "WWW-Authenticate: {}",
                wwwAuthenticate
        );

        requestContext.abortWith(
                Response.status(
                        Response.Status.UNAUTHORIZED
                )
                        .header(
                                "WWW-Authenticate",
                                wwwAuthenticate
                        )
                        .header(
                                "Docker-Distribution-API-Version",
                                "registry/2.0"
                        )
                        .build()
        );
    }

    private void abortWithForbidden(
            ContainerRequestContext requestContext
    ) {
        requestContext.abortWith(
                Response.status(
                        Response.Status.FORBIDDEN
                )
                        .header(
                                "Docker-Distribution-API-Version",
                                "registry/2.0"
                        )
                        .entity(
                                "{\"errors\":[{\"code\":\"DENIED\",\"message\":\"requested access to the resource is denied\"}]}"
                        )
                        .build()
        );
    }

    private void abortWithForbidden(
            ContainerRequestContext requestContext,
            String action,
            String repository
    ) {
        requestContext.abortWith(
                Response.status(
                        Response.Status.FORBIDDEN
                )
                        .header(
                                "Docker-Distribution-API-Version",
                                "registry/2.0"
                        )
                        .entity(
                                String.format(
                                        "{\"errors\":[{\"code\":\"DENIED\",\"message\":\"requested %s access to repository '%s' is denied\"}]}",
                                        action,
                                        repository
                                )
                        )
                        .build()
        );
    }

    private String buildScope(
            String path
    ) {
        String cleanPath = path.replaceFirst(
                "^/?v2/?",
                ""
        );
        if (cleanPath.isEmpty()) {
            return null;
        }

        int blobsIndex = cleanPath.lastIndexOf(
                "/blobs/"
        );
        int manifestsIndex = cleanPath.lastIndexOf(
                "/manifests/"
        );
        int tagsIndex = cleanPath.lastIndexOf(
                "/tags/"
        );

        int maxIndex = Math.max(
                Math.max(
                        blobsIndex,
                        manifestsIndex
                ),
                tagsIndex
        );
        if (maxIndex > 0) {
            String repoName = cleanPath.substring(
                    0,
                    maxIndex
            );
            return "repository:" + repoName + ":pull,push";
        }

        return null;
    }
}
