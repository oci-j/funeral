package io.oci.service.handler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import io.oci.annotation.CommentGET;
import io.oci.annotation.CommentPOST;
import io.oci.annotation.CommentPath;
import io.oci.annotation.CommentQueryParam;
import io.oci.dto.TokenResponse;
import io.oci.resource.OciV2Resource;
import io.oci.service.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommentPath(
    "/v2/token"
)
@ApplicationScoped
public class TokenResourceHandler {

    private static final Logger log = LoggerFactory.getLogger(
            OciV2Resource.class
    );

    @ConfigProperty(
            name = "oci.auth.allow-anonymous-pull",
            defaultValue = "false"
    )
    boolean allowAnonymousPull;

    @Inject
    AuthService authService;

    @CommentGET
    public Response getToken(
            @CommentQueryParam(
                "service"
            )
            String service,
            @CommentQueryParam(
                "scope"
            )
            String scope,
            @CommentQueryParam(
                "account"
            )
            String account,
            HttpHeaders headers
    ) {
        return handleTokenRequest(
                service,
                scope,
                account,
                headers
        );
    }

    @CommentPOST
    public Response postToken(
            @CommentQueryParam(
                "service"
            )
            String service,
            @CommentQueryParam(
                "scope"
            )
            String scope,
            @CommentQueryParam(
                "account"
            )
            String account,
            HttpHeaders headers,
            InputStream body
    ) {
        String formService = null;
        String formScope = null;
        String formAccount = null;
        String formUsername = null;
        String formPassword = null;
        String grant_type = null;
        String client_id = null;
        try {
            // body format like
            // "client_id=containerd-client&grant_type=password&password=password&scope=repository:test/hellohelm:pull,push&service=funeral-registry&username=admin"
            String bodyString = IOUtils.toString(
                    body,
                    StandardCharsets.UTF_8
            );
            String[] parts = bodyString.split(
                    "&"
            );
            for (String part : parts) {
                String[] keyValue = part.split(
                        "="
                );
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];
                    switch (key) {
                        case "service":
                            formService = value;
                            break;
                        case "scope":
                            formScope = value;
                            break;
                        case "account":
                            formAccount = value;
                            break;
                        case "username":
                            formUsername = value;
                            break;
                        case "password":
                            formPassword = value;
                            break;
                        case "grant_type":
                            grant_type = value;
                            break;
                        case "client_id":
                            client_id = value;
                            break;
                    }
                }
            }
        }
        catch (Exception e) {
            log.warn(
                    "postToken parse body failed",
                    e
            );
        }
        // Use form parameters if provided, otherwise fall back to query parameters
        String finalService = service != null ? service : formService;
        String finalScope = scope != null ? scope : formScope;
        String finalAccount = account != null ? account : formAccount;

        // Handle authentication from form parameters if provided
        if (formUsername != null && formPassword != null) {
            return handleFormAuthTokenRequest(
                    finalService,
                    finalScope,
                    finalAccount,
                    formUsername,
                    formPassword,
                    headers
            );
        }

        return handleTokenRequest(
                finalService,
                finalScope,
                finalAccount,
                headers
        );
    }

    private Response handleTokenRequest(
            String service,
            String scope,
            String account,
            HttpHeaders headers
    ) {
        if (!authService.isAuthEnabled()) {
            return Response.ok(
                    new TokenResponse(
                            "anonymous",
                            "Bearer",
                            3600
                    )
            ).build();
        }

        String authHeader = headers.getHeaderString(
                "Authorization"
        );
        if (authHeader == null || !authHeader.startsWith(
                "Basic "
        )) {
            if (!allowAnonymousPull) {
                return Response.status(
                        Response.Status.UNAUTHORIZED
                )
                        .header(
                                "WWW-Authenticate",
                                "Basic realm=\"funeral-registry\""
                        )
                        .build();
            }
            else {
                TokenResponse tokenResponse = authService.authenticateWithAnonymousUser(
                        scope
                );
                return Response.ok(
                        tokenResponse
                ).build();
            }
        }

        String credentials = authHeader.substring(
                "Basic ".length()
        );
        String decoded;
        try {
            decoded = new String(
                    Base64.getDecoder()
                            .decode(
                                    credentials
                            ),
                    StandardCharsets.UTF_8
            );
        }
        catch (IllegalArgumentException e) {
            return Response.status(
                    Response.Status.UNAUTHORIZED
            ).build();
        }

        String[] parts = decoded.split(
                ":",
                2
        );
        if (parts.length != 2) {
            return Response.status(
                    Response.Status.UNAUTHORIZED
            ).build();
        }

        String username = parts[0];
        String password = parts[1];

        try {
            TokenResponse tokenResponse = authService.authenticate(
                    username,
                    password,
                    service,
                    scope
            );
            if (tokenResponse == null) {
                return Response.status(
                        Response.Status.UNAUTHORIZED
                )
                        .entity(
                                "{\"errors\":[{\"code\":\"UNAUTHORIZED\",\"message\":\"authentication required\"}]}"
                        )
                        .build();
            }
            return Response.ok(
                    tokenResponse
            ).build();
        }
        catch (Exception e) {
            log.error(
                    "handleTokenRequest error",
                    e
            );
            return Response.status(
                    Response.Status.INTERNAL_SERVER_ERROR
            )
                    .entity(
                            "{\"error\":\"" + e.getMessage() + "\"}"
                    )
                    .build();
        }
    }

    private Response handleFormAuthTokenRequest(
            String service,
            String scope,
            String account,
            String username,
            String password,
            HttpHeaders headers
    ) {
        // Create Basic Auth header from form parameters
        String credentials = username + ":" + password;
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(
                        credentials.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
        String authHeader = "Basic " + encodedCredentials;

        // Create a wrapper HttpHeaders that includes the Basic auth header
        HttpHeaders wrappedHeaders = new HttpHeaders() {
            @Override
            public String getHeaderString(
                    String name
            ) {
                if ("Authorization".equalsIgnoreCase(
                        name
                )) {
                    return authHeader;
                }
                return headers.getHeaderString(
                        name
                );
            }

            @Override
            public java.util.List<String> getRequestHeader(
                    String name
            ) {
                if ("Authorization".equalsIgnoreCase(
                        name
                )) {
                    return java.util.List.of(
                            authHeader
                    );
                }
                return headers.getRequestHeader(
                        name
                );
            }

            @Override
            public MultivaluedMap<String, String> getRequestHeaders() {
                MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
                map.putAll(
                        headers.getRequestHeaders()
                );
                map.putSingle(
                        "Authorization",
                        authHeader
                );
                return map;
            }

            @Override
            public int getLength() {
                return headers.getLength();
            }

            @Override
            public MediaType getMediaType() {
                return headers.getMediaType();
            }

            @Override
            public Date getDate() {
                return headers.getDate();
            }

            @Override
            public java.util.Locale getLanguage() {
                return headers.getLanguage();
            }

            @Override
            public java.util.Map<String, jakarta.ws.rs.core.Cookie> getCookies() {
                return headers.getCookies();
            }

            @Override
            public java.util.List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() {
                return headers.getAcceptableMediaTypes();
            }

            @Override
            public java.util.List<java.util.Locale> getAcceptableLanguages() {
                return headers.getAcceptableLanguages();
            }
        };

        return handleTokenRequest(
                service,
                scope,
                account,
                wrappedHeaders
        );
    }
}
