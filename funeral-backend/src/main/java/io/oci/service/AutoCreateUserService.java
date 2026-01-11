package io.oci.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class AutoCreateUserService {

    private static final Logger LOG = Logger.getLogger(AutoCreateUserService.class);

    @ConfigProperty(name = "oci.auth.auto-create.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "oci.auth.auto-create.username", defaultValue = "admin")
    String username;

    @ConfigProperty(name = "oci.auth.auto-create.password", defaultValue = "password")
    String password;

    @ConfigProperty(name = "oci.auth.auto-create.email", defaultValue = "admin@funeral.local")
    String email;

    @ConfigProperty(name = "oci.auth.auto-create.roles", defaultValue = "ADMIN;USER;PUSH_ALL;PULL_ALL")
    String roles;

    @Inject
    AuthService authService;

    void onStart(@Observes StartupEvent event) {
        if (enabled) {
            try {
                authService.createUser(
                        username,
                        password,
                        email,
                        Arrays.asList(
                                StringUtils.split(roles, ';')
                        )
                );
                LOG.info("Created default admin user");
            } catch (IllegalArgumentException e) {
                LOG.info("Admin user already exists");
            }
        }
    }
}
