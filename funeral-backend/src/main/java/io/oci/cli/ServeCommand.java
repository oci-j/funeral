package io.oci.cli;

import java.util.concurrent.Callable;

import io.quarkus.runtime.Quarkus;
import picocli.CommandLine;

@CommandLine.Command(
        name = "serve",
        description = "Start the Funeral OCI Registry server"
)
public class ServeCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        Quarkus.waitForExit();
        return 0;
    }
}
