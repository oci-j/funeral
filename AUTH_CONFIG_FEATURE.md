# Authentication Configuration Feature

## Overview

This feature allows the FUNERAL OCI registry to dynamically adapt its frontend based on the backend authentication configuration. When authentication is disabled on the backend (`oci.auth.enabled = false`), the frontend automatically disables login requirements and treats all users as administrators.

## How It Works

### Backend Configuration

The backend exposes authentication settings through a new endpoint:

```
GET /funeral_addition/config/auth
```

Response:
```json
{
  "enabled": false,
  "allowAnonymousPull": false,
  "realm": "http://localhost:8911/v2/token"
}
```

### Frontend Behavior

1. **App Startup**: The frontend automatically checks the auth configuration when the app loads
2. **Auth Status Display**: A status tag in the header shows whether authentication is enabled or disabled
3. **Login Button**: When auth is disabled, the login button is grayed out and shows "Login Disabled"
4. **Auto Admin Rights**: When auth is disabled, all users are treated as administrators
5. **Route Access**: Router guards allow access to all routes when auth is disabled

### Configuration Flow

1. User opens the FUNERAL registry web UI
2. Frontend calls `/funeral_addition/config/auth` on startup
3. If `enabled: false`:
   - Login button is disabled
   - All routes become accessible
   - All users have admin rights
   - Auth status tag shows "Auth Disabled (Admin)" in green
4. If `enabled: true`:
   - Normal authentication flow
   - Login required for protected routes
   - Auth status tag shows "Auth Enabled" in red

## Visual Indicators

### When Auth is Enabled (Default)
- 🔒 Red tag in header: "Auth Enabled"
- Login button is active and clickable
- Login required for protected routes

### When Auth is Disabled
- 🔓 Green tag in header: "Auth Disabled (Admin)"
- Login button is disabled and shows "Login Disabled"
- All routes are accessible
- All users have admin privileges

## Backend Configuration

To disable authentication, set in `application.yml`:

```yaml
oci:
  auth:
    enabled: false
```

## Testing

1. Set `oci.auth.enabled: false` in backend configuration
2. Restart the backend service
3. Open the frontend in a private/incognito window
4. Verify:
   - [ ] Auth status tag shows "Auth Disabled (Admin)" in green
   - [ ] Login button is disabled
   - [ ] All pages are accessible without login
   - [ ] Admin menu is visible
   - [ ] Upload functionality works

## Security Considerations

⚠️ **Warning**: Disabling authentication allows **anyone** with network access to:
- Pull any image from the registry
- Push new images to the registry
- Delete images and repositories
- Access admin features

**Only disable authentication in trusted environments or for testing purposes.**

## Implementation Details

### New Components

1. **ConfigResource.java**: Backend endpoint exposing auth configuration
2. **Auth store updates**: Frontend auth store now tracks `authEnabled` state
3. **Router guards**: Updated to respect auth configuration

### Modified Files

- `funeral-backend/src/main/java/io/oci/resource/funeral_addition/configResource.java` - New
- `funeral-frontend/src/stores/auth.js` - Added auth config checking
- `funeral-frontend/src/router/index.js` - Updated guards
- `funeral-frontend/src/App.vue` - Added status indicator and disabled login button
- `funeral-frontend/src/main.js` - Check auth config on startup

## API Endpoints

### Get Auth Configuration
```
GET /funeral_addition/config/auth
Response: { enabled: boolean, allowAnonymousPull: boolean, realm: string }
```

### Get All Configuration
```
GET /funeral_addition/config/all
Response: { auth: { ... }, ... }
```
