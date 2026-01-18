# OCI Index/Manifest List Fix for Helm Charts

## Problem
When mirroring OCI format Helm charts, if the source registry returns an OCI index (manifest list) for multi-architecture images, the code was incorrectly handling it, resulting in manifests with missing descriptors. This caused Helm to fail with:
```
Error: manifest does not contain minimum number of descriptors (2), descriptors found: 1
```

## Root Cause
The `parseOCIManifest` method had a bug where when it detected an OCI index (instead of a regular manifest), it was incorrectly treating the manifest digest from the index as a config digest. This resulted in:
- Missing actual config blob
- Missing layer blobs
- Incorrect manifest structure

## Solution Implemented

### 1. Enhanced ManifestContent Class
Added fields to track OCI indexes:
```java
// Fields for handling OCI indexes (manifest lists)
boolean isIndex;
String indexManifestDigest;
```

### 2. Fixed OCI Index Detection
When `pullOCIManifest` detects an OCI index:
- It now properly identifies it with `isIndex = true`
- Stores the digest of the first manifest in the index
- Returns without parsing (since it's an index, not the actual manifest)

### 3. Added Cascade Fetch Logic
In `pullOCIChart`, after getting a manifest:
```java
// If this is an OCI index (manifest list), fetch the actual manifest
if (manifestContent.isIndex) {
    // Create a new reference using the digest from the index
    ImageRef digestRef = new ImageRef();
    digestRef.registry = ref.registry;
    digestRef.fullRepositoryPath = ref.fullRepositoryPath;
    digestRef.tag = manifestContent.indexManifestDigest.replace("sha256:", "");

    // Fetch the actual manifest
    manifestContent = pullOCIManifest(digestRef, username, password);
}
```

### 4. Proper Media Type Handling
Ensured all blobs (both config and layers) are stored with correct OCI media types:
- Config: `application/vnd.cncf.helm.config.v1+json`
- Layer: `application/vnd.cncf.helm.chart.content.v1.tar+gzip`

## Files Modified
- `funeral-backend/src/main/java/io/oci/resource/MirrorHelmResource.java`

## Testing
Test script created: `test_oci_chart.sh`

Run with:
```bash
# Start backend
cd funeral-backend && mvn quarkus:dev

# In another terminal, run test
cd ..
./test_oci_chart.sh
```

## Expected Behavior
1. OCI chart mirroring should handle both:
   - Regular OCI manifests (config + layers)
   - OCI indexes (manifest lists) pointing to arch-specific manifests

2. After mirroring, `helm pull` should work correctly with proper descriptor counts

3. Both config and layer blobs should be visible in the registry

## Example
Mirroring `bitnami/mongodb:12.1.31` from Docker Hub:
- Docker Hub returns OCI index for multi-arch
- Code detects index and fetches actual manifest
- Stores both config and layer properly
- `helm pull oci://localhost:8911/bitnami/mongodb --version 12.1.31` succeeds
