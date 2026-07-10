package io.oci.cli.auth;

public interface CredentialsStore {

    void save(
            String registry,
            Credentials credentials
    );

    Credentials load(
            String registry
    );

    void delete(
            String registry
    );
}
