package io.oci.resource;

import io.oci.dto.ErrorResponse;
import io.oci.dto.UserRequest;
import io.oci.dto.UserResponse;
import io.oci.model.RepositoryPermission;
import io.oci.model.User;
import io.oci.service.UserStorage;
import io.oci.service.RepositoryPermissionStorage;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Path("/funeral_addition/admin")
@ApplicationScoped
@Authenticated
public class AdminResource {

    private static final Logger log = Logger.getLogger(AdminResource.class);

    @Inject
    @Named("userStorage")
    UserStorage userStorage;

    @Inject
    @Named("repositoryPermissionStorage")
    RepositoryPermissionStorage permissionStorage;

    @ConfigProperty(name = "quarkus.http.auth.basic", defaultValue = "false")
    boolean basicAuthEnabled;

    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUsers(@Context io.quarkus.security.identity.SecurityIdentity identity) {
        // Check if user is admin
        if (!isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("FORBIDDEN", "Access denied: Admin privileges required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        List<UserResponse> users = userStorage.listAll().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return Response.ok(users).build();
    }

    @GET
    @Path("/users/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUser(@PathParam("username") String username,
                           @Context io.quarkus.security.identity.SecurityIdentity identity) {
        // Check if user is admin
        if (!isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("FORBIDDEN", "Access denied: Admin privileges required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        User user = userStorage.findByUsername(username);
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("NOT_FOUND", "User not found: " + username, username)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        return Response.ok(toUserResponse(user)).build();
    }

    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(UserRequest request,
                              @Context io.quarkus.security.identity.SecurityIdentity identity) {
        // Check if user is admin
        if (!isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("FORBIDDEN", "Access denied: Admin privileges required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        // Validation
        if (request.username == null || request.username.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("BAD_REQUEST", "Username is required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        if (request.password == null || request.password.length() < 6) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("BAD_REQUEST", "Password must be at least 6 characters", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        // Check if user already exists
        if (userStorage.findByUsername(request.username) != null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("CONFLICT", "User already exists: " + request.username, request.username)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        // Create new user
        User user = new User();
        user.username = request.username;
        user.passwordHash = hashPassword(request.password);
        user.email = request.email;
        user.enabled = request.enabled != null ? request.enabled : true;
        user.roles = request.roles;
        user.allowedRepositories = request.allowedRepositories;
        user.updatedAt = LocalDateTime.now();

        userStorage.persist(user);
        log.info("User created: " + user.username);

        return Response.status(Response.Status.CREATED)
                .entity(toUserResponse(user))
                .build();
    }

    @PUT
    @Path("/users/{username}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateUser(@PathParam("username") String username,
                              UserRequest request,
                              @Context io.quarkus.security.identity.SecurityIdentity identity) {
        // Check if user is admin
        if (!isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("FORBIDDEN", "Access denied: Admin privileges required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        User user = userStorage.findByUsername(username);
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("NOT_FOUND", "User not found: " + username, username)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        // Update fields
        if (request.password != null && !request.password.isEmpty()) {
            if (request.password.length() < 6) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse(java.util.List.of(
                                new ErrorResponse.Error("BAD_REQUEST", "Password must be at least 6 characters", null)
                        )).toJson())
                        .type("application/json")
                        .build();
            }
            user.passwordHash = hashPassword(request.password);
        }

        if (request.email != null) {
            user.email = request.email;
        }

        if (request.enabled != null) {
            user.enabled = request.enabled;
        }

        if (request.roles != null) {
            user.roles = request.roles;
        }

        if (request.allowedRepositories != null) {
            user.allowedRepositories = request.allowedRepositories;
        }

        user.updatedAt = LocalDateTime.now();
        userStorage.persist(user);
        log.info("User updated: " + user.username);

        return Response.ok(toUserResponse(user)).build();
    }

    @DELETE
    @Path("/users/{username}")
    public Response deleteUser(@PathParam("username") String username,
                              @Context io.quarkus.security.identity.SecurityIdentity identity) {
        // Check if user is admin
        if (!isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("FORBIDDEN", "Access denied: Admin privileges required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        // Prevent deleting the last admin
        User currentUser = userStorage.findByUsername(username);
        if (currentUser != null && currentUser.isAdmin()) {
            long adminCount = userStorage.listAll().stream()
                    .filter(User::isAdmin)
                    .count();
            if (adminCount <= 1) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse(java.util.List.of(
                                new ErrorResponse.Error("BAD_REQUEST", "Cannot delete the last admin user", null)
                        )).toJson())
                        .type("application/json")
                        .build();
            }
        }

        // Delete user permissions first
        permissionStorage.deleteByUsername(username);

        // Delete user
        userStorage.deleteByUsername(username);
        log.info("User deleted: " + username);

        return Response.noContent().build();
    }

    @GET
    @Path("/permissions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listPermissions(@Context io.quarkus.security.identity.SecurityIdentity identity) {
        if (!isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("FORBIDDEN", "Access denied: Admin privileges required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        return Response.ok(permissionStorage.listAll()).build();
    }

    @GET
    @Path("/permissions/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserPermissions(@PathParam("username") String username,
                                      @Context io.quarkus.security.identity.SecurityIdentity identity) {
        if (!isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("FORBIDDEN", "Access denied: Admin privileges required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        List<RepositoryPermission> permissions = permissionStorage.findByUsername(username);
        return Response.ok(permissions).build();
    }

    @POST
    @Path("/permissions/{username}/{repository}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setUserPermission(@PathParam("username") String username,
                                     @PathParam("repository") String repository,
                                     RepositoryPermission request,
                                     @Context io.quarkus.security.identity.SecurityIdentity identity) {
        if (!isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("FORBIDDEN", "Access denied: Admin privileges required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        // Check if user exists
        if (userStorage.findByUsername(username) == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("NOT_FOUND", "User not found: " + username, username)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        RepositoryPermission permission = permissionStorage.findByUsernameAndRepository(username, repository);
        if (permission == null) {
            permission = new RepositoryPermission(username, repository);
        }

        permission.canPull = request.canPull != null ? request.canPull : false;
        permission.canPush = request.canPush != null ? request.canPush : false;

        permissionStorage.persist(permission);
        log.infov("Permission updated for user {0} on repository {1}: pull={2}, push={3}",
                username, repository, permission.canPull, permission.canPush);

        return Response.ok(permission).build();
    }

    @DELETE
    @Path("/permissions/{username}/{repository}")
    public Response deleteUserPermission(@PathParam("username") String username,
                                        @PathParam("repository") String repository,
                                        @Context io.quarkus.security.identity.SecurityIdentity identity) {
        if (!isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(java.util.List.of(
                            new ErrorResponse.Error("FORBIDDEN", "Access denied: Admin privileges required", null)
                    )).toJson())
                    .type("application/json")
                    .build();
        }

        permissionStorage.deleteByUsernameAndRepository(username, repository);
        log.infov("Permission deleted for user {0} on repository {1}", username, repository);

        return Response.noContent().build();
    }

    private boolean isAdmin(io.quarkus.security.identity.SecurityIdentity identity) {
        String username = identity.getPrincipal().getName();
        User user = userStorage.findByUsername(username);
        return user != null && user.isAdmin();
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.username = user.username;
        response.email = user.email;
        response.enabled = user.enabled;
        response.roles = user.roles;
        response.allowedRepositories = user.allowedRepositories;
        response.createdAt = user.createdAt;
        response.updatedAt = user.updatedAt;
        return response;
    }

    private String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }
}
