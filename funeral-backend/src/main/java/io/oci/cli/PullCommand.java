package io.oci.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.oci.cli.client.FuneralClient;
import io.oci.cli.oci.ImageReference;
import io.oci.cli.oci.OciPushPull;
import picocli.CommandLine;

@CommandLine.Command(
        name = "pull",
        description = "Pull an image from a Funeral registry"
)
public class PullCommand implements Callable<Integer> {

    @CommandLine.Parameters(
            index = "0",
            description = "Image reference <name>:<tag>"
    )
    String imageRef;

    @CommandLine.Option(
            names = {
                    "--to"
            },
            description = "OCI layout output directory"
    )
    Path layoutDir;

    @CommandLine.Parameters(
            index = "1",
            arity = "0..1",
            description = "Registry host:port"
    )
    String registry;

    @Override
    public Integer call() throws Exception {
        if (layoutDir == null) {
            throw new RuntimeException(
                    "OCI pull requires --to <layout-dir>"
            );
        }
        ImageReference ref = ImageReference.parse(
                imageRef
        );
        FuneralClient client = CliHelper.createClient(
                registry
        );
        OciPushPull oci = new OciPushPull(
                client
        );
        oci.pull(
                ref.name,
                ref.tag,
                layoutDir
        );
        return 0;
    }
}
