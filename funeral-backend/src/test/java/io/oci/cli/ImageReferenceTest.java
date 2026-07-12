package io.oci.cli;

import io.oci.model.ImageReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageReferenceTest {

    @Test
    void parseDomainRepositoryTag() {
        ImageReference r = ImageReference.parse(
                "docker.xenoamess.com/a/b/c:1.1.1"
        );
        assertEquals(
                "docker.xenoamess.com",
                r.registry
        );
        assertEquals(
                "a/b/c",
                r.repository
        );
        assertEquals(
                "1.1.1",
                r.tag
        );
        assertNull(
                r.digest
        );
    }

    @Test
    void parseLocalhostWithPort() {
        ImageReference r = ImageReference.parse(
                "localhost:8911/a/b:c"
        );
        assertEquals(
                "localhost:8911",
                r.registry
        );
        assertEquals(
                "a/b",
                r.repository
        );
        assertEquals(
                "c",
                r.tag
        );
    }

    @Test
    void parseIpWithPort() {
        ImageReference r = ImageReference.parse(
                "1.1.1.1:5000/a/b/c:1"
        );
        assertEquals(
                "1.1.1.1:5000",
                r.registry
        );
        assertEquals(
                "a/b/c",
                r.repository
        );
        assertEquals(
                "1",
                r.tag
        );
    }

    @Test
    void parseDigest() {
        ImageReference r = ImageReference.parse(
                "docker.xenoamess.com/a/b/c@sha256:abcdef123456"
        );
        assertEquals(
                "docker.xenoamess.com",
                r.registry
        );
        assertEquals(
                "a/b/c",
                r.repository
        );
        assertNull(
                r.tag
        );
        assertEquals(
                "sha256:abcdef123456",
                r.digest
        );
        assertTrue(
                r.isDigested()
        );
        assertFalse(
                r.isTagged()
        );
    }

    @Test
    void parseDockerHubOfficial() {
        ImageReference r = ImageReference.parse(
                "alpine:3.20"
        );
        assertEquals(
                ImageReference.DEFAULT_REGISTRY,
                r.registry
        );
        assertEquals(
                "library/alpine",
                r.repository
        );
        assertEquals(
                "3.20",
                r.tag
        );
    }

    @Test
    void parseDockerHubNamespaced() {
        ImageReference r = ImageReference.parse(
                "xenoamess/foo:bar"
        );
        assertEquals(
                ImageReference.DEFAULT_REGISTRY,
                r.registry
        );
        assertEquals(
                "xenoamess/foo",
                r.repository
        );
        assertEquals(
                "bar",
                r.tag
        );
    }

    @Test
    void parseDockerHubWithRegistry() {
        ImageReference r = ImageReference.parse(
                "docker.io/library/alpine:3.20"
        );
        assertEquals(
                "docker.io",
                r.registry
        );
        assertEquals(
                "library/alpine",
                r.repository
        );
        assertEquals(
                "3.20",
                r.tag
        );
    }

    @Test
    void defaultTag() {
        ImageReference r = ImageReference.parse(
                "docker.xenoamess.com/a/b/c"
        );
        assertEquals(
                "latest",
                r.tag
        );
    }

    @Test
    void roundTrip() {
        ImageReference r = ImageReference.parse(
                "docker.xenoamess.com/a/b/c:1.1.1"
        );
        assertEquals(
                "docker.xenoamess.com/a/b/c:1.1.1",
                r.toString()
        );
    }
}
