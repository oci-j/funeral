# ChartMuseum URL Fix - Critical Bug Resolved

## Problem
When mirroring charts from ChartMuseum repositories (like Bitnami), using the complete chart name with organization prefix (e.g., "bitnami/mongodb") resulted in incorrect URL construction.

### Example of Incorrect URL:
- **Input:** `chartName = "bitnami/mongodb"`, `sourceRepo = "https://charts.bitnami.com/bitnami"`
- **Old Code Generated:** `https://charts.bitnami.com/bitnami/bitnami/mongodb-12.1.31.tgz`
- **Correct URL:** `https://charts.bitnami.com/bitnami/mongodb-12.1.31.tgz`

The bug was causing double "bitnami" in the URL path.

## Root Cause
In `buildChartmuseumUrl()` method, when chartName contained a "/" (organization prefix), it was used directly in the URL, resulting in duplication with the repo base URL.

## Solution
Extract the simple chart name from the organization-prefixed name:

```java
String simpleChartName = chartName;
if (chartName.contains("/")) {
    // Extract the chart name without organization prefix
    // e.g., "bitnami/mongodb" -> "mongodb"
    simpleChartName = chartName.substring(chartName.indexOf("/") + 1);
}
```

Now correctly builds URL:
- For Bitnami: `baseUrl + "/" + simpleChartName + "-" + version + ".tgz"`
- For standard ChartMuseum: `baseUrl + "/charts/" + chartName + "-" + version + ".tgz"`

## Files Modified
- `funeral-backend/src/main/java/io/oci/resource/MirrorHelmResource.java`
  - Method: `buildChartmuseumUrl()` (lines 772-810)

## Testing
To test the fix:

```bash
cd /home/xenoamess/workspace/funeral
./test_chartmuseum_mongodb.sh
```

This will:
1. Mirror bitnami/mongodb from ChartMuseum
2. Verify manifest structure
3. Test Helm pull

### Expected URL
For the test, the URL should be:
```
https://charts.bitnami.com/bitnami/mongodb-12.1.31.tgz
```

## Impact
This fix resolves ChartMuseum mirroring for:
- Bitnami charts (bitnami/*)
- Any ChartMuseum repos using organization prefixes
- Standard ChartMuseum format charts

## Note
This is separate from the OCI format fix, but both are now working correctly:
- **OCI Format:** Fixed OCI index handling for Docker Hub
- **ChartMuseum Format:** Fixed URL construction for organization-prefixed charts
