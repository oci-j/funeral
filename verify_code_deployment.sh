#!/bin/bash

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║          URGENT: Verify Code Deployment                            ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""

# Check 1: Verify the compiled class has our changes
echo "1. Checking if compiled code contains the fixes..."
echo ""

JAR_FILE="funeral-backend/target/funeral-0.1.7.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "✗ JAR file not found: $JAR_FILE"
    echo "  Code has not been compiled!"
    echo "  Run: cd funeral-backend && mvn clean compile quarkus:dev"
    exit 1
fi

echo "✓ JAR file exists"
echo ""

# Extract and check the MirrorHelmResource class
echo "2. Checking MirrorHelmResource.class for fixes..."
cd funeral-backend/target/classes

# Check for \"Generated URL\" debug message (added in latest fix)
if javap -c io/oci/resource/MirrorHelmResource.class 2>/dev/null | grep -q "Generated URL"; then
    echo "✓ Found 'Generated URL' debug code - Latest fixes ARE compiled"
else
    echo "✗ 'Generated URL' debug code NOT found"
    echo "  The latest fixes are NOT in the compiled code!"
    echo ""
    echo "  SOLUTION:"
    echo "  1. cd funeral-backend"
    echo "  2. mvn clean compile quarkus:dev"
    echo "  3. Wait for compilation to complete"
    exit 1
fi

echo "✓ Latest code is compiled"
echo ""

# Check for backup JAR that might be running
echo "3. Checking for multiple JAR versions..."
cd /home/xenoamess/workspace/funeral

JAR_COUNT=$(find funeral-backend/target -name "funeral-*.jar" 2>/dev/null | wc -l)
echo "Found $JAR_COUNT JAR files"

if [ $JAR_COUNT -gt 2 ]; then
    echo "⚠ Found multiple JAR files:"
    find funeral-backend/target -name "funeral-*.jar" -exec ls -lh {} \;
    echo ""
    echo "This might cause confusion. Recommend:"
    echo "cd funeral-backend && rm -rf target && mvn clean compile quarkus:dev"
fi

echo ""
echo "4. Checking backend process..."
BACKEND_PID=$(ps aux | grep "quarkus:dev" | grep -v grep | awk '{print $2}')

if [ -z "$BACKEND_PID" ]; then
    echo "✗ Backend is NOT running"
    echo "  Start it with: cd funeral-backend && mvn quarkus:dev"
    exit 1
else
    echo "✓ Backend is running (PID: $BACKEND_PID)"
fi

echo ""
echo "5. Checking if backend is serving new code..."

# Test an endpoint that would only exist with new code
RESPONSE=$(curl -s http://localhost:8911/funeral_addition/mirror/helm/pull -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "format=test" 2>&1)

if echo "$RESPONSE" | grep -q "Mirror Helm request"; then
    echo "✓ Backend is responding and logging correctly"
else
    echo "⚠ Could not verify backend response"
fi

echo ""
echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║                    VERIFICATION COMPLETE                            ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""

if [ -z "$BACKEND_PID" ]; then
    echo "Issue: Backend is not running"
    echo "Solution: cd funeral-backend && mvn quarkus:dev"
elif ! javap -c io/oci/resource/MirrorHelmResource.class 2>/dev/null | grep -q "Generated URL"; then
    echo "Issue: Latest code is NOT compiled"
    echo "Solution: cd funeral-backend && mvn clean compile quarkus:dev"
else
    echo "Status: Code is compiled and backend is running"
    echo ""
    echo "If Helm pull still fails, the issue is likely:"
    echo "1. Chart was mirrored with old code (before fixes)"
    echo "2. Data corruption in MongoDB"
    echo "3. Frontend is sending incorrect parameters"
    echo ""
    echo "Solution: Delete and re-mirror the chart"
fi

echo ""
