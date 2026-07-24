package io.oci.service;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public class MongoTestResource implements QuarkusTestResourceLifecycleManager {

    static volatile boolean dockerAvailable = false;

    private MongoDBContainer container;

    @Override
    public Map<String, String> start() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            dockerAvailable = false;
            return Map.of();
        }
        container = new MongoDBContainer(
                DockerImageName.parse(
                        "mongo:7.0"
                )
        );
        container.start();
        dockerAvailable = true;
        return Map.of(
                "quarkus.mongodb.connection-string",
                container.getReplicaSetUrl()
        );
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
