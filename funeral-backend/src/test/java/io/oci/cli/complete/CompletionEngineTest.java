package io.oci.cli.complete;

import java.util.List;

import io.oci.cli.FuneralCommand;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompletionEngineTest {

    private static final CompletionEngine.CandidateSource FAKE_SOURCE = new CompletionEngine.CandidateSource() {

        @Override
        public List<String> hosts() {
            return List.of(
                    "localhost:8911",
                    "prod.example.com:5000"
            );
        }

        @Override
        public List<String> repositories(
                String host
        ) {
            return List.of(
                    "library/alpine",
                    "myrepo"
            );
        }

        @Override
        public List<String> tags(
                String host,
                String repository
        ) {
            return "myrepo".equals(
                    repository
            )
                    ? List.of(
                            "latest",
                            "v1"
                    )
                    : List.of();
        }

        @Override
        public List<String> users(
                String host
        ) {
            return List.of(
                    "admin",
                    "alice"
            );
        }
    };

    private static final CompletionEngine.CandidateSource FAILING_SOURCE = new CompletionEngine.CandidateSource() {

        @Override
        public List<String> hosts() {
            throw new RuntimeException(
                    "boom"
            );
        }

        @Override
        public List<String> repositories(
                String host
        ) {
            throw new RuntimeException(
                    "boom"
            );
        }

        @Override
        public List<String> tags(
                String host,
                String repository
        ) {
            throw new RuntimeException(
                    "boom"
            );
        }

        @Override
        public List<String> users(
                String host
        ) {
            throw new RuntimeException(
                    "boom"
            );
        }
    };

    private final CompletionEngine engine = new CompletionEngine(
            new CommandLine(
                    new FuneralCommand()
            ).getCommandSpec(),
            FAKE_SOURCE
    );

    @Test
    public void topLevelSubcommands() {
        List<String> result = engine.complete(
                List.of(
                        ""
                )
        );
        assertTrue(
                result.contains(
                        "serve"
                )
        );
        assertTrue(
                result.contains(
                        "login"
                )
        );
        assertTrue(
                result.contains(
                        "repo"
                )
        );
        assertTrue(
                result.contains(
                        "admin"
                )
        );
        assertTrue(
                result.contains(
                        "generate-completion"
                )
        );
        assertFalse(
                result.contains(
                        "__complete"
                )
        );
    }

    @Test
    public void topLevelPrefixFilter() {
        assertEquals(
                List.of(
                        "repo"
                ),
                engine.complete(
                        List.of(
                                "re"
                        )
                )
        );
    }

    @Test
    public void nestedSubcommands() {
        assertEquals(
                List.of(
                        "list",
                        "rm"
                ),
                engine.complete(
                        List.of(
                                "repo",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(
                        "permission",
                        "user"
                ),
                engine.complete(
                        List.of(
                                "admin",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(
                        "create",
                        "delete",
                        "list",
                        "update"
                ),
                engine.complete(
                        List.of(
                                "admin",
                                "user",
                                ""
                        )
                )
        );
    }

    @Test
    public void optionNames() {
        List<String> result = engine.complete(
                List.of(
                        "mirror",
                        "helm",
                        "--"
                )
        );
        assertTrue(
                result.contains(
                        "--format"
                )
        );
        assertTrue(
                result.contains(
                        "--to"
                )
        );
        assertTrue(
                result.contains(
                        "--target-version"
                )
        );
        assertTrue(
                result.contains(
                        "--username"
                )
        );
        assertTrue(
                result.contains(
                        "--password"
                )
        );
        assertEquals(
                List.of(
                        "--format"
                ),
                engine.complete(
                        List.of(
                                "mirror",
                                "helm",
                                "--fo"
                        )
                )
        );
    }

    @Test
    public void rootOptionNames() {
        List<String> result = engine.complete(
                List.of(
                        "-"
                )
        );
        assertTrue(
                result.contains(
                        "--help"
                )
        );
        assertTrue(
                result.contains(
                        "--version"
                )
        );
    }

    @Test
    public void optionValues() {
        assertEquals(
                List.of(
                        "chartmuseum",
                        "oci"
                ),
                engine.complete(
                        List.of(
                                "mirror",
                                "helm",
                                "--format",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(
                        "oci"
                ),
                engine.complete(
                        List.of(
                                "mirror",
                                "helm",
                                "--format",
                                "o"
                        )
                )
        );
        assertEquals(
                List.of(
                        "--format=oci"
                ),
                engine.complete(
                        List.of(
                                "mirror",
                                "helm",
                                "--format=o"
                        )
                )
        );
        assertEquals(
                List.of(
                        "docker",
                        "local",
                        "oci"
                ),
                engine.complete(
                        List.of(
                                "import",
                                "-t",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(
                        "docker",
                        "local",
                        "oci"
                ),
                engine.complete(
                        List.of(
                                "export",
                                "--from",
                                ""
                        )
                )
        );
    }

    @Test
    public void optionValueIsConsumed() {
        // "oci" is the value of --format, so completing "" afterwards must not
        // repeat the format candidates (positional index 0 of mirror helm has none)
        assertEquals(
                List.of(),
                engine.complete(
                        List.of(
                                "mirror",
                                "helm",
                                "--format",
                                "oci",
                                ""
                        )
                )
        );
    }

    @Test
    public void dynamicRepositories() {
        assertEquals(
                List.of(
                        "library/alpine",
                        "myrepo"
                ),
                engine.complete(
                        List.of(
                                "repo",
                                "rm",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(
                        "myrepo"
                ),
                engine.complete(
                        List.of(
                                "repo",
                                "rm",
                                "my"
                        )
                )
        );
    }

    @Test
    public void dynamicTagsUseRepositoryContext() {
        assertEquals(
                List.of(
                        "latest",
                        "v1"
                ),
                engine.complete(
                        List.of(
                                "tag",
                                "rm",
                                "myrepo",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(
                        "v1"
                ),
                engine.complete(
                        List.of(
                                "tag",
                                "rm",
                                "myrepo",
                                "v"
                        )
                )
        );
    }

    @Test
    public void dynamicHosts() {
        assertEquals(
                List.of(
                        "localhost:8911",
                        "prod.example.com:5000"
                ),
                engine.complete(
                        List.of(
                                "login",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(
                        "localhost:8911"
                ),
                engine.complete(
                        List.of(
                                "login",
                                "loc"
                        )
                )
        );
        // host positional after repository
        assertEquals(
                List.of(
                        "localhost:8911",
                        "prod.example.com:5000"
                ),
                engine.complete(
                        List.of(
                                "repo",
                                "rm",
                                "myrepo",
                                ""
                        )
                )
        );
        // mirror image: host is positional index 1
        assertEquals(
                List.of(
                        "localhost:8911",
                        "prod.example.com:5000"
                ),
                engine.complete(
                        List.of(
                                "mirror",
                                "image",
                                "alpine:latest",
                                ""
                        )
                )
        );
    }

    @Test
    public void dynamicUsers() {
        assertEquals(
                List.of(
                        "admin",
                        "alice"
                ),
                engine.complete(
                        List.of(
                                "admin",
                                "user",
                                "delete",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(
                        "library/alpine",
                        "myrepo"
                ),
                engine.complete(
                        List.of(
                                "admin",
                                "permission",
                                "set",
                                "alice",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(
                        "localhost:8911",
                        "prod.example.com:5000"
                ),
                engine.complete(
                        List.of(
                                "admin",
                                "permission",
                                "set",
                                "alice",
                                "myrepo",
                                ""
                        )
                )
        );
    }

    @Test
    public void failingSourceYieldsEmpty() {
        CompletionEngine failing = new CompletionEngine(
                new CommandLine(
                        new FuneralCommand()
                ).getCommandSpec(),
                FAILING_SOURCE
        );
        assertEquals(
                List.of(),
                failing.complete(
                        List.of(
                                "repo",
                                "rm",
                                ""
                        )
                )
        );
        assertEquals(
                List.of(),
                failing.complete(
                        List.of(
                                "login",
                                ""
                        )
                )
        );
        // static completion still works with a failing source
        assertEquals(
                List.of(
                        "list",
                        "rm"
                ),
                failing.complete(
                        List.of(
                                "repo",
                                ""
                        )
                )
        );
    }
}
