package io.oci.cli.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oci.cli.auth.Credentials;
import io.oci.dto.ErrorResponse;
import io.oci.dto.RepositoryInfo;
import io.oci.dto.TagsResponse;
import io.oci.dto.TokenResponse;
import io.oci.dto.UserRequest;
import io.oci.dto.UserResponse;
import io.oci.model.RepositoryPermission;

public class FuneralClient {

    private static final String DEFAULT_SERVICE = "funeral-registry";

    static {
        String allowed = System.getProperty(
                "jdk.httpclient.allowRestrictedHeaders",
                ""
        );
        if (!allowed.contains(
                "Host"
        )) {
            System.setProperty(
                    "jdk.httpclient.allowRestrictedHeaders",
                    allowed.isEmpty() ? "Host" : allowed + ",Host"
            );
        }
    }

    private final HttpClient httpClient;

    private final String baseUrl;

    private final String hostOverride;

    private final Credentials credentials;

    private final ObjectMapper mapper = new ObjectMapper();

    private String token;

    public FuneralClient(
            String registry,
            Credentials credentials
    ) {
        this(
                registry,
                null,
                credentials
        );
    }

    public FuneralClient(
            String registry,
            String hostOverride,
            Credentials credentials
    ) {
        this.baseUrl = baseUrl(
                registry
        );
        this.hostOverride = hostOverride;
        this.credentials = credentials;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        Duration.ofSeconds(
                                10
                        )
                )
                .build();
    }

    private String baseUrl(
            String registry
    ) {
        if (registry.startsWith(
                "http://"
        ) || registry.startsWith(
                "https://"
        )) {
            return registry;
        }
        return "http://" + registry;
    }

    public String getToken() throws IOException, InterruptedException {
        if (credentials == null) {
            return null;
        }
        String auth = Base64.getEncoder()
                .encodeToString(
                        (credentials.username + ":" + credentials.password).getBytes(
                                StandardCharsets.UTF_8
                        )
                );
        String url = baseUrl + "/v2/token?service=" + DEFAULT_SERVICE;
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create(
                        url
                )
        );
        addHost(
                builder
        );
        HttpRequest request = builder.header(
                "Authorization",
                "Basic " + auth
        ).GET().build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Authentication failed: " + response.statusCode() + " " + response.body()
            );
        }
        TokenResponse tokenResponse = mapper.readValue(
                response.body(),
                TokenResponse.class
        );
        return tokenResponse.accessToken != null ? tokenResponse.accessToken : tokenResponse.token;
    }

    public HttpResponse<String> get(
            String path
    )
            throws IOException,
            InterruptedException {
        return request(
                "GET",
                path,
                null,
                null
        );
    }

    public HttpResponse<String> post(
            String path,
            String body,
            String contentType
    )
            throws IOException,
            InterruptedException {
        return request(
                "POST",
                path,
                body,
                contentType
        );
    }

    public HttpResponse<String> put(
            String path,
            String body,
            String contentType
    )
            throws IOException,
            InterruptedException {
        return request(
                "PUT",
                path,
                body,
                contentType
        );
    }

    public HttpResponse<String> delete(
            String path
    )
            throws IOException,
            InterruptedException {
        return request(
                "DELETE",
                path,
                null,
                null
        );
    }

    public boolean blobExists(
            String name,
            String digest
    )
            throws IOException,
            InterruptedException {
        HttpResponse<byte[]> response = requestBytes(
                "HEAD",
                "/v2/" + name + "/blobs/" + digest,
                null,
                null
        );
        return response.statusCode() == 200;
    }

    public void uploadBlob(
            String name,
            String digest,
            byte[] bytes
    )
            throws IOException,
            InterruptedException {
        HttpResponse<byte[]> response = requestBytes(
                "POST",
                "/v2/" + name + "/blobs/uploads/?digest=" + digest,
                bytes,
                "application/octet-stream"
        );
        ensureSuccessBytes(
                response
        );
    }

    public byte[] getBlob(
            String name,
            String digest
    )
            throws IOException,
            InterruptedException {
        HttpResponse<byte[]> response = requestBytes(
                "GET",
                "/v2/" + name + "/blobs/" + digest,
                null,
                null
        );
        ensureSuccessBytes(
                response
        );
        return response.body();
    }

    public byte[] getManifest(
            String name,
            String reference
    )
            throws IOException,
            InterruptedException {
        HttpResponse<byte[]> response = getManifestResponse(
                name,
                reference
        );
        return response.body();
    }

    public HttpResponse<byte[]> getManifestResponse(
            String name,
            String reference
    )
            throws IOException,
            InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create(
                        baseUrl + "/v2/" + name + "/manifests/" + reference
                )
        );
        addHost(
                builder
        );
        addAuth(
                builder
        );
        builder.header(
                "Accept",
                "application/vnd.oci.image.manifest.v1+json, " + "application/vnd.oci.image.index.v1+json, "
                        + "application/vnd.docker.distribution.manifest.v2+json, "
                        + "application/vnd.docker.distribution.manifest.list.v2+json"
        );
        HttpRequest request = builder.GET().build();
        HttpResponse<byte[]> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
        );
        ensureSuccessBytes(
                response
        );
        return response;
    }

    public void putManifest(
            String name,
            String reference,
            byte[] bytes,
            String mediaType
    )
            throws IOException,
            InterruptedException {
        HttpResponse<byte[]> response = requestBytes(
                "PUT",
                "/v2/" + name + "/manifests/" + reference,
                bytes,
                mediaType
        );
        ensureSuccessBytes(
                response
        );
    }

    public String uploadDockerTar(
            Path tarFile
    )
            throws IOException,
            InterruptedException {
        byte[] fileBytes = Files.readAllBytes(
                tarFile
        );
        String boundary = "----FuneralBoundary" + System.currentTimeMillis();
        String header = "--" + boundary + "\r\n" + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + tarFile.getFileName() + "\"\r\n" + "Content-Type: application/octet-stream\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(
                header.getBytes(
                        StandardCharsets.UTF_8
                )
        );
        baos.write(
                fileBytes
        );
        baos.write(
                footer.getBytes(
                        StandardCharsets.UTF_8
                )
        );
        byte[] body = baos.toByteArray();
        HttpResponse<byte[]> response = requestBytes(
                "POST",
                "/funeral_addition/write/upload/dockertar",
                body,
                "multipart/form-data; boundary=" + boundary
        );
        ensureSuccessBytes(
                response
        );
        return new String(
                response.body(),
                StandardCharsets.UTF_8
        );
    }

    private HttpResponse<byte[]> requestBytes(
            String method,
            String path,
            byte[] body,
            String contentType
    )
            throws IOException,
            InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create(
                        baseUrl + path
                )
        );
        addHost(
                builder
        );
        addAuth(
                builder
        );
        if (contentType != null) {
            builder.header(
                    "Content-Type",
                    contentType
            );
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(
                        body
                );
        HttpRequest request = builder.method(
                method,
                publisher
        ).build();
        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private void ensureSuccessBytes(
            HttpResponse<byte[]> response
    ) {
        if (response.statusCode() >= 400) {
            String message = response.body() != null
                    ? new String(
                            response.body(),
                            StandardCharsets.UTF_8
                    )
                    : "";
            try {
                ErrorResponse error = mapper.readValue(
                        message,
                        ErrorResponse.class
                );
                if (error.errors != null && !error.errors.isEmpty()) {
                    message = error.errors.get(
                            0
                    ).message;
                }
            }
            catch (Exception e) {
            }
            throw new RuntimeException(
                    "Request failed: HTTP " + response.statusCode() + " " + message
            );
        }
    }

    private HttpResponse<String> request(
            String method,
            String path,
            String body,
            String contentType
    )
            throws IOException,
            InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create(
                        baseUrl + path
                )
        );
        addHost(
                builder
        );
        addAuth(
                builder
        );
        if (contentType != null) {
            builder.header(
                    "Content-Type",
                    contentType
            );
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(
                        body
                );
        HttpRequest request = builder.method(
                method,
                publisher
        ).build();
        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private void addHost(
            HttpRequest.Builder builder
    ) {
        if (hostOverride != null && !hostOverride.isBlank()) {
            builder.header(
                    "Host",
                    hostOverride
            );
        }
    }

    private void addAuth(
            HttpRequest.Builder builder
    )
            throws IOException,
            InterruptedException {
        if (credentials != null) {
            if (token == null) {
                token = getToken();
            }
            if (token != null) {
                builder.header(
                        "Authorization",
                        "Bearer " + token
                );
            }
        }
    }

    public List<RepositoryInfo> listRepositories() throws IOException, InterruptedException {
        HttpResponse<String> response = get(
                "/v2/repositories"
        );
        ensureSuccess(
                response
        );
        return mapper.readValue(
                response.body(),
                new TypeReference<List<RepositoryInfo>>() {
                }
        );
    }

    public List<String> listTags(
            String name
    )
            throws IOException,
            InterruptedException {
        HttpResponse<String> response = get(
                "/v2/" + name + "/tags/list"
        );
        ensureSuccess(
                response
        );
        TagsResponse tags = mapper.readValue(
                response.body(),
                TagsResponse.class
        );
        return tags.tags;
    }

    public void deleteRepository(
            String name
    )
            throws IOException,
            InterruptedException {
        HttpResponse<String> response = delete(
                "/v2/" + name
        );
        ensureSuccess(
                response
        );
    }

    public void deleteManifest(
            String name,
            String reference
    )
            throws IOException,
            InterruptedException {
        HttpResponse<String> response = delete(
                "/v2/" + name + "/manifests/" + reference
        );
        ensureSuccess(
                response
        );
    }

    public String mirrorImage(
            String sourceImage,
            String targetRepository,
            String targetTag,
            String username,
            String password
    )
            throws IOException,
            InterruptedException {
        StringBuilder body = new StringBuilder();
        appendForm(
                body,
                "sourceImage",
                sourceImage
        );
        appendForm(
                body,
                "targetRepository",
                targetRepository
        );
        appendForm(
                body,
                "targetTag",
                targetTag
        );
        appendForm(
                body,
                "username",
                username
        );
        appendForm(
                body,
                "password",
                password
        );
        HttpResponse<String> response = post(
                "/funeral_addition/mirror/pull",
                body.toString(),
                "application/x-www-form-urlencoded"
        );
        ensureSuccess(
                response
        );
        return response.body();
    }

    public String mirrorHelm(
            String sourceRepo,
            String chartName,
            String version,
            String targetRepository,
            String targetVersion,
            String username,
            String password,
            String format
    )
            throws IOException,
            InterruptedException {
        StringBuilder body = new StringBuilder();
        appendForm(
                body,
                "sourceRepo",
                sourceRepo
        );
        appendForm(
                body,
                "chartName",
                chartName
        );
        appendForm(
                body,
                "version",
                version
        );
        appendForm(
                body,
                "targetRepository",
                targetRepository
        );
        appendForm(
                body,
                "targetVersion",
                targetVersion
        );
        appendForm(
                body,
                "username",
                username
        );
        appendForm(
                body,
                "password",
                password
        );
        appendForm(
                body,
                "format",
                format
        );
        HttpResponse<String> response = post(
                "/funeral_addition/mirror/helm/pull",
                body.toString(),
                "application/x-www-form-urlencoded"
        );
        ensureSuccess(
                response
        );
        return response.body();
    }

    private void appendForm(
            StringBuilder sb,
            String key,
            String value
    ) {
        if (value == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(
                    "\u0026"
            );
        }
        sb.append(
                URLEncoder.encode(
                        key,
                        StandardCharsets.UTF_8
                )
        )
                .append(
                        "="
                )
                .append(
                        URLEncoder.encode(
                                value,
                                StandardCharsets.UTF_8
                        )
                );
    }

    public List<UserResponse> listUsers() throws IOException, InterruptedException {
        HttpResponse<String> response = get(
                "/funeral_addition/admin/users"
        );
        ensureSuccess(
                response
        );
        return mapper.readValue(
                response.body(),
                new TypeReference<List<UserResponse>>() {
                }
        );
    }

    public UserResponse createUser(
            UserRequest request
    )
            throws IOException,
            InterruptedException {
        String body = mapper.writeValueAsString(
                request
        );
        HttpResponse<String> response = post(
                "/funeral_addition/admin/users",
                body,
                "application/json"
        );
        ensureSuccess(
                response
        );
        return mapper.readValue(
                response.body(),
                UserResponse.class
        );
    }

    public UserResponse updateUser(
            String username,
            UserRequest request
    )
            throws IOException,
            InterruptedException {
        String body = mapper.writeValueAsString(
                request
        );
        HttpResponse<String> response = put(
                "/funeral_addition/admin/users/" + username,
                body,
                "application/json"
        );
        ensureSuccess(
                response
        );
        return mapper.readValue(
                response.body(),
                UserResponse.class
        );
    }

    public void deleteUser(
            String username
    )
            throws IOException,
            InterruptedException {
        HttpResponse<String> response = delete(
                "/funeral_addition/admin/users/" + username
        );
        ensureSuccess(
                response
        );
    }

    public List<RepositoryPermission> listPermissions() throws IOException, InterruptedException {
        HttpResponse<String> response = get(
                "/funeral_addition/admin/permissions"
        );
        ensureSuccess(
                response
        );
        return mapper.readValue(
                response.body(),
                new TypeReference<List<RepositoryPermission>>() {
                }
        );
    }

    public List<RepositoryPermission> listPermissions(
            String username
    )
            throws IOException,
            InterruptedException {
        HttpResponse<String> response = get(
                "/funeral_addition/admin/permissions/" + username
        );
        ensureSuccess(
                response
        );
        return mapper.readValue(
                response.body(),
                new TypeReference<List<RepositoryPermission>>() {
                }
        );
    }

    public void setPermission(
            String username,
            String repository,
            RepositoryPermission request
    )
            throws IOException,
            InterruptedException {
        String body = mapper.writeValueAsString(
                request
        );
        HttpResponse<String> response = post(
                "/funeral_addition/admin/permissions/" + username + "/" + repository,
                body,
                "application/json"
        );
        ensureSuccess(
                response
        );
    }

    public void deletePermission(
            String username,
            String repository
    )
            throws IOException,
            InterruptedException {
        HttpResponse<String> response = delete(
                "/funeral_addition/admin/permissions/" + username + "/" + repository
        );
        ensureSuccess(
                response
        );
    }

    public void ensureSuccess(
            HttpResponse<String> response
    ) {
        if (response.statusCode() >= 400) {
            String message = response.body();
            try {
                ErrorResponse error = mapper.readValue(
                        response.body(),
                        ErrorResponse.class
                );
                if (error.errors != null && !error.errors.isEmpty()) {
                    message = error.errors.get(
                            0
                    ).message;
                }
            }
            catch (Exception e) {
            }
            throw new RuntimeException(
                    "Request failed: HTTP " + response.statusCode() + " " + message
            );
        }
    }
}
