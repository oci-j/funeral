package io.oci.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine;

@CommandLine.Command(
        name = "version",
        description = "Show version information"
)
public class VersionCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println(
                "funeral 0.2.0"
        );
        return 0;
    }
}
