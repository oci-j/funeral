package io.oci.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(
    "/funeral_addition/config"
)
@ApplicationScoped
public class ConfigResource {

    private static final Logger log = LoggerFactory.getLogger(
            ConfigResource.class
    );

    @ConfigProperty(
            name = "oci.auth.enabled",
            defaultValue = "true"
    )
    boolean authEnabled;

    @ConfigProperty(
            name = "oci.auth.allow-anonymous-pull",
            defaultValue = "false"
    )
    boolean allowAnonymousPull;

    @ConfigProperty(
            name = "oci.auth.realm",
            defaultValue = "http://localhost:8911/v2/token"
    )
    String authRealm;

    @GET
    @Path(
        "/auth"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getAuthConfig() {
        log.debug(
                "Fetching auth configuration"
        );

        Map<String, Object> config = new HashMap<>();
        config.put(
                "enabled",
                authEnabled
        );
        config.put(
                "allowAnonymousPull",
                allowAnonymousPull
        );
        config.put(
                "realm",
                authRealm
        );

        return Response.ok(
                config
        ).build();
    }

    @GET
    @Path(
        "/all"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getAllConfig() {
        log.debug(
                "Fetching all configuration"
        );

        Map<String, Object> config = new HashMap<>();

        // Auth config
        Map<String, Object> authConfig = new HashMap<>();
        authConfig.put(
                "enabled",
                authEnabled
        );
        authConfig.put(
                "allowAnonymousPull",
                allowAnonymousPull
        );
        authConfig.put(
                "realm",
                authRealm
        );
        config.put(
                "auth",
                authConfig
        );

        return Response.ok(
                config
        ).build();
    }

    @GET
    @Path(
        "/runtime"
    )
    @Produces(
        MediaType.APPLICATION_JSON
    )
    public Response getRuntimeInfo() {
        log.debug(
                "Fetching runtime information"
        );

        Map<String, Object> runtimeInfo = new HashMap<>();

        // Check if running as GraalVM native binary
        boolean isNativeImage = System.getProperty(
                "org.graalvm.nativeimage.imagecode"
        ) != null;
        runtimeInfo.put(
                "isNativeImage",
                isNativeImage
        );

        // Get process ID
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean()
                .getName()
                .split(
                        "@"
                )[0];
        runtimeInfo.put(
                "pid",
                pid
        );

        // Get system info
        runtimeInfo.put(
                "javaVersion",
                System.getProperty(
                        "java.version"
                )
        );
        runtimeInfo.put(
                "javaVendor",
                System.getProperty(
                        "java.vendor"
                )
        );
        runtimeInfo.put(
                "osName",
                System.getProperty(
                        "os.name"
                )
        );
        runtimeInfo.put(
                "osArch",
                System.getProperty(
                        "os.arch"
                )
        );

        // If native image, try to get binary path
        if (isNativeImage) {
            try {
                String processPath = java.nio.file.Paths.get(
                        "/proc/self/exe"
                ).toRealPath().toString();
                File binaryFile = new File(
                        processPath
                );
                if (binaryFile.exists() && binaryFile.isFile()) {
                    runtimeInfo.put(
                            "binaryPath",
                            processPath
                    );
                    runtimeInfo.put(
                            "binaryName",
                            binaryFile.getName()
                    );
                    runtimeInfo.put(
                            "binarySize",
                            binaryFile.length()
                    );
                    runtimeInfo.put(
                            "canDownload",
                            true
                    );
                }
            }
            catch (Exception e) {
                log.warn(
                        "Cannot determine binary path: {}",
                        e.getMessage()
                );
                runtimeInfo.put(
                        "canDownload",
                        false
                );
            }
        }
        else {
            runtimeInfo.put(
                    "canDownload",
                    false
            );
        }

        return Response.ok(
                runtimeInfo
        ).build();
    }

    @GET
    @Path(
        "/download/binary"
    )
    @Produces(
        MediaType.APPLICATION_OCTET_STREAM
    )
    public Response downloadBinary() {
        // Verify this is a native image
        boolean isNativeImage = System.getProperty(
                "org.graalvm.nativeimage.imagecode"
        ) != null;
        if (!isNativeImage) {
            return Response.status(
                    Response.Status.BAD_REQUEST
            )
                    .entity(
                            "Not running as native binary"
                    )
                    .build();
        }

        try {
            String processPath = java.nio.file.Paths.get(
                    "/proc/self/exe"
            ).toRealPath().toString();
            File binaryFile = new File(
                    processPath
            );

            if (!binaryFile.exists() || !binaryFile.isFile()) {
                return Response.status(
                        Response.Status.NOT_FOUND
                )
                        .entity(
                                "Binary file not found"
                        )
                        .build();
            }

            StreamingOutput fileStream = output -> {
                try (
                        InputStream inputStream = new FileInputStream(
                                binaryFile
                        )) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(
                            buffer
                    )) != -1) {
                        output.write(
                                buffer,
                                0,
                                bytesRead
                        );
                    }
                    output.flush();
                }
                catch (IOException e) {
                    log.error(
                            "Error streaming binary file: {}",
                            e.getMessage()
                    );
                    throw e;
                }
            };

            return Response.ok(
                    fileStream
            )
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + binaryFile.getName() + "\""
                    )
                    .header(
                            HttpHeaders.CONTENT_LENGTH,
                            binaryFile.length()
                    )
                    .header(
                            "X-Original-Filename",
                            binaryFile.getName()
                    )
                    .build();

        }
        catch (Exception e) {
            log.error(
                    "Error preparing binary download: {}",
                    e.getMessage()
            );
            return Response.status(
                    Response.Status.INTERNAL_SERVER_ERROR
            )
                    .entity(
                            "Error preparing download: " + e.getMessage()
                    )
                    .build();
        }
    }
}
