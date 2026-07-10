# Docker Local Storage Integration Plan

## Objective

Allow funeral to treat the Docker local image store on the same machine as a
read-only external fallback storage backend. When funeral's own storage
(S3/MinIO or local file) does not contain a requested manifest or blob, the
registry should fall back to Docker's local store in this order:

1. Funeral's own storage (`S3StorageService` / `StorageService`).
2. Docker Daemon API (`docker-java` via Unix socket `/var/run/docker.sock`).
3. Direct file system read of Docker's local image files.

Both modern **containerd image store** (Docker Engine 29+ default) and legacy
**overlay2** storage driver must be supported. The implementation will be done
in three phases.

## Prerequisites

- Funeral must run as **root** so it can read `/var/run/docker.sock` and
  `/var/lib/containerd` / `/var/lib/docker`.
- No automatic caching into funeral's own storage unless explicitly requested.
- Overlay2 compressed layers will be reconstructed from `tar-split` + `diff`
  and re-gzipped. This produces a **new compressed digest** and therefore a
  **new manifest digest** for overlay2 images. This is accepted.

## Phase 1: Docker Daemon API Read-Only Fallback

Goal: Make `ManifestResourceHandler` and `BlobResourceHandler` fall back to the
Docker Daemon API when own storage misses.

### New Dependencies

Add to `funeral-backend/pom.xml`:

```xml
<dependency>
    <groupId>com.github.docker-java</groupId>
    <artifactId>docker-java-core</artifactId>
    <version>3.7.1</version>
    <exclusions>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </exclusion>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>com.github.docker-java</groupId>
    <artifactId>docker-java-transport-zerodep</artifactId>
    <version>3.7.1</version>
</dependency>
```

Jackson dependencies are excluded because Quarkus already provides Jackson.

### New Configuration

Add to `funeral-backend/src/main/resources/application.yml`:

```yaml
oci:
  docker-local:
    enabled: true
    socket: /var/run/docker.sock
    api-version: 1.44
    timeout: 30s
    containerd-root: /var/lib/containerd
    docker-root: /var/lib/docker
    fallback-to-direct-read: true
```

### New Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `DockerClientFactory` | `io.oci.docker.client` | Build a singleton `DockerClient` using `DefaultDockerClientConfig` + `ZerodepDockerHttpClient` over the Unix socket. |
| `DockerImageNameResolver` | `io.oci.docker` | Map a funeral `repositoryName:tag` to a Docker local `RepoTag`. Try exact match, `docker.io/library/<repo>:<tag>`, `docker.io/<repo>:<tag>`, etc. |
| `DockerApiImageResolver` | `io.oci.docker` | Use `dockerClient.saveImageCmd(name).exec()` to export the image as a tar stream, then reuse the existing `DockerTarResource` tar parsing to construct a Docker v2 manifest and extract blobs by digest. |
| `DockerLocalResolver` | `io.oci.docker` | Entry point for the fallback chain. In Phase 1 it delegates to `DockerApiImageResolver`. |

### Modified Classes

| Class | Change |
|-------|--------|
| `ManifestResourceHandler` | After `manifestStorage.findByRepositoryAndTag` / `findByRepositoryAndDigest` returns null, call `dockerLocalResolver.resolveManifest(repository, reference)`. If found, return the manifest bytes with the appropriate media type. Do not persist to own storage. |
| `BlobResourceHandler` | After `blobStorage.findByDigest` + `storage.getBlobStream` fails, call `dockerLocalResolver.resolveBlob(digest, repository, reference)`. If found, return the stream and size. Do not persist to own storage. |

### Phase 1 Test

- On a machine with Docker running, pull an image (e.g. `ubuntu:latest`).
- Ensure the image is not in funeral's own storage.
- `docker pull <funeral-host>/<repo>:<tag>` where `<repo>:<tag>` maps to the local Docker image.
- Verify that funeral serves the manifest and blobs via the Docker API fallback.

## Phase 2: Containerd Image Store Direct File Read

Goal: When the Docker Daemon API is unavailable, read compressed blobs directly
from the containerd content store.

### New Classes

| Class | Purpose |
|-------|---------|
| `ContainerdFileResolver` | Resolve blobs by reading `/var/lib/containerd/io.containerd.content.v1.content/blobs/<alg>/<hex>`. For manifest by digest, read the same path. For manifest by tag, fall back to the Docker API (or skip if the daemon is down). |
| `DockerStorageModeDetector` | Detect whether the containerd content store is present at the configured path. |

### Modified Classes

| Class | Change |
|-------|--------|
| `DockerLocalResolver` | Update fallback order: Docker API → containerd direct read → overlay2 direct read. |

### Phase 2 Test

- Stop Docker daemon.
- Verify that a `docker pull` from funeral still works for images whose blobs
  exist in `/var/lib/containerd/io.containerd.content.v1.content/blobs/`.

## Phase 3: Overlay2 Direct File Read

Goal: When Docker API and containerd direct read both fail, read overlay2 files
directly and reconstruct layers.

### New Classes

| Class | Purpose |
|-------|---------|
| `Overlay2FileResolver` | Read `/var/lib/docker/image/overlay2/metadata.db` (SQLite) or `repositories.json` to map `repo:tag` to image ID (config digest). Read `imagedb/content/sha256/<id>` for config. Read `distribution/v2metadata-by-diffid/sha256/<diff_id>` for compressed digests to construct a Docker v2 manifest. |
| `TarSplitReassembler` | Parse `layerdb/sha256/<layer_id>/tar-split.json.gz`, combine with `overlay2/<cache-id>/diff` files, rebuild the tar stream, and gzip it. The resulting compressed digest will differ from the original. |

### Modified Classes

| Class | Change |
|-------|--------|
| `DockerLocalResolver` | Complete the final fallback chain: Docker API → containerd direct read → overlay2 direct read. |

### Phase 3 Test

- Use an older Docker Engine configured with `overlay2` storage driver.
- Pull an image locally, ensure it is not in funeral's own storage.
- Pull through funeral and verify manifest + blob delivery.
- Verify that the served manifest/layer digests are consistent with the
  reconstructed layers (even if they differ from the original upstream digests).

## Risks and Notes

- **Native image metadata**: `docker-java` and `zerodep` may introduce new
  reflection / resource requirements. Run native tests after each phase and
  update reachability metadata if needed.
- **Dependency conflicts**: Exclude docker-java's Jackson to avoid conflicts
  with Quarkus's Jackson.
- **Performance**: Docker API export (`docker save`) streams the entire image;
  overlay2 tar-split reconstruction is CPU intensive. These are fallbacks only.
- **Security**: Running as root is required; ensure funeral is not exposed to
  untrusted networks.

## Acceptance Criteria

- All three phases implemented and tested.
- `BlobResourceHandler` and `ManifestResourceHandler` always try own storage
  first, then Docker API, then direct file read.
- No automatic writes to funeral's own storage.
- Containerd image store can be read even when Docker daemon is stopped.
- Overlay2 images can be served even when Docker daemon is stopped, with the
  understanding that compressed digests will differ from the original.
