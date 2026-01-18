# Helm Chart Mirroring - Complete Fix Summary

## Overview
Fixed multiple issues with Helm chart mirroring functionality to ensure both OCI and ChartMuseum formats work correctly.

## Issues Fixed

### 1. ChartMuseum Format - Missing Config and Layers ✅
**Problem:** ChartMuseum charts were stored as single blobs without proper OCI manifest structure (config + layer).

**Root Cause:** The `storeBlob` method used a hardcoded media type for all blobs.

**Solution:**
- Added `mediaType` parameter to `storeBlob()` method
- Store chart tarball as layer with: `application/vnd.cncf.helm.chart.content.v1.tar+gzip`
- Create separate config blob with: `application/vnd.cncf.helm.config.v1+json`
- Build proper OCI manifest linking both

**Files Modified:**
- `funeral-backend/src/main/java/io/oci/resource/MirrorHelmResource.java`
  - Lines 557-589: Store layer with correct media type
  - Lines 905-969: Updated storeBlob methods

### 2. OCI Format - OCI Index Handling ✅
**Problem:** When pulling OCI charts that use OCI indexes (manifest lists), got error:
```
Error: manifest does not contain minimum number of descriptors (2), descriptors found: 1
```

**Root Cause:** Code incorrectly treated manifest digest from OCI index as config digest, missing actual manifest fetch.

**Solution:**
- Added `isIndex` and `indexManifestDigest` fields to `ManifestContent`
- Detect OCI indexes and fetch the actual manifest they point to
- Cascade fetch: OCI Index → Actual Manifest → Config/Layers

**Files Modified:**
- `funeral-backend/src/main/java/io/oci/resource/MirrorHelmResource.java`
  - Lines 1230-1274: OCI index detection
  - Lines 424-444: Cascade fetch logic
  - Lines 1618-1641: Updated ManifestContent class

### 3. Media Types Consistency ✅
**Problem:** Blobs were stored with incorrect or inconsistent media types.

**Solution Applied:
- Config blobs: `application/vnd.cncf.helm.config.v1+json`
- Layer blobs: `application/vnd.cncf.helm.chart.content.v1.tar+gzip`
- Manifest: `application/vnd.oci.image.manifest.v1+json`

**Files Modified:**
- All blob storage operations updated with explicit media types

## Testing Scripts Created

### 1. validate_chartmuseum_fix.sh
Tests ChartMuseum format mirroring with nginx chart from Bitnami.
```bash
./validate_chartmuseum_fix.sh
```

### 2. test_oci_chart.sh
Tests OCI format mirroring and validates helm pull works.
```bash
./test_oci_chart.sh
```

### 3. test_helm_mirror.sh
Comprehensive test for both formats with detailed validation.
```bash
./test_helm_mirror.sh
```

## How to Use

### Mirror ChartMuseum Chart
```bash
curl -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=chartmuseum" \
  -d "sourceRepo=https://charts.bitnami.com/bitnami" \
  -d "chartName=nginx" \
  -d "version=18.2.3" \
  -d "targetRepository=nginx-test" \
  -d "targetVersion=18.2.3"
```

### Mirror OCI Chart
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

### Pull with Helm
```bash
helm pull oci://localhost:8911/bitnami/mongodb --version 12.1.31 --plain-http
```

## Expected Results

### Before Fixes
- ❌ ChartMuseum charts stored as single blob
- ❌ OCI charts with indexes failed with "descriptors found: 1" error
- ❌ Missing config/layer separation

### After Fixes
- ✅ ChartMuseum charts create proper config + layer structure
- ✅ OCI charts with indexes properly fetch actual manifest
- ✅ Both config and layer visible in registry UI
- ✅ `helm pull` works correctly for both formats

## Files Modified Summary

**Backend:**
- `MirrorHelmResource.java` - Core mirroring logic

**Documentation:**
- `CHARTMUSEUM_FIX.md` - ChartMuseum fix details
- `OCI_INDEX_FIX.md` - OCI index handling fix details
- `HELM_MIRROR_FIXES_SUMMARY.md` - This file

**Test Scripts:**
- `validate_chartmuseum_fix.sh`
- `test_oci_chart.sh`
- `test_helm_mirror.sh`

## Next Steps

1. Test with backend running:
   ```bash
   cd funeral-backend && mvn quarkus:dev
   ```

2. Run validation tests in another terminal:
   ```bash
   ./validate_chartmuseum_fix.sh   # Test ChartMuseum format
   ./test_oci_chart.sh             # Test OCI format
   ```

3. Verify in frontend UI that both config and layers appear for mirrored charts

4. Try pulling with Helm to ensure full compatibility
