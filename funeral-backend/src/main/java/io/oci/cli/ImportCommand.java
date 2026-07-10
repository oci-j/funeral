package io.oci.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine;

@CommandLine.Command(
        name = "import",
        description = "Import an image from a registry to local storage / Docker / OCI layout"
)
public class ImportCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        throw new UnsupportedOperationException(
                "import command not yet implemented"
        );
    }
}
