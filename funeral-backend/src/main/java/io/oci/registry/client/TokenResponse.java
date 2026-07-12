package io.oci.registry.client;

public class TokenResponse {

    public final String token;

    public final long expiresIn;

    public TokenResponse(
            String token,
            long expiresIn
    ) {
        this.token = token;
        this.expiresIn = expiresIn;
    }
}
