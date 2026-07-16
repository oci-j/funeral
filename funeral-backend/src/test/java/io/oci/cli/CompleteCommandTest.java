package io.oci.cli;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
public class CompleteCommandTest {

    @Test
    @Launch(
        {
                "__complete", "--", ""
    }
    )
    public void testTopLevelCandidates(
            LaunchResult result
    ) {
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "serve"
                        )
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "repo"
                        )
        );
        assertFalse(
                result.getOutput()
                        .contains(
                                "__complete"
                        )
        );
    }

    @Test
    @Launch(
        {
                "__complete", "--", "repo", ""
    }
    )
    public void testNestedSubcommands(
            LaunchResult result
    ) {
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "list"
                        )
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "rm"
                        )
        );
    }

    @Test
    @Launch(
        {
                "__complete", "--", "mirror", "helm", "--format", ""
    }
    )
    public void testOptionValues(
            LaunchResult result
    ) {
        assertEquals(
                0,
                result.exitCode()
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "oci"
                        )
        );
        assertTrue(
                result.getOutput()
                        .contains(
                                "chartmuseum"
                        )
        );
    }

    @Test
    @Launch(
        {
                "__complete", "--", "repo", "rm", ""
    }
    )
    public void testDynamicFailureIsSilent(
            LaunchResult result
    ) {
        // no registry reachable in the test environment: empty output, exit 0
        assertEquals(
                0,
                result.exitCode()
        );
    }
}
