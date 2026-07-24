package io.oci.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.oci.cli.oci.MockRegistryServer;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
public class CliCommandsIntegrationTest {

    private Path tempConfigDir;

    private MockRegistryServer server;

    @BeforeEach
    public void setUp() throws IOException {
        tempConfigDir = Files.createTempDirectory(
                "funeral-cli-test"
        );
        System.setProperty(
                "funeral.config.dir",
                tempConfigDir.toString()
        );
        System.setProperty(
                "funeral.cli.disableKeyring",
                "true"
        );

        server = new MockRegistryServer();
        server.start();
        server.requireBasicAuth(
                "user",
                "pass"
        );
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty(
                "funeral.config.dir"
        );
        System.clearProperty(
                "funeral.cli.disableKeyring"
        );
        if (server != null) {
            server.stop();
        }
    }

    private String registryArg() {
        return server.baseUrl()
                .replace(
                        "http://",
                        ""
                );
    }

    @Test
    public void testHealth(
            QuarkusMainLauncher launcher
    ) {
        LaunchResult result = launcher.launch(
                "health",
                registryArg()
        );
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "OK:"
                        )
        );
    }

    @Test
    public void testRepoList(
            QuarkusMainLauncher launcher
    ) {
        LaunchResult result = launcher.launch(
                "repo",
                "list",
                registryArg()
        );
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "No repositories found"
                        )
        );
    }

    @Test
    public void testTagList(
            QuarkusMainLauncher launcher
    ) {
        LaunchResult result = launcher.launch(
                "tag",
                "list",
                "test/repo",
                registryArg()
        );
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "No tags found"
                        )
        );
    }

    @Test
    public void testMirrorImage(
            QuarkusMainLauncher launcher
    ) {
        LaunchResult result = launcher.launch(
                "mirror",
                "image",
                "nginx:latest",
                registryArg(),
                "--to",
                "nginx"
        );
        assertEquals(
                0,
                result.exitCode()
        );
    }

    @Test
    public void testAdminUserList(
            QuarkusMainLauncher launcher
    ) {
        LaunchResult result = launcher.launch(
                "admin",
                "user",
                "list",
                registryArg()
        );
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "No users found"
                        )
        );
    }

    @Test
    public void testLogin(
            QuarkusMainLauncher launcher
    )
            throws IOException {
        LaunchResult result = launcher.launch(
                "login",
                registryArg(),
                "-u",
                "user",
                "-p",
                "pass"
        );
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "Login succeeded"
                        )
        );
        Path credentialsFile = tempConfigDir.resolve(
                "credentials.json"
        );
        assertTrue(
                Files.exists(
                        credentialsFile
                ),
                "Credentials file should be created"
        );
        String contents = Files.readString(
                credentialsFile
        );
        assertTrue(
                contents.contains(
                        "user"
                ),
                "Credentials file should contain username"
        );
    }

    @Test
    public void testLoginWithWrongPassword(
            QuarkusMainLauncher launcher
    ) {
        LaunchResult result = launcher.launch(
                "login",
                registryArg(),
                "-u",
                "user",
                "-p",
                "wrong-password"
        );
        assertTrue(
                result.exitCode() != 0
        );
    }

    @Test
    public void testLoginWithoutRegistryAndNoDefault(
            QuarkusMainLauncher launcher
    ) {
        LaunchResult result = launcher.launch(
                "login",
                "-u",
                "user",
                "-p",
                "pass"
        );
        assertTrue(
                result.exitCode() != 0
        );
    }
}
