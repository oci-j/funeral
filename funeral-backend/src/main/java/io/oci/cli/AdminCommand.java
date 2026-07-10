package io.oci.cli;

import java.util.List;
import java.util.concurrent.Callable;

import io.oci.dto.UserRequest;
import io.oci.dto.UserResponse;
import io.oci.model.RepositoryPermission;
import picocli.CommandLine;

@CommandLine.Command(
        name = "admin",
        description = "Administrative commands",
        subcommands = {
                AdminCommand.UserCommand.class, AdminCommand.PermissionCommand.class
        }
)
public class AdminCommand {

    @CommandLine.Command(
            name = "user",
            description = "Manage users",
            subcommands = {
                    UserCommand.ListCommand.class,
                    UserCommand.CreateCommand.class,
                    UserCommand.UpdateCommand.class,
                    UserCommand.DeleteCommand.class
            }
    )
    public static class UserCommand {

        @CommandLine.Command(
                name = "list",
                description = "List users"
        )
        public static class ListCommand implements Callable<Integer> {

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
                List<UserResponse> users = client.listUsers();
                if (users.isEmpty()) {
                    System.out.println(
                            "No users found"
                    );
                    return 0;
                }
                System.out.printf(
                        "%-20s %-30s %-10s %s%n",
                        "USERNAME",
                        "EMAIL",
                        "ENABLED",
                        "ROLES"
                );
                for (UserResponse u : users) {
                    System.out.printf(
                            "%-20s %-30s %-10s %s%n",
                            u.username,
                            u.email,
                            u.enabled,
                            u.roles
                    );
                }
                return 0;
            }
        }

        @CommandLine.Command(
                name = "create",
                description = "Create a user"
        )
        public static class CreateCommand implements Callable<Integer> {

            @CommandLine.Parameters(
                    index = "0",
                    description = "Username"
            )
            String username;

            @CommandLine.Option(
                    names = {
                            "--password"
                    },
                    description = "Password",
                    interactive = true
            )
            String password;

            @CommandLine.Option(
                    names = {
                            "--email"
                    },
                    description = "Email"
            )
            String email;

            @CommandLine.Option(
                    names = {
                            "--role"
                    },
                    description = "Role (repeatable)"
            )
            List<String> roles;

            @CommandLine.Option(
                    names = {
                            "--repo"
                    },
                    description = "Allowed repository (repeatable)"
            )
            List<String> allowedRepositories;

