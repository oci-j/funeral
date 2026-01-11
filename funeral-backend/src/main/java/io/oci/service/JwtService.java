package io.oci.service;

import io.oci.model.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class JwtService {

    @ConfigProperty(name = "oci.auth.jwt.issuer", defaultValue = "funeral-registry")
    String issuer;

    @ConfigProperty(name = "oci.auth.jwt.expiration-seconds", defaultValue = "3600")
    long expirationSeconds;

    private PrivateKey privateKey;

    @PostConstruct
    void init() {
        try {
            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("privateKey.pem");
            if (is == null) {
                throw new RuntimeException("privateKey.pem not found on classpath");
            }
            String pemContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String base64Key = pemContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load private key: " + e.getMessage(), e);
        }
    }

    public String generateToken(User user, String scope) {
        Set<String> groups = new HashSet<>();
        if (user.roles != null) {
            groups.addAll(user.roles);
        }

        Set<String> actions = parseScopeActions(scope);

        try {
            return Jwt.issuer(issuer)
                    .subject(user.username)
                    .groups(groups)
                    .claim("scope", scope != null ? scope : "")
                    .claim("actions", actions)
                    .expiresIn(Duration.ofSeconds(expirationSeconds))
                    .sign(privateKey);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("JWT signing failed: " + e.getMessage() + " | Cause: " + cause.getMessage(), e);
        }
    }

    private Set<String> parseScopeActions(String scope) {
        Set<String> actions = new HashSet<>();
        if (scope == null || scope.isEmpty()) {
            actions.add("pull");
            actions.add("push");
            return actions;
        }

        String[] parts = scope.split(":");
        if (parts.length >= 3) {
            String[] actionParts = parts[2].split(",");
            for (String action : actionParts) {
                actions.add(action.trim());
            }
        }
        return actions;
    }
}
