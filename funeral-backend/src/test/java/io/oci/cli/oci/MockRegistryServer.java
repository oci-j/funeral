package io.oci.cli.oci;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class MockRegistryServer implements AutoCloseable {

    private static final String TOKEN = "mock-token";

    private HttpServer server;

    private String baseUrl;

    private final Map<String, byte[]> blobs = new HashMap<>();

    private final Map<String, ManifestEntry> manifests = new HashMap<>();

    private final List<String> hostHeaders = Collections.synchronizedList(
            new ArrayList<>()
    );

    private final List<String> requestPaths = Collections.synchronizedList(
            new ArrayList<>()
    );

    private volatile byte[] lastUploadedTar;

    private final AtomicInteger uploadCount = new AtomicInteger(
            0
    );

    private volatile boolean failUpload;

    private boolean requireAuth;

    private String username;

    private String password;

    public void start() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(
                        0
                ),
                0
        );
        server.createContext(
                "/",
                new Handler()
        );
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(
                    0
            );
        }
    }

    @Override
    public void close() {
        stop();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public void registerBlob(
            String digest,
            byte[] content
    ) {
        blobs.put(
                digest,
                content
        );
    }

    public void registerManifest(
            String repository,
            String reference,
            byte[] content,
            String mediaType
    ) {
        manifests.put(
                repository + "|" + reference,
                new ManifestEntry(
                        content,
                        mediaType
                )
        );
    }

    public void requireBasicAuth(
            String username,
            String password
    ) {
        this.requireAuth = true;
        this.username = username;
        this.password = password;
    }

    public List<String> recordedHostHeaders() {
        return new ArrayList<>(
                hostHeaders
        );
    }

    public List<String> recordedRequestPaths() {
        return new ArrayList<>(
                requestPaths
        );
    }

    public byte[] lastUploadedTarBytes() {
        return lastUploadedTar;
    }

    public int uploadCount() {
        return uploadCount.get();
    }

    public void failUpload(
            boolean fail
    ) {
        this.failUpload = fail;
    }

    private class Handler implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange
        )
                throws IOException {
            String host = exchange.getRequestHeaders()
                    .getFirst(
                            "Host"
                    );
            if (host != null) {
                hostHeaders.add(
                        host
                );
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            requestPaths.add(
                    method + " " + path
            );

            try {
                if (path.equals(
                        "/v2/token"
                )) {
                    handleToken(
                            exchange
                    );
                    return;
                }
                if (path.contains(
                        "/manifests/"
                )) {
                    handleManifest(
                            exchange,
                            path
                    );
                    return;
                }
                if (path.contains(
                        "/blobs/"
                )) {
                    handleBlob(
                            exchange,
                            path
                    );
                    return;
                }
                if (path.equals(
                        "/funeral_addition/write/upload/dockertar"
                ) && "POST".equals(
                        method
                )) {
                    handleUpload(
                            exchange
                    );
                    return;
                }
                send(
                        exchange,
                        404,
                        "Not found"
                );
            }
            finally {
                exchange.close();
            }
        }

        private void handleToken(
                HttpExchange exchange
        )
                throws IOException {
            if (requireAuth) {
                String auth = exchange.getRequestHeaders()
                        .getFirst(
                                "Authorization"
                        );
                if (auth == null || !auth.startsWith(
                        "Basic "
                ) || !checkBasicAuth(
                        auth
                )) {
                    send(
                            exchange,
                            401,
                            "Unauthorized"
                    );
                    return;
                }
            }
            sendJson(
                    exchange,
                    200,
                    "{\"token\":\"" + TOKEN + "\"}"
            );
        }

        private boolean checkBasicAuth(
                String auth
        ) {
            try {
                String creds = new String(
                        Base64.getDecoder()
                                .decode(
                                        auth.substring(
                                                6
                                        )
                                ),
                        StandardCharsets.UTF_8
                );
                int colon = creds.indexOf(
                        ':'
                );
                if (colon < 0) {
                    return false;
                }
                String u = creds.substring(
                        0,
                        colon
                );
                String p = creds.substring(
                        colon + 1
                );
                return u.equals(
                        username
                ) && p.equals(
                        password
                );
            }
            catch (Exception e) {
                return false;
            }
        }

        private void handleManifest(
                HttpExchange exchange,
                String path
        )
                throws IOException {
            int manifestsIdx = path.lastIndexOf(
                    "/manifests/"
            );
            if (manifestsIdx < 0) {
                send(
                        exchange,
                        404,
                        "Not found"
                );
                return;
            }
            String repo = path.substring(
                    "/v2/".length(),
                    manifestsIdx
            );
            String ref = path.substring(
                    manifestsIdx + "/manifests/".length()
            );
            ManifestEntry entry = manifests.get(
                    repo + "|" + ref
            );
            if (entry == null) {
                send(
                        exchange,
                        404,
                        "Manifest not found"
                );
                return;
            }
            exchange.getResponseHeaders()
                    .set(
                            "Content-Type",
                            entry.mediaType
                    );
            send(
                    exchange,
                    200,
                    entry.content
            );
        }

        private void handleBlob(
                HttpExchange exchange,
                String path
        )
                throws IOException {
            int blobsIdx = path.lastIndexOf(
                    "/blobs/"
            );
            if (blobsIdx < 0) {
                send(
                        exchange,
                        404,
                        "Not found"
                );
                return;
            }
            String digest = path.substring(
                    blobsIdx + "/blobs/".length()
            );
            byte[] content = blobs.get(
                    digest
            );
            if (content == null) {
                send(
                        exchange,
                        404,
                        "Blob not found"
                );
                return;
            }
            exchange.getResponseHeaders()
                    .set(
                            "Content-Type",
                            "application/octet-stream"
                    );
            send(
                    exchange,
                    200,
                    content
            );
        }

        private void handleUpload(
                HttpExchange exchange
        )
                throws IOException {
            String contentType = exchange.getRequestHeaders()
                    .getFirst(
                            "Content-Type"
                    );
            if (contentType == null || !contentType.startsWith(
                    "multipart/form-data"
            )) {
                send(
                        exchange,
                        400,
                        "Bad request"
                );
                return;
            }
            String boundary = extractBoundary(
                    contentType
            );
            if (boundary == null) {
                send(
                        exchange,
                        400,
                        "Bad request"
                );
                return;
            }
            uploadCount.incrementAndGet();
            if (failUpload) {
                send(
                        exchange,
                        500,
                        "Simulated upload failure"
                );
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            byte[] tar = extractMultipartFile(
                    body,
                    boundary
            );
            lastUploadedTar = tar;
            sendJson(
                    exchange,
                    200,
                    "{\"uploaded\":true}"
            );
        }

        private String extractBoundary(
                String contentType
        ) {
            int idx = contentType.indexOf(
                    "boundary="
            );
            if (idx < 0) {
                return null;
            }
            return contentType.substring(
                    idx + "boundary=".length()
            );
        }

        private byte[] extractMultipartFile(
                byte[] body,
                String boundary
        ) {
            byte[] boundaryBytes = ("--" + boundary).getBytes(
                    StandardCharsets.UTF_8
            );
            int firstBoundary = indexOf(
                    body,
                    boundaryBytes,
                    0
            );
            if (firstBoundary < 0) {
                return new byte[0];
            }
            int headerStart = firstBoundary + boundaryBytes.length + 2;
            byte[] headerEnd = "\r\n\r\n".getBytes(
                    StandardCharsets.UTF_8
            );
            int contentStart = indexOf(
                    body,
                    headerEnd,
                    headerStart
            );
            if (contentStart < 0) {
                return new byte[0];
            }
            contentStart += headerEnd.length;
            int nextBoundary = indexOf(
                    body,
                    boundaryBytes,
                    contentStart
            );
            if (nextBoundary < 0) {
                return new byte[0];
            }
            int contentEnd = nextBoundary - 2;
            if (contentEnd < contentStart) {
                return new byte[0];
            }
            return Arrays.copyOfRange(
                    body,
                    contentStart,
                    contentEnd
            );
        }

        private int indexOf(
                byte[] source,
                byte[] target,
                int start
        ) {
            for (int i = start; i <= source.length - target.length; i++) {
                boolean found = true;
                for (int j = 0; j < target.length; j++) {
                    if (source[i + j] != target[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    return i;
                }
            }
            return -1;
        }

        private void send(
                HttpExchange exchange,
                int status,
                String body
        )
                throws IOException {
            byte[] bytes = body.getBytes(
                    StandardCharsets.UTF_8
            );
            exchange.sendResponseHeaders(
                    status,
                    bytes.length
            );
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(
                        bytes
                );
            }
        }

        private void sendJson(
                HttpExchange exchange,
                int status,
                String json
        )
                throws IOException {
            exchange.getResponseHeaders()
                    .set(
                            "Content-Type",
                            "application/json"
                    );
            send(
                    exchange,
                    status,
                    json
            );
        }

        private void send(
                HttpExchange exchange,
                int status,
                byte[] body
        )
                throws IOException {
            exchange.sendResponseHeaders(
                    status,
                    body.length
            );
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(
                        body
                );
            }
        }
    }

    private static class ManifestEntry {

        final byte[] content;

        final String mediaType;

        ManifestEntry(
                byte[] content,
                String mediaType
        ) {
            this.content = content;
            this.mediaType = mediaType;
        }
    }
}
