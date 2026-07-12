package io.oci.registry.client;

public class AuthContext {

    public final String username;

    public final String password;

    public final String protocol;

    public final boolean insecure;

    public AuthContext(
            String username,
            String password,
            String protocol,
            boolean insecure
    ) {
        this.username = username;
        this.password = password;
        this.protocol = protocol;
        this.insecure = insecure;
    }
}
