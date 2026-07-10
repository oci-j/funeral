package io.oci.docker;

import java.io.InputStream;

public class ResolvedBlob {

    public final InputStream stream;

    public final long size;

    public ResolvedBlob(
            InputStream stream,
            long size
    ) {
        this.stream = stream;
        this.size = size;
    }
}
