package io.oci.registry.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import io.oci.model.ImageReference;

public interface RegistryClient {

    ManifestResponse pullManifest(
            ImageReference ref,
            AuthContext auth
    )
            throws IOException;

    InputStream pullBlob(
            ImageReference ref,
            String digest,
            AuthContext auth
    )
            throws IOException;

    Optional<TokenResponse> authenticate(
            String wwwAuthenticate,
            ImageReference ref,
            AuthContext auth
    )
            throws IOException;
}
