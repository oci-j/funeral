package io.oci.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine;

@CommandLine.Command(
        name = "mirror",
        description = "Mirror images or Helm charts",
        subcommands = {
                MirrorCommand.ImageCommand.class, MirrorCommand.HelmCommand.class
        }
)
public class MirrorCommand {

    @CommandLine.Command(
            name = "image",
            description = "Mirror a Docker/OCI image"
    )
    public static class ImageCommand implements Callable<Integer> {

        @CommandLine.Parameters(
                index = "0",
                description = "Source image reference"
        )
        String sourceImage;

        @CommandLine.Option(
                names = {
                        "--to"
                },
                description = "Target repository name"
        )
        String targetRepository;

        @CommandLine.Option(
                names = {
                        "--target-tag"
                },
                description = "Target tag"
        )
        String targetTag;

        @CommandLine.Option(
                names = {
                        "--username"
                },
                description = "Source registry username"
        )
        String username;

        @CommandLine.Option(
                names = {
                        "--password"
                },
                description = "Source registry password",
                interactive = true
        )
        String password;

        @CommandLine.Parameters(
                index = "1",
                arity = "0..1",
                description = "Funeral registry host:port"
        )
        String registry;

        @Override
        public Integer call() throws Exception {
            io.oci.cli.client.FuneralClient client = CliHelper.createClient(
                    registry
            );
            String result = client.mirrorImage(
                    sourceImage,
                    targetRepository,
                    targetTag,
                    username,
                    password
            );
            System.out.println(
                    result
            );
            return 0;
        }
    }

    @CommandLine.Command(
            name = "helm",
            description = "Mirror a Helm chart"
    )
    public static class HelmCommand implements Callable<Integer> {

        @CommandLine.Parameters(
                index = "0",
                description = "Source repository URL or name"
        )
        String sourceRepo;

        @CommandLine.Parameters(
                index = "1",
                description = "Chart name"
        )
        String chartName;

        @CommandLine.Option(
                names = {
                        "--version"
                },
                description = "Chart version"
        )
        String version;

        @CommandLine.Option(
                names = {
                        "--to"
                },
                description = "Target repository name"
        )
        String targetRepository;

        @CommandLine.Option(
                names = {
                        "--target-version"
                },
                description = "Target version"
        )
        String targetVersion;

        @CommandLine.Option(
                names = {
                        "--username"
                },
                description = "Source repository username"
        )
        String username;

        @CommandLine.Option(
                names = {
                        "--password"
                },
                description = "Source repository password",
                interactive = true
        )
        String password;

        @CommandLine.Option(
                names = {
                        "--format"
                },
                description = "Source format: oci or chartmuseum",
                defaultValue = "oci"
        )
        String format;

        @CommandLine.Parameters(
                index = "2",
                arity = "0..1",
                description = "Funeral registry host:port"
        )
        String registry;

        @Override
        public Integer call() throws Exception {
            io.oci.cli.client.FuneralClient client = CliHelper.createClient(
                    registry
            );
            String result = client.mirrorHelm(
                    sourceRepo,
                    chartName,
                    version,
                    targetRepository,
                    targetVersion,
                    username,
                    password,
                    format
            );
            System.out.println(
                    result
            );
            return 0;
        }
    }
}
