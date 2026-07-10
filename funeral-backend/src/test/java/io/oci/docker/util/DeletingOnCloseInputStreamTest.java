package io.oci.docker.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletingOnCloseInputStreamTest {

    @TempDir
    Path tempDir;

    @Test
    void readsAndDeletesOnClose() throws Exception {
        byte[] content = "temporary".getBytes();
        Path file = tempDir.resolve(
                "delete-me.txt"
        );
        Files.write(
                file,
                content
        );
        assertTrue(
                Files.exists(
                        file
                )
        );

        try (
                InputStream source = Files.newInputStream(
                        file
                );
                DeletingOnCloseInputStream wrapper = new DeletingOnCloseInputStream(
                        source,
                        file
                )) {
            byte[] read = wrapper.readAllBytes();
            assertArrayEquals(
                    content,
                    read
            );
        }

        assertFalse(
                Files.exists(
                        file
                )
        );
    }

    @Test
    void deletesEvenWhenSourceThrows() throws Exception {
        Path file = tempDir.resolve(
                "delete-me.txt"
        );
        Files.write(
                file,
                "x".getBytes()
        );

        try (
                DeletingOnCloseInputStream wrapper = new DeletingOnCloseInputStream(
                        new ByteArrayInputStream(
                                new byte[0]
                        ),
                        file
                )) {
            // no read
        }

        assertFalse(
                Files.exists(
                        file
                )
        );
    }
}
