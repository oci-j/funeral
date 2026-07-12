package io.oci.registry.client;

import java.io.IOException;

public class RegistryAuthenticationException extends IOException {

    private final int statusCode;

    public RegistryAuthenticationException(
            int statusCode,
            String message
    ) {
        super(
                message
        );
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
