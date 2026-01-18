#!/bin/bash

echo "Testing Helm v3.16.4 (older version) as workaround..."
echo ""

# Download older Helm version
cd /tmp
if [ ! -f helm-v3.16.4-linux-amd64.tar.gz ]; then
  echo "Downloading Helm v3.16.4..."
  wget -q https://get.helm.sh/helm-v3.16.4-linux-amd64.tar.gz
fi

tar xzf helm-v3.16.4-linux-amd64.tar.gz > /dev/null 2>&1

# Make test chart
cd /tmp/helm-test
mkdir -p mychart/templates
cat > mychart/Chart.yaml << CHART
ame: mychart
description: A test chart
type: application
version: 0.1.0
appVersion: "1.0"
CHART

cat > mychart/values.yaml << VALUES
replicaCount: 1
VALUES

cat > mychart/templates/pod.yaml << TEMPLATE
apiVersion: v1
kind: Pod
metadata:
  name: test-pod
spec:
  containers:
  - name: test
    image: nginx:latest
TEMPLATE

# Package chart
/tmp/linux-amd64/helm package mychart > /dev/null 2>&1

# Push to registry
echo "Pushing test chart..."
/tmp/linux-amd64/helm push mychart-0.1.0.tgz oci://192.168.8.9:8911/test --plain-http

echo "Pulling test chart with v3.16.4..."
/tmp/linux-amd64/helm pull oci://192.168.8.9:8911/test/mychart --version 0.1.0 --plain-http

echo ""
if [ $? -eq 0 ]; then
  echo "✓ Helm v3.16.4 works with this registry!"
  echo "Issue is in Helm v3.17.2"
else
  echo "✗ Even Helm v3.16.4 fails - deeper issue"
fi
