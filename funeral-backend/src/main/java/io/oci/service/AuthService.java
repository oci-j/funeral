package io.oci.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.oci.dto.TokenResponse;
import io.oci.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class AuthService {

    @Inject
    JwtService jwtService;

    @Inject
    @Named("userStorage")
    UserStorage userStorage;

    @ConfigProperty(name = "oci.auth.enabled", defaultValue = "true")
    boolean authEnabled;

    @ConfigProperty(name = "oci.auth.jwt.expiration-seconds", defaultValue = "3600")
    int expirationSeconds;

    public TokenResponse authenticate(String username, String password, String service, String scope) {
        User user = userStorage.findByUsername(username);
        if (user == null || !user.enabled) {
            return null;
        }

        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash);
        if (!result.verified) {
            return null;
        }

        String repositoryName = parseScopeRepository(scope);
        if (repositoryName != null && !user.hasAccessToRepository(repositoryName)) {
            return null;
        }

        String accessToken = jwtService.generateToken(user, scope);
        return new TokenResponse(accessToken, "Bearer", expirationSeconds);
    }

    public TokenResponse authenticateWithAnonymousUser(String service) {
        User user = new User();
        user.username = "temp_" + UUID.randomUUID().toString();
        user.passwordHash = user.username;
        user.email = user.username;

        String scope = "pull";
        String repositoryName = parseScopeRepository(scope);
        if (repositoryName != null && !user.hasAccessToRepository(repositoryName)) {
            return null;
        }
        String accessToken = jwtService.generateToken(user, scope);
        return new TokenResponse(accessToken, "Bearer", expirationSeconds);
    }

    public User createUser(String username, String password, String email, List<String> roles) {
        if (userStorage.findByUsername(username) != null) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.username = username;
        user.passwordHash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        user.email = email;
        user.roles = roles;
        userStorage.persist(user);

        return user;
    }

    private String parseScopeRepository(String scope) {
        if (scope == null || scope.isEmpty()) {
            return null;
        }
        String[] parts = scope.split(":");
        if (parts.length >= 2 && "repository".equals(parts[0])) {
            return parts[1];
        }
        return null;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }
}