            @CommandLine.Option(
                    names = {
                            "--enabled"
                    },
                    description = "Enable user"
            )
            boolean enabled = true;

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
                UserRequest request = new UserRequest();
                request.username = username;
                request.password = promptIfNeeded(
                        password,
                        "Password"
                );
                request.email = email;
                request.roles = roles;
                request.allowedRepositories = allowedRepositories;
                request.enabled = enabled;
                UserResponse created = client.createUser(
                        request
                );
                System.out.println(
                        "Created user " + created.username
                );
                return 0;
            }
        }

        @CommandLine.Command(
                name = "update",
                description = "Update a user"
        )
        public static class UpdateCommand implements Callable<Integer> {

            @CommandLine.Parameters(
                    index = "0",
                    description = "Username"
            )
            String username;

            @CommandLine.Option(
                    names = {
                            "--password"
                    },
                    description = "Password",
                    interactive = true
            )
            String password;

            @CommandLine.Option(
                    names = {
                            "--email"
                    },
                    description = "Email"
            )
            String email;

            @CommandLine.Option(
                    names = {
                            "--role"
                    },
                    description = "Role (repeatable)"
            )
            List<String> roles;

            @CommandLine.Option(
                    names = {
                            "--repo"
                    },
                    description = "Allowed repository (repeatable)"
            )
            List<String> allowedRepositories;

            @CommandLine.Option(
                    names = {
                            "--enabled"
                    },
                    description = "Enable user",
                    negatable = true
            )
            Boolean enabled;

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
                UserRequest request = new UserRequest();
                request.password = password;
                request.email = email;
                request.roles = roles;
                request.allowedRepositories = allowedRepositories;
                request.enabled = enabled;
                UserResponse updated = client.updateUser(
                        username,
                        request
                );
                System.out.println(
                        "Updated user " + updated.username
                );
                return 0;
            }
        }

        @CommandLine.Command(
                name = "delete",
                description = "Delete a user"
        )
        public static class DeleteCommand implements Callable<Integer> {

            @CommandLine.Parameters(
                    index = "0",
                    description = "Username"
            )
            String username;

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
                client.deleteUser(
                        username
                );
                System.out.println(
                        "Deleted user " + username
                );
                return 0;
            }
        }
    }

    @CommandLine.Command(
            name = "permission",
            description = "Manage permissions",
            subcommands = {
                    PermissionCommand.ListCommand.class,
                    PermissionCommand.SetCommand.class,
                    PermissionCommand.DeleteCommand.class
            }
    )
    public static class PermissionCommand {

        @CommandLine.Command(
                name = "list",
                description = "List permissions"
        )
        public static class ListCommand implements Callable<Integer> {

            @CommandLine.Parameters(
                    index = "0",
                    arity = "0..1",
                    description = "Username (optional)"
            )
            String username;

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
                List<RepositoryPermission> permissions = username == null
                        ? client.listPermissions()
                        : client.listPermissions(
                                username
                        );
                if (permissions.isEmpty()) {
                    System.out.println(
                            "No permissions found"
                    );
                    return 0;
                }
                System.out.printf(
                        "%-20s %-40s %-6s %-6s%n",
                        "USER",
                        "REPOSITORY",
                        "PULL",
                        "PUSH"
                );
                for (RepositoryPermission p : permissions) {
                    System.out.printf(
                            "%-20s %-40s %-6s %-6s%n",
                            p.username,
                            p.repositoryName,
                            p.canPull,
                            p.canPush
                    );
                }
                return 0;
            }
        }

        @CommandLine.Command(
                name = "set",
                description = "Set permission for a user on a repository"
        )
        public static class SetCommand implements Callable<Integer> {

            @CommandLine.Parameters(
                    index = "0",
                    description = "Username"
            )
            String username;

            @CommandLine.Parameters(
                    index = "1",
                    description = "Repository name"
            )
            String repository;

            @CommandLine.Option(
                    names = {
                            "--pull"
                    },
                    description = "Allow pull"
            )
            boolean pull;

            @CommandLine.Option(
                    names = {
                            "--push"
                    },
                    description = "Allow push"
            )
            boolean push;

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
                RepositoryPermission request = new RepositoryPermission();
                request.username = username;
                request.repositoryName = repository;
                request.canPull = pull;
                request.canPush = push;
                client.setPermission(
                        username,
                        repository,
                        request
                );
                System.out.println(
                        "Set permission for " + username + " on " + repository
                );
                return 0;
            }
        }

        @CommandLine.Command(
                name = "delete",
                description = "Delete permission for a user on a repository"
        )
        public static class DeleteCommand implements Callable<Integer> {

            @CommandLine.Parameters(
                    index = "0",
                    description = "Username"
            )
            String username;

            @CommandLine.Parameters(
                    index = "1",
                    description = "Repository name"
            )
            String repository;

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
                client.deletePermission(
                        username,
                        repository
                );
                System.out.println(
                        "Deleted permission for " + username + " on " + repository
                );
                return 0;
            }
        }
    }

    private static String promptIfNeeded(
            String value,
            String prompt
    ) {
        if (value != null) {
            return value;
        }
        java.io.Console console = System.console();
        if (console == null) {
            throw new RuntimeException(
                    prompt + " required"
            );
        }
        char[] pass = console.readPassword(
                prompt + ": "
        );
        return pass != null
                ? new String(
                        pass
                )
                : null;
    }
}
