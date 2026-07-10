package io.oci.docker;

public class ResolvedManifest {

    public final byte[] bytes;

    public final String mediaType;

    public final String digest;

    public ResolvedManifest(
            byte[] bytes,
            String mediaType,
            String digest
    ) {
        this.bytes = bytes;
        this.mediaType = mediaType;
        this.digest = digest;
    }
}
