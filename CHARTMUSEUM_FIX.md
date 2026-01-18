# ChartMuseum Mirroring Fix Summary

## Problem
ChartMuseum charts were being stored as a single blob without proper OCI manifest structure, resulting in missing config and layer information in the registry.

## Root Cause
The `storeBlob` method was using a hardcoded media type for all blobs, and ChartMuseum charts were not being properly converted to OCI format with separate config and layer blobs.

## Solution
Modified `MirrorHelmResource.java` to:

### 1. Updated `storeBlob` method signature
- Added `mediaType` parameter to accept different media types for config and layer blobs
- Created overloaded version for backward compatibility
- Allows proper OCI artifact structure with config and layers having different media types

### 2. Fixed ChartMuseum mirroring
- Store chart tarball as layer blob with correct media type: `application/vnd.cncf.helm.chart.content.v1.tar+gzip`
- Create separate config blob with metadata and media type: `application/vnd.cncf.helm.config.v1+json`
- Build proper OCI manifest structure linking config and layer

### 3. Updated OCI mirroring
- Explicitly pass media types for config and layer blobs
- Ensures consistency across both OCI and ChartMuseum formats

### 4. Media Types Used
```java
// Config blob (metadata)
"application/vnd.cncf.helm.config.v1+json"

// Layer blob (chart content)
"application/vnd.cncf.helm.chart.content.v1.tar+gzip"

// Manifest
"application/vnd.oci.image.manifest.v1+json"
```

## Testing
Created test script (`test_helm_mirror.sh`) to verify:
1. ChartMuseum charts create proper config and layer blobs
2. Manifest structure is correct
3. Blobs are accessible via registry API
4. Both config and layers are visible in the UI

## Files Modified
- `/home/xenoamess/workspace/funeral/funeral-backend/src/main/java/io/oci/resource/MirrorHelmResource.java`

## Key Changes in Code

### Before:
```java
storeBlob(layerDigest, new ByteArrayInputStream(chartData), (long) chartData.length);
// All blobs got the same media type
```

### After:
```java
// Store chart as layer with correct media type
storeBlob(
    layerDigest,
    new ByteArrayInputStream(chartData),
    (long) chartData.length,
    "application/vnd.cncf.helm.chart.content.v1.tar+gzip"
);

// Store config with metadata media type
storeBlob(
    configDigest,
    new ByteArrayInputStream(configMetadata),
    configSize,
    "application/vnd.cncf.helm.config.v1+json"
);
```

## Result
ChartMuseum charts now properly create:
- Config blob with chart metadata
- Layer blob with actual chart content
- OCI manifest linking both
- Both config and layers visible in registry UI
