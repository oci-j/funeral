package io.oci.registry.client;

import java.io.IOException;

public class RegistryImageNotFoundException extends IOException {

    public RegistryImageNotFoundException(
            String message
    ) {
        super(
                message
        );
    }
}
