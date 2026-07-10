package io.oci.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "repo",
        description = "Manage repositories",
        subcommands = {
                RepoCommand.ListCommand.class, RepoCommand.RmCommand.class
        }
)
public class RepoCommand {

    @CommandLine.Command(
            name = "list",
            description = "List repositories"
    )
    public static class ListCommand implements java.util.concurrent.Callable<Integer> {

        @CommandLine.Parameters(
                index = "0",
                arity = "0..1",
                description = "Registry host:port"
        )
        String registry;

        @Override
        public Integer call() throws Exception {
            io.oci.cli.client.FuneralClient client = CliHelper.createClient(
                    registry
            );
            java.util.List<io.oci.dto.RepositoryInfo> repos = client.listRepositories();
            if (repos.isEmpty()) {
                System.out.println(
                        "No repositories found"
                );
                return 0;
            }
            System.out.printf(
                    "%-40s %-20s %s%n",
                    "NAME",
                    "TAGS",
                    "UPDATED"
            );
            for (io.oci.dto.RepositoryInfo repo : repos) {
                System.out.printf(
                        "%-40s %-20d %s%n",
                        repo.name,
                        repo.tagCount,
                        repo.updatedAt
                );
            }
            return 0;
        }
    }

    @CommandLine.Command(
            name = "rm",
            description = "Delete a repository"
    )
    public static class RmCommand implements java.util.concurrent.Callable<Integer> {

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
            client.deleteRepository(
                    repository
            );
            System.out.println(
                    "Deleted repository " + repository
            );
            return 0;
        }
    }
}
