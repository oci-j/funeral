# Helm Pull Error: "manifest does not contain minimum number of descriptors (2)"

## Problem Summary
When pulling mirrored OCI Helm charts with `helm pull`, you get:
```
Error: manifest does not contain minimum number of descriptors (2), descriptors found: 1
```

This indicates the manifest stored in the registry is missing required descriptors (config and/or layers).

## Root Causes & Solutions

### Issue 1: OCI Index Not Properly Resolved ✅ FIXED
**Problem:** When mirroring OCI charts from Docker Hub, multi-architecture charts use OCI indexes (manifest lists). The code wasn't properly fetching the actual manifest.

**Solution Implemented:**
- Modified code to detect OCI indexes
- Fetch actual manifest using digest from the index
- Ensure full digest (with `sha256:` prefix) is used

**Files Fixed:**
- `MirrorHelmResource.java` lines 424-444, 1230-1274, 1618-1641

### Issue 2: Existing Bad Manifests in Database
**Problem:** If you tested before the fix, bad manifests may be cached/stored.

**Solution:** Clean up existing manifests before re-testing.

## Step-by-Step Resolution

### Step 1: Restart Backend with New Code
```bash
# In funeral-backend directory
mvn clean compile quarkus:dev
```

### Step 2: Clean Up Existing Bad Manifests
```bash
# Run cleanup script
cd ..
./cleanup_manifests.sh
```

Or manually:
```bash
# Delete existing mongodb manifest
curl -X DELETE http://localhost:8911/v2/bitnami/mongodb/manifests/12.1.31
```

### Step 3: Mirror the Chart Again
```bash
curl -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=oci" \
  -d "sourceRepo=registry-1.docker.io" \
  -d "chartName=bitnami/mongodb" \
  -d "version=12.1.31" \
  -d "targetRepository=bitnami/mongodb" \
  -d "targetVersion=12.1.31"
```

### Step 4: Verify Manifest Structure
```bash
# Run debug script
./debug_manifest.sh
```

Expected output should show:
- `config.digest`: non-null value
- `layers`: at least 1 layer

### Step 5: Test Helm Pull
```bash
helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http
```

Should succeed without errors!

## Debug Tools Provided

### debug_manifest.sh
Checks actual manifest structure in registry:
```bash
./debug_manifest.sh
```

### cleanup_manifests.sh
Removes existing bad manifests:
```bash
./cleanup_manifests.sh
```

### test_oci_chart.sh
Complete test for OCI chart mirroring:
```bash
./test_oci_chart.sh
```

## What the Fix Does

1. **OCI Index Detection**: Identifies when Docker Hub returns an OCI index (manifest list) instead of a direct manifest

2. **Manifest Resolution**: Automatically fetches the actual manifest using the digest from the index

3. **Proper Descriptor Storage**: Ensures both config and layer descriptors are stored in the manifest

4. **Full Digest Usage**: Uses complete digest (including `sha256:` prefix) for fetching by digest

## Technical Details

### Before Fix
```java
// Was using partial digest
digestRef.tag = digest.replace("sha256:", "")
// Result: URL = /v2/.../manifests/abc123...
```

### After Fix
```java
// Now using full digest
digestRef.tag = digest
// Result: URL = /v2/.../manifests/sha256:abc123...
```

### OCI Index Flow
```
1. Fetch manifest from registry
2. Detect it's an OCI index (isIndex=true)
3. Extract digest of actual manifest from index
4. Re-fetch using the digest as tag
5. Parse actual manifest to get config and layers
6. Store complete manifest with all descriptors
```

## Testing Checklist

- [ ] Backend restarted with new code
- [ ] Existing bad manifests cleaned up
- [ ] Chart re-mirrored successfully
- [ ] Manifest has config.digest (not null)
- [ ] Manifest has at least 1 layer
- [ ] Helm pull succeeds without errors

## Still Having Issues?

If the error persists after following these steps:

1. Check backend logs for errors during mirroring
2. Run debug_manifest.sh to see actual stored structure
3. Verify backend is using latest code (check compilation timestamp)
4. Check MongoDB directly for manifest document

## Files Modified

- `funeral-backend/src/main/java/io/oci/resource/MirrorHelmResource.java`
  - OCI index handling (lines 424-444)
  - Manifest parsing (lines 1230-1274)
  - ManifestContent class (lines 1618-1641)

## Quick Commands

```bash
# Full cleanup and test flow
cd funeral-backend && mvn clean compile quarkus:dev &
sleep 10
cd ..
./cleanup_manifests.sh
./test_oci_chart.sh
```
