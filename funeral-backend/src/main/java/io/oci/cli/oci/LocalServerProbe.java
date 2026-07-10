package io.oci.cli.oci;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class LocalServerProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(
            2
    );

    private final HttpClient httpClient;

    public LocalServerProbe() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        TIMEOUT
                )
                .build();
    }

    public boolean isReachable(
            String baseUrl
    ) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return false;
        }
        String normalized = baseUrl;
        if (normalized.endsWith(
                "/"
        )) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1
            );
        }
        String healthUrl = normalized + "/funeral_addition/health/live";
        try {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(
                            healthUrl
                    )
            )
                    .timeout(
                            TIMEOUT
                    )
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() < 400;
        }
        catch (Exception e) {
            return false;
        }
    }
}
