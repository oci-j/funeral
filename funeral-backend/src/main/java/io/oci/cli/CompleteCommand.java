package io.oci.cli;

import java.util.List;
import java.util.concurrent.Callable;

import io.oci.cli.complete.CompletionEngine;
import io.oci.cli.complete.DefaultCandidateSource;
import picocli.CommandLine;

@CommandLine.Command(
        name = "__complete",
        hidden = true,
        description = "Print shell completion candidates (internal use)"
)
public class CompleteCommand implements Callable<Integer> {

    @CommandLine.Parameters(
            arity = "0..*",
            description = "Words on the command line; the last word is the prefix being completed"
    )
    List<String> words;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        try {
            CompletionEngine engine = new CompletionEngine(
                    spec.root(),
                    new DefaultCandidateSource()
            );
            for (String candidate : engine.complete(
                    words == null ? List.of() : words
            )) {
                System.out.println(
                        candidate
                );
            }
        }
        catch (Throwable t) {
            // completion must fail silently
        }
        return 0;
    }
}
