package io.oci.service.handler;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommentPath(
    "/v2/token"
)
@ApplicationScoped
//@Produces(MediaType.APPLICATION_JSON)
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
            HttpHeaders headers
    ) {
        return handleTokenRequest(
                service,
                scope,
                account,
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
}
