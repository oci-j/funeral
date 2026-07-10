package io.oci.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine;

@CommandLine.Command(
        name = "export",
        description = "Export an image from local storage / Docker / OCI layout to one or more registries"
)
public class ExportCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        throw new UnsupportedOperationException(
                "export command not yet implemented"
        );
    }
}
