package io.oci.config;

import io.minio.GetBucketLocationArgs;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(
        targets = {
                GetBucketLocationArgs.class
        }
)
public class NativeImageConfig {
}
