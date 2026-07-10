package io.oci.docker.client;

import java.time.Duration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DockerClientFactory {

    private static final Logger log = LoggerFactory.getLogger(
            DockerClientFactory.class
    );

    @ConfigProperty(
            name = "oci.docker-local.socket",
            defaultValue = "/var/run/docker.sock"
    )
    String socket;

    @ConfigProperty(
            name = "oci.docker-local.api-version",
            defaultValue = "1.44"
    )
    String apiVersion;

    @ConfigProperty(
            name = "oci.docker-local.timeout",
            defaultValue = "30s"
    )
    Duration timeout;

    @Produces
    @Singleton
    public DockerClient dockerClient() {
        log.info(
                "Creating Docker client for unix socket: {}",
                socket
        );
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(
                        "unix://" + socket
                )
                .withApiVersion(
                        apiVersion
                )
                .build();

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(
                config.getDockerHost()
        )
                .sslConfig(
                        config.getSSLConfig()
                )
                .connectionTimeout(
                        timeout
                )
                .responseTimeout(
                        timeout
                )
                .build();

        return DockerClientImpl.getInstance(
                config,
                httpClient
        );
    }
}
