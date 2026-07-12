package io.oci.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import io.oci.model.ImageReference;
import io.oci.registry.client.AuthContext;
import io.oci.registry.client.ManifestResponse;
import io.oci.registry.client.RegistryClient;
import io.oci.registry.client.TokenResponse;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

@Alternative
@Priority(
    1
)
@ApplicationScoped
public class MockRegistryClientProducer {

    static volatile RegistryClient delegate;

    @Produces
    @Alternative
    @Priority(
        1
    )
    public RegistryClient registryClient() {
        return new RegistryClient() {
            @Override
            public ManifestResponse pullManifest(
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                if (delegate == null) {
                    throw new IllegalStateException(
                            "MockRegistryClientProducer.delegate not set"
                    );
                }
                return delegate.pullManifest(
                        ref,
                        auth
                );
            }

            @Override
            public InputStream pullBlob(
                    ImageReference ref,
                    String digest,
                    AuthContext auth
            )
                    throws IOException {
                if (delegate == null) {
                    throw new IllegalStateException(
                            "MockRegistryClientProducer.delegate not set"
                    );
                }
                return delegate.pullBlob(
                        ref,
                        digest,
                        auth
                );
            }

            @Override
            public Optional<TokenResponse> authenticate(
                    String wwwAuthenticate,
                    ImageReference ref,
                    AuthContext auth
            )
                    throws IOException {
                if (delegate == null) {
                    throw new IllegalStateException(
                            "MockRegistryClientProducer.delegate not set"
                    );
                }
                return delegate.authenticate(
                        wwwAuthenticate,
                        ref,
                        auth
                );
            }
        };
    }
}
