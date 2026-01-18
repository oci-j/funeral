package io.oci.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import io.jsonwebtoken.Jwts;
import io.oci.model.User;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class JwtService {

    private static final Logger LOG = Logger.getLogger(
            JwtService.class
    );

    @ConfigProperty(
            name = "oci.auth.jwt.issuer",
            defaultValue = "funeral-registry"
    )
    String issuer;

    @ConfigProperty(
            name = "oci.auth.jwt.expiration-seconds",
            defaultValue = "3600"
    )
    long expirationSeconds;

    @ConfigProperty(
            name = "oci.auth.jwt.key-size",
            defaultValue = "2048"
    )
    String keySizeStr;

    private PrivateKey privateKey;

    private PublicKey publicKey;

    @PostConstruct
    void init() {
        try {
            // Parse key size from config
            int keySize = Integer.parseInt(
                    keySizeStr
            );
            if (keySize < 2048 || keySize > 4096) {
                LOG.warn(
                        "Invalid key size " + keySize + ", using default 2048"
                );
                keySize = 2048;
            }

            // Generate RSA key pair dynamically
            LOG.info(
                    "Generating RSA key pair with size " + keySize + " bits"
            );
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                    "RSA"
            );
            keyPairGenerator.initialize(
                    keySize
            );
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();

            LOG.info(
                    "Successfully generated RSA key pair"
            );
        }
        catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate RSA key pair: " + e.getMessage(),
                    e
            );
        }
    }

    public String generateToken(
            User user,
            String scope
    ) {
        Set<String> groups = new HashSet<>();
        if (user.roles != null) {
            groups.addAll(
                    user.roles
            );
        }

        Set<String> actions = parseScopeActions(
                scope
        );

        try {
            return Jwts.builder()
                    .issuer(
                            issuer
                    )
                    .subject(
                            user.username
                    )
                    .claim(
                            "groups",
                            groups
                    )
                    .claim(
                            "scope",
                            scope != null ? scope : ""
                    )
                    .claim(
                            "actions",
                            actions
                    )
                    .expiration(
                            new Date(
                                    System.currentTimeMillis() + expirationSeconds * 1000
                            )
                    )
                    .signWith(
                            privateKey
                    )
                    .compact();
        }
        catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(
                    "JWT signing failed: " + e.getMessage() + " | Cause: " + cause.getMessage(),
                    e
            );
        }
    }

    private Set<String> parseScopeActions(
            String scope
    ) {
        Set<String> actions = new HashSet<>();
        if (scope == null || scope.isEmpty()) {
            actions.add(
                    "pull"
            );
            actions.add(
                    "push"
            );
            return actions;
        }

        String[] parts = scope.split(
                ":"
        );
        if (parts.length >= 3) {
            String[] actionParts = parts[2].split(
                    ","
            );
            for (String action : actionParts) {
                actions.add(
                        action.trim()
                );
            }
        }
        return actions;
    }

    /**
     * Get the public key for JWT verification
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Get the private key for testing purposes
     */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    /**
     * Export the public key in PEM format (base64 encoded)
     */
    public String exportPublicKey() {
        String base64Key = Base64.getEncoder()
                .encodeToString(
                        publicKey.getEncoded()
                );
        return "-----BEGIN PUBLIC KEY-----\n" + base64Key.replaceAll(
                "(.{64})",
                "$1\n"
        ) + "\n-----END PUBLIC KEY-----";
    }

    /**
     * Export the private key in PEM format (base64 encoded)
     */
    public String exportPrivateKey() {
        String base64Key = Base64.getEncoder()
                .encodeToString(
                        privateKey.getEncoded()
                );
        return "-----BEGIN PRIVATE KEY-----\n" + base64Key.replaceAll(
                "(.{64})",
                "$1\n"
        ) + "\n-----END PRIVATE KEY-----";
    }
}
