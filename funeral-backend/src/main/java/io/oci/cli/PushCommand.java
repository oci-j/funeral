package io.oci.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.oci.cli.client.FuneralClient;
import io.oci.cli.oci.ImageReference;
import io.oci.cli.oci.OciPushPull;
import picocli.CommandLine;

@CommandLine.Command(
        name = "push",
        description = "Push an image to a Funeral registry"
)
public class PushCommand implements Callable<Integer> {

    @CommandLine.Parameters(
            index = "0",
            description = "Image reference <name>:<tag>"
    )
    String imageRef;

    @CommandLine.Option(
            names = {
                    "--from"
            },
            description = "OCI layout directory"
    )
    Path layoutDir;

    @CommandLine.Option(
            names = {
                    "--tar"
            },
            description = "Docker tar file"
    )
    Path tarFile;

    @CommandLine.Parameters(
            index = "1",
            arity = "0..1",
            description = "Registry host:port"
    )
    String registry;

    @Override
    public Integer call() throws Exception {
        if (tarFile != null) {
            return pushTar();
        }
        return pushOci();
    }

    private Integer pushOci() throws Exception {
        if (layoutDir == null) {
            throw new RuntimeException(
                    "OCI push requires --from <layout-dir>"
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
        oci.push(
                ref.name,
                ref.tag,
                layoutDir
        );
        return 0;
    }

    private Integer pushTar() throws Exception {
        FuneralClient client = CliHelper.createClient(
                registry
        );
        String result = client.uploadDockerTar(
                tarFile
        );
        System.out.println(
                result
        );
        return 0;
    }
}
