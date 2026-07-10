package io.oci.docker.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeletingOnCloseInputStream extends FilterInputStream {

    private final Path file;

    public DeletingOnCloseInputStream(
            InputStream in,
            Path file
    ) {
        super(
                in
        );
        this.file = file;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        }
        finally {
            Files.deleteIfExists(
                    file
            );
        }
    }
}
