# Docker Tar Batch Upload Guide

## Overview

The FUNERAL OCI registry now supports batch uploading multiple Docker tar files in a single request. This allows you to upload several images at once, saving time and reducing network overhead.

## Features

- **Multiple File Upload**: Upload multiple `.tar`, `.tar.gz`, `.tgz`, `.tar.zst`, or `.zip` files simultaneously
- **Individual Processing**: Each file is processed independently, so one failed upload doesn't affect others
- **Comprehensive Results**: View summary statistics and detailed results for each uploaded file
- **Progress Tracking**: Monitor upload progress and see which files succeeded or failed

## Using the Web UI

1. Navigate to the **Upload** page in the FUNERAL registry web interface
2. Select multiple files by:
   - Dragging and dropping multiple files onto the upload area
   - Clicking to browse and selecting multiple files with Ctrl/Cmd+click or Shift+click
3. Click **"Upload and Analyze"** to start the batch upload
4. View the upload summary and individual file results

## Using the API

### Endpoint

```
POST /funeral_addition/write/upload/dockertar/batch
```

### Content-Type

```
multipart/form-data
```

### Request Format

Send multiple files with the field name `files`:

```bash
curl -X POST \
  -F "files=@image1.tar" \
  -F "files=@image2.tar.gz" \
  -F "files=@image3.tar.zst" \
  http://your-registry:8911/funeral_addition/write/upload/dockertar/batch
```

### Response Format

```json
{
  "totalFiles": 3,
  "successfulUploads": 3,
  "failedUploads": 0,
  "repositories": ["nginx", "ubuntu", "alpine"],
  "manifests": [...],
  "blobs": [...],
  "results": [
    {
      "fileIndex": 1,
      "success": true,
      "uploadResponse": {
        "repositories": ["nginx"],
        "manifests": [...],
        "blobs": [...]
      },
      "error": null
    },
    ...
  ]
}
```

## Error Handling

If some files fail to upload, the response will include:

1. **Overall summary** showing how many files succeeded and failed
2. **Individual results** for each file with success/failure status
3. **Error messages** for any failed uploads

Example error response:

```json
{
  "totalFiles": 3,
  "successfulUploads": 2,
  "failedUploads": 1,
  "repositories": ["nginx", "ubuntu"],
  "results": [
    {
      "fileIndex": 1,
      "success": true,
      "uploadResponse": {...}
    },
    {
      "fileIndex": 2,
      "success": false,
      "error": "Invalid tar file format"
    },
    {
      "fileIndex": 3,
      "success": true,
      "uploadResponse": {...}
    }
  ]
}
```

## Testing

Use the provided test script:

```bash
./test_batch_upload.sh image1.tar image2.tar.gz image3.tar.zst
```

## Supported File Types

- `.tar` - Plain tar files
- `.tar.gz` or `.tgz` - Gzip compressed tar files
- `.tar.zst` - Zstandard compressed tar files
- `.zip` - Zip files containing tar archives

## Tips

1. **File Size**: Large files may take longer to upload and process
2. **Network**: Ensure stable network connection for large batch uploads
3. **Authentication**: Include authentication token if registry requires login
4. **Duplicate Images**: If uploading images that already exist, they will be overwritten

## Debugging

Check the registry logs for detailed processing information:

- Each file's processing is logged with file index
- Config and layer sizes are logged during tar parsing
- Blob storage operations are logged with sizes and digests

Use the debug endpoints to verify uploaded content:

```
GET /debug/manifest/{repository}/{tag}
GET /debug/blob/{digest}
GET /debug/manifests/list
```
