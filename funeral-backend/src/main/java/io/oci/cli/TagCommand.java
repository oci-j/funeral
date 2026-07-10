package io.oci.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "tag",
        description = "Manage tags",
        subcommands = {
                TagCommand.ListCommand.class, TagCommand.RmCommand.class
        }
)
public class TagCommand {

    @CommandLine.Command(
            name = "list",
            description = "List tags for a repository"
    )
    public static class ListCommand implements java.util.concurrent.Callable<Integer> {

        @CommandLine.Parameters(
                index = "0",
                description = "Repository name"
        )
        String repository;

        @CommandLine.Parameters(
                index = "1",
                arity = "0..1",
                description = "Registry host:port"
        )
        String registry;

        @Override
        public Integer call() throws Exception {
            io.oci.cli.client.FuneralClient client = CliHelper.createClient(
                    registry
            );
            java.util.List<String> tags = client.listTags(
                    repository
            );
            if (tags.isEmpty()) {
                System.out.println(
                        "No tags found"
                );
                return 0;
            }
            for (String tag : tags) {
                System.out.println(
                        tag
                );
            }
            return 0;
        }
    }

    @CommandLine.Command(
            name = "rm",
            description = "Delete a tag (manifest)"
    )
    public static class RmCommand implements java.util.concurrent.Callable<Integer> {

        @CommandLine.Parameters(
                index = "0",
                description = "Repository name"
        )
        String repository;

        @CommandLine.Parameters(
                index = "1",
                description = "Tag or digest"
        )
        String reference;

        @CommandLine.Parameters(
                index = "2",
                arity = "0..1",
                description = "Registry host:port"
        )
        String registry;

        @Override
        public Integer call() throws Exception {
            io.oci.cli.client.FuneralClient client = CliHelper.createClient(
                    registry
            );
            client.deleteManifest(
                    repository,
                    reference
            );
            System.out.println(
                    "Deleted " + repository + ":" + reference
            );
            return 0;
        }
    }
}
