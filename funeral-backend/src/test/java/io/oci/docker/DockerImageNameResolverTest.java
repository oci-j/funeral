package io.oci.docker;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Image;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DockerImageNameResolverTest {

    private final DockerImageNameResolver resolver = new DockerImageNameResolver();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolveExactMatch() throws Exception {
        Image image = objectMapper.readValue(
                "{\"RepoTags\":[\"myrepo:latest\"]}",
                Image.class
        );
        List<Image> images = List.of(
                image
        );

        assertEquals(
                "myrepo:latest",
                resolver.resolve(
                        "myrepo",
                        "latest",
                        images
                )
        );
        assertEquals(
                "myrepo:latest",
                resolver.resolveByRepositoryName(
                        "myrepo",
                        images
                )
        );
    }

    @Test
    void resolveLibraryPrefix() throws Exception {
        Image image = objectMapper.readValue(
                "{\"RepoTags\":[\"docker.io/library/ubuntu:22.04\"]}",
                Image.class
        );
        List<Image> images = List.of(
                image
        );

        assertEquals(
                "docker.io/library/ubuntu:22.04",
                resolver.resolve(
                        "ubuntu",
                        "22.04",
                        images
                )
        );
        assertEquals(
                "docker.io/library/ubuntu:22.04",
                resolver.resolve(
                        "library/ubuntu",
                        "22.04",
                        images
                )
        );
    }

    @Test
    void resolveDockerIoPrefix() throws Exception {
        Image image = objectMapper.readValue(
                "{\"RepoTags\":[\"docker.io/mygroup/myapp:1.0\"]}",
                Image.class
        );
        List<Image> images = List.of(
                image
        );

        assertEquals(
                "docker.io/mygroup/myapp:1.0",
                resolver.resolve(
                        "mygroup/myapp",
                        "1.0",
                        images
                )
        );
    }

    @Test
    void resolveMissing() throws Exception {
        Image image = objectMapper.readValue(
                "{\"RepoTags\":[\"other:tag\"]}",
                Image.class
        );
        List<Image> images = List.of(
                image
        );

        assertNull(
                resolver.resolve(
                        "myrepo",
                        "latest",
                        images
                )
        );
        assertNull(
                resolver.resolveByRepositoryName(
                        "myrepo",
                        images
                )
        );
    }
}
