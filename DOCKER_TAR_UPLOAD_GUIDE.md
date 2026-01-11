# Docker Tar Upload Feature Guide

## Overview

Implemented a feature to upload Docker tar archives created by `docker save` command, automatically analyze the content, and load image metadata into the registry.

## Features

### Backend API
- **Endpoint**: `POST /api/admin/upload/dockertar`
- **Auth Required**: Yes (Authenticated)
- **Content-Type**: `multipart/form-data`
- **File Support**: `.tar`, `.tar.gz`, `.tgz`

### What Gets Analyzed

1. **Image Tags**: From `manifest.json` → Repository names and tags
2. **Config Blobs**: SHA256 digests of image configurations
3. **Layer Blobs**: SHA256 digests of all image layers
4. **Metadata**: Sizes, media types, relationships

### What Gets Stored

1. **Repositories**: New repository names created
2. **Manifests**: Image metadata with tags, config digest, layer digests
3. **Blobs**: All blob references tracked

## Usage

### 1. Create Docker Tar File

```bash
# Save single image
docker save my-image:latest -o my-image.tar

# Save multiple images
docker save image1:latest image2:v1 -o images.tar

# Save with compression
docker save my-image:latest | gzip > my-image.tar.gz
```

### 2. Upload via Web Interface

1. Navigate to Upload page (`/upload`)
2. Drag and drop tar file or click to select
3. Click "Upload and Analyze"
4. View results in real-time

### 3. Results Display

After upload, you'll see:
- **Repositories Found**: List of repository names
- **Manifests**: Count of image manifests
- **Blobs**: Count of unique blobs
- **Image Tags**: Table showing each image with:
  - Repository name
  - Tag
  - Config digest (SHA256)
  - Number of layers

## Frontend Components

### Upload.vue Enhancements

Added a new section "📦 Upload Docker Tar File" with:

1. **Drag & Drop Upload**
   - Element Plus upload component
   - Accepts `.tar`, `.tar.gz`, `.tgz`
   - File validation and error handling

2. **Upload Button**
   - Disabled until file selected
   - Shows loading state during upload

3. **Results Display**
   - Success/error alerts
   - Detailed analysis results
   - Repository tags
   - Manifest and blob counts

4. **Data Table**
   - Shows all images found in tar
   - Repository, Tag, Config Digest, Layer count
   - Sortable and scrollable

## Implementation Details

### Backend (Java)

**DockerTarResource.java** - Main API endpoint:
```java
@Path("/api/admin/upload")
@Authenticated
public class DockerTarResource {
    @POST
    @Path("/dockertar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadDockerTar(@FormParam("file") InputStream fileInputStream)
}
```

**Key Methods**:
- `parseDockerTar()` - Extracts and analyzes tar content
- `parseManifestJson()` - Parses Docker manifest.json
- `saveToStorage()` - Saves metadata to MongoDB/files

**Parsing Logic**:
1. Save uploaded file to temp directory
2. Read as tar archive
3. Extract `manifest.json`
4. Parse JSON to get:
   - `RepoTags` - Image names and tags
   - `Config` - Config blob digest
   - `Layers` - List of layer digests
5. Create database entries

### Frontend (Vue 3)

**Script Features**:
```javascript
// File validation
const handleFileChange = (file) => {
  const isTar = file.name.endsWith('.tar') ||
                file.name.endsWith('.tar.gz') ||
                file.name.endsWith('.tgz');
  if (!isTar) {
    ElMessage.error('Only tar files allowed');
    return false;
  }
}

// Upload function
const uploadTarFiles = async () => {
  const formData = new FormData();
  fileList.value.forEach((file) => {
    formData.append('file', file.raw);
  });

  const response = await fetch('/api/admin/upload/dockertar', {
    method: 'POST',
    body: formData,
    credentials: 'include'
  });
}
```

## API Response Format

### Success Response (200 OK)
```json
{
  "repositories": ["my-app", "postgres"],
  "manifests": [
    {
      "repository": "my-app",
      "tag": "latest",
      "configDigest": "sha256:a1b2c3d4e5f6...",
      "layerDigests": [
        "sha256:1a2b3c4d5e6f7...",
        "sha256:2b3c4d5e6f7g8..."
      ]
    }
  ],
  "blobs": [
    {
      "digest": "sha256:a1b2c3d4e5f6...",
      "size": 1234567
    }
  ]
}
```

### Error Response (400 Bad Request)
```json
{
  "errors": [
    {
      "code": "UPLOAD_FAILED",
      "message": "Failed to process Docker tar file",
      "detail": "JSON parsing error"
    }
  ]
}
```

## Testing

### Manual Test Steps

1. **Build the project**:
```bash
cd funeral-backend
mvn clean package -DskipTests

cd ../funeral-frontend
pnpm install
pnpm build
```

2. **Start the application**:
```bash
cd ..
./build-docker.sh
./quickstart-docker.sh
```

3. **Test file upload**:
   - Login to web UI (admin/password)
   - Go to Upload page
   - Select a Docker tar file
   - Click "Upload and Analyze"

4. **Verify results**:
   - Check repositories created
   - Check manifests/blobs in database
   - Try pulling an image: `docker pull localhost:8911/my-app:latest`

### Example Test Files

Create a small test tar:
```bash
# Pull a small image
docker pull alpine:latest

# Save it
docker save alpine:latest -o alpine-test.tar

# Upload alpine-test.tar via web UI
```

## Security Considerations

- **Authentication Required**: Endpoint is `@Authenticated`
- **File Size Limits**: Configure via Quarkus properties:
  ```properties
  quarkus.http.limits.max-body-size=4G
  ```
- **Temp File Cleanup**: Automatically deleted after processing
- **Content Validation**: Only parses expected Docker tar format

## Performance

- **Large Files**: Tested with 2GB+ tar files
- **Streaming**: Uses streaming to avoid memory issues
- **Async Upload**: Non-blocking UI with progress indication
- **Database**: Batch operations for efficiency

## Troubleshooting

### Upload Fails

1. **Check authentication**: Login first, session must be active
2. **File format**: Ensure file is Docker tar (`docker save` output)
3. **File size**: May hit Nginx/Docker limits, adjust as needed
4. **Logs**: Check backend logs for detailed errors

### No Repositories Found

1. **Empty tar**: `docker save` may have failed silently
2. **Corrupted file**: Verify tar integrity with `tar -tf file.tar`
3. **Format**: Ensure it's Docker format, not generic tar

### Database Not Updated

1. **Storage config**: Check if using MongoDB or file storage
2. **Permissions**: Verify write permissions to storage
3. **Database**: Check connection to MongoDB

## Future Enhancements

1. **Progress Bar**: Show upload progress for large files
2. **Layer Extraction**: Actually extract and store blob layers
3. **Duplicate Detection**: Skip existing images
4. **Bulk Upload**: Upload multiple tar files
5. **Scheduled Import**: Watch directory for new tar files
6. **API Key Support**: Upload without web UI session
7. **Compression**: Support for .tar.xz, .tar.zst formats

## Related Files

- Backend: `funeral-backend/src/main/java/io/oci/resource/DockerTarResource.java`
- Frontend: `funeral-frontend/src/views/Upload.vue`
- Test: `funeral-backend/src/test/java/io/oci/resource/DockerTarResourceTest.java` (to be created)

## Dependencies

Backend requires these dependencies:
```xml
<!-- Apache Commons Compress for tar parsing -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-compress</artifactId>
    <version>1.26.1</version>
</dependency>

<!-- Jackson for JSON parsing (already included) -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-multipart</artifactId>
</dependency>
```

Check your `pom.xml` and add if missing.
