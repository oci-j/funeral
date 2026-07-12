package io.oci.registry.client;

import java.util.List;
import java.util.Map;

public class ManifestResponse {

    public final String json;

    public final String digest;

    public final String configDigest;

    public final long configSize;

    public final List<String> layerDigests;

    public final Map<String, Long> layerSizes;

    public ManifestResponse(
            String json,
            String digest,
            String configDigest,
            long configSize,
            List<String> layerDigests,
            Map<String, Long> layerSizes
    ) {
        this.json = json;
        this.digest = digest;
        this.configDigest = configDigest;
        this.configSize = configSize;
        this.layerDigests = layerDigests;
        this.layerSizes = layerSizes;
    }
}
