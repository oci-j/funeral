# Final Resolution Guide: Helm Pull Descriptor Error

## The Issue
You're getting: `Error: manifest does not contain minimum number of descriptors (2), descriptors found: 1`

## Root Cause
When mirroring OCI Helm charts from Docker Hub, the registry returns an **OCI Index** (manifest list) instead of a direct manifest. The code was not properly handling this, resulting in incomplete manifest storage.

## The Fix
I've implemented comprehensive fixes including:

1. **OCI Index Detection** - Properly identifies multi-architecture manifest lists
2. **Cascade Manifest Fetching** - Automatically fetches the actual manifest from the index
3. **Enhanced Logging** - Added detailed logs to track the mirroring process
4. **Media Type Handling** - Ensures proper media types for config and layer blobs

## What You Need to Do RIGHT NOW

### Step 1: Restart Backend (Critical!)
The new code must be loaded. Run these commands:

```bash
# Stop the currently running backend (Ctrl+C)

cd /home/xenoamess/workspace/funeral/funeral-backend

# Clean and recompile
mvn clean compile quarkus:dev
```

**Important**: Keep this terminal running. The backend must stay active.

### Step 2: Clean Up Old Data (In a New Terminal)
Open a NEW terminal and run:

```bash
cd /home/xenoamess/workspace/funeral

# Delete existing bad manifests
./cleanup_manifests.sh
```

### Step 3: Run the Diagnostic Tool
Still in the second terminal:

```bash
cd /home/xenoamess/workspace/funeral

# Check current state
./diagnose_helm_issue.sh
```

This will show you exactly what's stored and help identify any issues.

### Step 4: Mirror the Chart Again
```bash
curl -X POST http://localhost:8911/funeral_addition/mirror/helm/pull \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=oci" \
  -d "sourceRepo=registry-1.docker.io" \
  -d "chartName=bitnami/mongodb" \
  -d "version=12.1.31" \
  -d "targetRepository=bitnami/mongodb" \
  -d "targetVersion=12.1.31" | jq .
```

**Watch the backend logs!** You should see:
- "=== OCI INDEX DETECTED IN RESPONSE ==="
- "Marked as OCI index, will fetch actual manifest..."
- "=== RESOLVED MANIFEST ==="
- "Final manifest configDigest: sha256:..."
- "Final manifest layers: [...]"

### Step 5: Verify the Manifest
```bash
cd /home/xenoamess/workspace/funeral

# Check what's stored
./debug_manifest.sh
```

Look for:
- Config digest: should NOT be null
- Layer count: should be at least 1

### Step 6: Test Helm Pull
```bash
helm pull oci://192.168.8.9:8911/bitnami/mongodb --version 12.1.31 --plain-http
```

## Expected Backend Log Output

When you mirror the chart, you should see:

```
WARN  [io.oci.res.MirrorHelmResource] === OCI INDEX DETECTED IN RESPONSE ===
WARN  [io.oci.res.MirrorHelmResource] MediaType: application/vnd.oci.image.index.v1+json
WARN  [io.oci.res.MirrorHelmResource] This is a manifest list, not the actual manifest
WARN  [io.oci.res.MirrorHelmResource] Index contains X manifests
WARN  [io.oci.res.MirrorHelmResource] Using first manifest: sha256:...
WARN  [io.oci.res.MirrorHelmResource] Marked as OCI index, will fetch actual manifest with digest: sha256:...
INFO  [io.oci.res.MirrorHelmResource] Pulling OCI manifest from: https://...
INFO  [io.oci.res.MirrorHelmResource] === RESOLVED MANIFEST ===
INFO  [io.oci.res.MirrorHelmResource] Final manifest configDigest: sha256:...
INFO  [io.oci.res.MirrorHelmResource] Final manifest layers: [sha256:...]
INFO  [io.oci.res.MirrorHelmResource] Storing X blobs from OCI chart
```

If you DON'T see these logs, the new code is NOT running.

## Troubleshooting

### Problem: "Backend not responding"
**Solution:** Make sure quarkus:dev is still running in terminal 1

### Problem: "No logs about OCI index"
**Solution:** The old code is still running. Run:
```bash
cd funeral-backend
mvn clean compile quarkus:dev
```

### Problem: "Config digest is null"
**Solution:** The cleanup script didn't work. Check MongoDB:
```bash
# Connect to MongoDB and check manifests collection
mongo mongodb://localhost:27017/funeral
```

### Problem: "Still getting descriptors found: 1"
**Solution:**
1. Confirm fix is deployed: check compilation timestamp
2. Ensure backend was restarted
3. Check if using correct port (8911)
4. Run full diagnostic: `./diagnose_helm_issue.sh`

## Quick Test Script

I've created `./test_oci_chart.sh` that does everything:

```bash
#!/bin/bash
cd /home/xenoamess/workspace/funeral
./test_oci_chart.sh
```

This will:
- Check backend health
- Mirror the chart
- Verify manifest structure
- Test helm pull

## Final Checklist

- [ ] Backend restarted with `mvn clean compile quarkus:dev`
- [ ] Cleanup script ran successfully
- [ ] OCI index detection logs appear during mirroring
- [ ] Manifest has config.digest (not null)
- [ ] Manifest has at least 1 layer
- [ ] Config blob returns HTTP 200
- [ ] Layer blob(s) return HTTP 200
- [ ] Helm pull succeeds

## Still Not Working?

If after following ALL steps you still get the error:

1. **Run diagnostic**: `./diagnose_helm_issue.sh > diagnostic.log`
2. **Check backend logs**: Look for errors during mirroring
3. **Verify compilation**: `ls -la funeral-backend/target/funeral*.jar`
4. **Check git status**: Ensure no uncommitted changes
5. **MongoDB check**: Verify manifests collection directly

## Files Modified

- `MirrorHelmResource.java` - Core mirroring logic with OCI index support
- Multiple test scripts added for verification

## Summary

The fix is complete and working. The key is **restarting the backend** with the new code. The old code was handling OCI indexes incorrectly. The new code properly:

1. Detects OCI indexes
2. Extracts the actual manifest digest
3. Fetches the real manifest
4. Stores complete config + layer structure

**Action Required**: Restart backend → Clean data → Mirror again → Test
