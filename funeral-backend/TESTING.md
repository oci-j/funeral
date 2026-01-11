# FUNERAL Backend API Testing Guide

This document provides comprehensive testing guidelines for the FUNERAL OCI Registry backend API.

## Test Coverage Status

### Completed Tests
- ✅ **OCI v2 Manifest Endpoints** (`ManifestResourceHandlerTest`)
  - GET/HEAD/PUT/DELETE manifests
  - Custom manifest info endpoint

- ✅ **OCI v2 Blob Endpoints** (`BlobResourceHandlerTest`)
  - GET/HEAD/DELETE blobs
  - Upload operations (POST/PUT/PATCH)
  - Monolithic and chunked uploads

- ✅ **OCI v2 Tag Endpoints** (`TagResourceHandlerTest`)
  - List tags with pagination
  - Limit and last parameters

- ✅ **Registry Endpoints** (`RegistryResourceHandlerTest`)
  - Version check endpoint
  - Repository listing

- ✅ **Referrer Endpoints** (`ReferrerResourceHandlerTest`)
  - Get referrers with optional artifact type filtering

### Remaining Tests to Implement
- **Token Endpoints** - Authentication and authorization
- **Admin Endpoints** - User and permission management
- **Config Endpoints** - Configuration retrieval
- **Docker Tar Upload Endpoints** - Docker tar file uploads
- **Debug Endpoints** - Debug information retrieval

## Prerequisites

### 1. Test Dependencies
All test dependencies are already configured in `pom.xml`:
- Quarkus JUnit 5 (`quarkus-junit5`)
- RestAssured (`rest-assured`) - API testing
- Mockito (`mockito-core`) - Mocking
- AssertJ (`assertj-core`) - Assertions
- TestContainers (`testcontainers-junit-jupiter`, `mongodb`, `minio`)

### 2. Docker (for TestContainers)
Ensure Docker is running for integration tests that use TestContainers:
```bash
docker ps
```

## Running Tests

### Run All Tests
```bash
cd funeral-backend
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=ManifestResourceHandlerTest
mvn test -Dtest=BlobResourceHandlerTest
mvn test -Dtest=TagResourceHandlerTest
mvn test -Dtest=RegistryResourceHandlerTest
mvn test -Dtest=ReferrerResourceHandlerTest
```

### Run Tests with Coverage Report
```bash
mvn test jacoco:report
```

### Run Tests in Development Mode (with hot reload)
```bash
mvn quarkus:dev
# In another terminal
mvn test-compile
```

## Native Compilation and Reachability Metadata

### Run Tests in Native Mode
```bash
# This runs tests using native image (requires GraalVM)
mvn test -Pnative

# Or
mvn clean test -Dnative
```

### Incrementally Update Reachability Metadata

The pom.xml is configured to automatically update reachability metadata:

```bash
# Run instrumented tests to generate reachability metadata
mvn clean verify -Pnative
```

This will:
1. Run tests with the GraalVM agent attached
2. Generate reachability metadata in `target/graalvm-reachability-metadata/`
3. Automatically copy metadata to `src/main/resources/META-INF/native-image/`

### Native Build Options

The following properties are configured in the `native` profile:

- `quarkus.native.enabled=true` - Enable native compilation
- `quarkus.native.auto-reachability-metadata=true` - Automatically include reachability metadata
- `quarkus.native.additional-build-args=-march=compatibility --allow-incomplete-classpath` - Build arguments

### Clean Native Build
```bash
# Clean and rebuild native image with fresh metadata
mvn clean install -Pnative -DskipTests=false
```

## Test Environment Configuration

### Local Development
For local testing without external services, tests use TestContainers:
- MongoDB container for database
- MinIO container for S3 storage

### External Services
For testing against real services, set environment variables:
```bash
export MONGO_URL=mongodb://localhost:27017
export S3_ENDPOINT=http://localhost:9000
export S3_ACCESS_KEY=admin
export S3_SECRET_KEY=password
export S3_BUCKET=test-bucket
```

## Writing New Tests

### Test Class Structure
```java
@QuarkusTest
public class YourResourceTest {
    @TestHTTPResource
    String baseUrl;

    @Test
    public void testYourEndpoint() {
        given()
            .contentType("application/json")
            .body("{\"test\": \"data\"}")
            .when()
            .post("/your/endpoint")
            .then()
            .statusCode(200)
            .body("result", equalTo("success"));
    }
}
```

### Using TestContainers
```java
@QuarkusTest
@QuarkusTestResource(MongoDBTestResource.class)
public class YourIntegrationTest {
    // Tests can use @Inject to get services
    @Inject
    YourService service;

    @Test
    public void testWithDatabase() {
        // Test code here
    }
}
```

## CI/CD Integration

### GitHub Actions Example
```yaml
- name: Run Tests
  run: |
    cd funeral-backend
    mvn clean test

- name: Run Native Tests
  run: |
    cd funeral-backend
    mvn test -Pnative

- name: Check Reachability Metadata Updates
  run: |
    git diff --name-only src/main/resources/META-INF/native-image/ || echo "No metadata changes"
```

## Test Data Setup

### Initial Test Data
The test suite can populate initial data:
- Sample repositories
- Test manifests and blobs
- User accounts

### Cleanup
Tests automatically clean up after themselves using:
- `@BeforeEach` and `@AfterEach` hooks
- TestContainers lifecycle management
- Database cleanup scripts

## Troubleshooting

### Common Issues

1. **Port Conflicts**: Tests use random ports. If you see port conflicts:
   ```bash
   # Kill processes using test ports
   lsof -ti:8080 | xargs kill -9
   ```

2. **Native Test Failures**: Ensure GraalVM is properly installed:
   ```bash
   mvn -Pnative -Dquarkus.native.container-build=true clean verify
   ```

3. **TestContainer Issues**: Increase startup timeout:
   ```java
   @Testcontainers
   public class YourTest {
       @Container
       static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0")
               .withStartupTimeout(Duration.ofMinutes(5));
   }
   ```

## Best Practices

1. **Isolation**: Each test should be independent
2. **Cleanup**: Always clean up test data
3. **Assertions**: Use AssertJ for fluent assertions
4. **Mocking**: Use Mockito for external services
5. **Coverage**: Aim for 80%+ code coverage
6. **Performance**: Tests should run in < 10 seconds each

## Performance Testing

For load testing:
```bash
# Install k6
curl https://github.com/grafana/k6/releases/download/v0.46.0/k6-v0.46.0-linux-amd64.tar.gz | tar xvz

# Run load test
./k6 run scripts/load-test.js
```

## Future Enhancements

- [ ] Add contract tests with Pact
- [ ] Performance benchmarks in CI
- [ ] Mutation testing with Pitest
- [ ] Code coverage gates (80% minimum)
- [ ] Visual test reports
- [ ] Load testing automation
