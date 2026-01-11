# FUNERAL Frontend

Vue 3 + Element Plus frontend for FUNERAL OCI Registry

## Project Setup

```bash
cd funeral-frontend

# Install dependencies
pnpm install

# Start development server
pnpm dev
```

## Development

The frontend will run on http://localhost:3000 and proxy API requests to the backend (http://localhost:8911).

## Features

- **User Authentication**: Login/logout with JWT token support
- **Repository Management**: Browse and manage OCI repositories
- **Repository Details**: View tags and manifests with pull commands
- **Upload Guidance**: Step-by-step instructions for pushing images
- **Protected Routes**: Authentication required for accessing registry features

## Default Credentials

- Username: `admin`
- Password: `password`

## Authentication Flow

1. Users are redirected to login page if not authenticated
2. JWT tokens are stored in localStorage for session persistence
3. All API requests include Bearer token authentication
4. Automatic logout on token expiration or 401 responses
5. Logged-in users see their username in the header with logout option

## API Integration

The frontend integrates with the backend OCI registry API:
- `/v2/token` - Authentication endpoint
- `/v2/_catalog` - List repositories
- `/v2/{name}/tags/list` - List repository tags
- `/v2/{name}/` - Delete repository

All requests automatically include authentication headers when user is logged in.
