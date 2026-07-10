package io.oci.cli;

import java.util.concurrent.Callable;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(
        name = "funeral",
        mixinStandardHelpOptions = true,
        version = "funeral 0.2.0",
        subcommands = {
                ServeCommand.class,
                LoginCommand.class,
                LogoutCommand.class,
                RepoCommand.class,
                TagCommand.class,
                MirrorCommand.class,
                AdminCommand.class,
                PushCommand.class,
                PullCommand.class,
                VersionCommand.class,
                HealthCommand.class
        },
        description = "Funeral OCI Registry CLI"
)
public class FuneralCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        spec.commandLine()
                .usage(
                        System.out
                );
        return 0;
    }
}
