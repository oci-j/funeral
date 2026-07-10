package io.oci.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
public class FuneralCliTest {

    private Path tempConfigDir;

    @BeforeEach
    public void setUp() throws IOException {
        tempConfigDir = Files.createTempDirectory(
                "funeral-cli-test"
        );
        System.setProperty(
                "funeral.config.dir",
                tempConfigDir.toString()
        );
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty(
                "funeral.config.dir"
        );
    }

    @Test
    public void testNoArgsShowsHelp(
            QuarkusMainLauncher launcher
    ) {
        LaunchResult result = launcher.launch();
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "Usage:"
                        )
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "Funeral OCI Registry CLI"
                        )
        );
    }

    @Test
    @Launch(
        "--help"
    )
    public void testHelp(
            LaunchResult result
    ) {
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "Usage:"
                        )
        );
    }

    @Test
    @Launch(
        "--version"
    )
    public void testVersion(
            LaunchResult result
    ) {
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "funeral 0.2.0"
                        )
        );
    }

    @Test
    @Launch(
        {
                "logout", "localhost:8911"
    }
    )
    public void testLogout(
            LaunchResult result
    ) {
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "Logged out from localhost:8911"
                        )
        );
    }
}
