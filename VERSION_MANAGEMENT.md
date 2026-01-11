# FUNERAL Version Management

This document describes the unified version management system for the FUNERAL OCI Registry project.

## Overview

FUNERAL uses the backend pom.xml as the single source of truth for version management:
- **Backend (pom.xml)**: Maven project version is the source of truth
- **Frontend sync**: npm package version is synchronized from pom.xml during build
- **VERSION file**: Cached version for reference, automatically updated from pom.xml

## Version Format

Versions follow semantic versioning (SemVer) format: `MAJOR.MINOR.PATCH`
- **MAJOR**: Significant changes, API breaking changes
- **MINOR**: New features, backwards compatible
- **PATCH**: Bug fixes, minor changes

## Updating Version

To update the version for the entire project:

1. Update the version in `funeral-backend/pom.xml`:
   ```xml
   <version>0.2.0</version>
   ```

2. Run the sync script to update all components:
   ```bash
   cd /path/to/funeral
   ./sync-version.sh
   ```

Or simply build the frontend, which will automatically sync from pom.xml:
```bash
cd funeral-frontend
pnpm build  # Automatically syncs version from pom.xml
```

This will automatically update:
- `VERSION` - Project root version file
- `funeral-frontend/package.json` - npm package version

## Automated Sync

### Frontend Build
The frontend build process automatically syncs version from backend pom.xml before building:
```bash
cd funeral-frontend
pnpm build  # Automatically syncs version from pom.xml
```

### Frontend Development
The development server also syncs version from pom.xml on startup:
```bash
cd funeral-frontend
pnpm dev  # Automatically syncs version from pom.xml
```

### Manual Sync
You can also manually sync versions without building:
```bash
# Sync all components from VERSION file
cd /path/to/funeral
./sync-version.sh

# Or sync frontend only from pom.xml
cd funeral-frontend
pnpm sync-version
```

## Version Files

### funeral-backend/pom.xml
Source of truth for project version. Update this file to change the version.

```xml
<project>
  <groupId>com.xenoamess.oci-j</groupId>
  <artifactId>funeral</artifactId>
  <version>0.1.5</version> <!-- Source of truth -->
  ...
</project>
```

### funeral-frontend/package.json
npm package version is automatically synchronized from pom.xml during build.

```json
{
  "name": "funeral-frontend",
  "version": "0.1.5", // Auto-synced from pom.xml
  ...
}
```

### VERSION (Root)
Cached version reference, automatically updated from pom.xml.

```
0.1.5
```

## Build Integration

### Backend (Maven)
The Maven build does not automatically sync versions. Update pom.xml directly:
```bash
# Edit funeral-backend/pom.xml to update version
# Then build:
cd funeral-backend
mvn clean package
```

### Frontend (pnpm)
The pnpm build automatically syncs version from pom.xml:
```bash
cd funeral-frontend
pnpm build  # Auto-syncs version from pom.xml before building
```

### Frontend Development
The development server automatically syncs version from pom.xml on startup:
```bash
cd funeral-frontend
pnpm dev  # Auto-syncs version from pom.xml before starting dev server
```

## CI/CD Integration

For CI/CD pipelines, you can either let frontend builds auto-sync or manually sync first:

### GitHub Actions Example
```yaml
- name: Build backend
  run: cd funeral-backend && mvn clean package

- name: Build frontend (auto-syncs from pom.xml)
  run: cd funeral-frontend && pnpm build
```

Or manually sync for more control:
```yaml
- name: Sync versions from pom.xml
  run: cd funeral-backend && mvn help:evaluate -Dexpression=project.version -q -DforceStdout > ../VERSION

- name: Build backend
  run: cd funeral-backend && mvn clean package

- name: Build frontend
  run: cd funeral-frontend && pnpm build
```

## Best Practices

1. **Update backend pom.xml first**: The backend pom.xml is the source of truth
2. **Commit pom.xml changes**: Version changes in pom.xml should be committed to git
3. **Tag releases**: Create git tags matching the version number
   ```bash
   git tag -a v0.1.5 -m "Release version 0.1.5"
   git push origin v0.1.5
   ```
4. **Update CHANGELOG**: Document significant changes for each version
5. **Test after version updates**: Always test builds after version changes
6. **Don't manually edit package.json version**: It's auto-synced from pom.xml
7. **Keep VERSION file in sync**: It's automatically updated from pom.xml during frontend build

## Troubleshooting

### Version not updating
- Check that pom.xml exists and contains a valid version number
- Ensure you have proper permissions to modify pom.xml and package.json
- Run sync script manually: `cd /path/to/funeral && ./sync-frontend-from-pom.sh`
- Run script with verbose output: `bash -x ./sync-frontend-from-pom.sh`

### Script fails on macOS
- The script uses BSD sed syntax for macOS compatibility
- If errors persist, install GNU sed: `brew install gnu-sed`

### Unexpected version changes in dependencies
- The script only updates the project version, not dependency versions
- If dependency versions are changed, restore from git and check script

### Frontend build fails after version update
- Ensure pom.xml has valid XML format
- Check that the version tag exists after artifactId in pom.xml
- Verify pnpm/node.js is properly installed

## Version History

View git history of VERSION file:
```bash
git log -p VERSION
```
