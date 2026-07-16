package io.oci.cli.complete;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

public class CompletionEngine {

    public interface CandidateSource {

        List<String> hosts();

        List<String> repositories(
                String host
        );

        List<String> tags(
                String host,
                String repository
        );

        List<String> users(
                String host
        );
    }

    private final CommandSpec root;

    private final CandidateSource source;

    public CompletionEngine(
            CommandSpec root,
            CandidateSource source
    ) {
        this.root = root;
        this.source = source;
    }

    public List<String> complete(
            List<String> words
    ) {
        String cur = words.isEmpty()
                ? ""
                : words.get(
                        words.size() - 1
                );
        CommandSpec spec = root;
        String path = "";
        int positionalIndex = 0;
        Map<Integer, String> positionals = new HashMap<>();
        OptionSpec pendingOption = null;
        int end = Math.max(
                0,
                words.size() - 1
        );
        for (int i = 0; i < end; i++) {
            String word = words.get(
                    i
            );
            if (pendingOption != null) {
                pendingOption = null;
                continue;
            }
            if (!word.startsWith(
                    "-"
            ) && spec.subcommands()
                    .containsKey(
                            word
                    )) {
                spec = spec.subcommands()
                        .get(
                                word
                        )
                        .getCommandSpec();
                path = path.isEmpty() ? word : path + " " + word;
                positionalIndex = 0;
                positionals = new HashMap<>();
                continue;
            }
            if (word.startsWith(
                    "-"
            )) {
                int eq = word.indexOf(
                        '='
                );
                String name = eq >= 0
                        ? word.substring(
                                0,
                                eq
                        )
                        : word;
                OptionSpec option = findOption(
                        spec,
                        name
                );
                if (option != null && eq < 0 && takesValue(
                        option
                )) {
                    pendingOption = option;
                }
                continue;
            }
            positionals.put(
                    positionalIndex++,
                    word
            );
        }

        if (pendingOption != null && !cur.startsWith(
                "-"
        )) {
            return filter(
                    optionValueCandidates(
                            pendingOption
                    ),
                    cur
            );
        }
        if (cur.startsWith(
                "-"
        )) {
            int eq = cur.indexOf(
                    '='
            );
            if (eq >= 0) {
                String name = cur.substring(
                        0,
                        eq
                );
                String valuePrefix = cur.substring(
                        eq + 1
                );
                OptionSpec option = findOption(
                        spec,
                        name
                );
                if (option == null) {
                    return List.of();
                }
                List<String> result = new ArrayList<>();
                for (String value : filter(
                        optionValueCandidates(
                                option
                        ),
                        valuePrefix
                )) {
                    result.add(
                            name + "=" + value
                    );
                }
                return result;
            }
            TreeSet<String> names = new TreeSet<>();
            for (OptionSpec option : spec.options()) {
                if (option.hidden()) {
                    continue;
                }
                for (String name : option.names()) {
                    if (name.startsWith(
                            cur
                    )) {
                        names.add(
                                name
                        );
                    }
                }
            }
            return List.copyOf(
                    names
            );
        }
        if (!spec.subcommands().isEmpty()) {
            TreeSet<String> names = new TreeSet<>();
            for (picocli.CommandLine subcommand : spec.subcommands().values()) {
                CommandSpec subSpec = subcommand.getCommandSpec();
                if (!subSpec.usageMessage().hidden() && subSpec.name()
                        .startsWith(
                                cur
                        )) {
                    names.add(
                            subSpec.name()
                    );
                }
            }
            return List.copyOf(
                    names
            );
        }
        return filter(
                positionalCandidates(
                        path,
                        positionalIndex,
                        positionals
                ),
                cur
        );
    }

    private List<String> positionalCandidates(
            String path,
            int index,
            Map<Integer, String> positionals
    ) {
        switch (path) {
            case "login":
            case "logout":
            case "health":
            case "repo list":
                return index == 0 ? hosts() : List.of();
            case "repo rm":
            case "tag list":
                if (index == 0) {
                    return repositories();
                }
                return index == 1 ? hosts() : List.of();
            case "tag rm":
                if (index == 0) {
                    return repositories();
                }
                if (index == 1) {
                    return safe(
                            () -> source.tags(
                                    null,
                                    positionals.get(
                                            0
                                    )
                            )
                    );
                }
                return index == 2 ? hosts() : List.of();
            case "mirror image":
                return index == 1 ? hosts() : List.of();
            case "mirror helm":
                return index == 2 ? hosts() : List.of();
            case "admin user list":
                return index == 0 ? hosts() : List.of();
            case "admin user create":
                return index == 1 ? hosts() : List.of();
            case "admin user update":
            case "admin user delete":
                if (index == 0) {
                    return users();
                }
                return index == 1 ? hosts() : List.of();
            case "admin permission list":
                if (index == 0) {
                    return users();
                }
                return index == 1 ? hosts() : List.of();
            case "admin permission set":
            case "admin permission delete":
                if (index == 0) {
                    return users();
                }
                if (index == 1) {
                    return repositories();
                }
                return index == 2 ? hosts() : List.of();
            default:
                return List.of();
        }
    }

    private List<String> hosts() {
        return safe(
                source::hosts
        );
    }

    private List<String> repositories() {
        return safe(
                () -> source.repositories(
                        null
                )
        );
    }

    private List<String> users() {
        return safe(
                () -> source.users(
                        null
                )
        );
    }

    private static List<String> safe(
            Supplier<List<String>> supplier
    ) {
        try {
            List<String> result = supplier.get();
            return result == null ? List.of() : result;
        }
        catch (Throwable t) {
            return List.of();
        }
    }

    private static List<String> optionValueCandidates(
            OptionSpec option
    ) {
        Iterable<String> candidates = option.completionCandidates();
        if (candidates == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        candidates.forEach(
                result::add
        );
        return result;
    }

    private static OptionSpec findOption(
            CommandSpec spec,
            String name
    ) {
        for (OptionSpec option : spec.options()) {
            for (String optionName : option.names()) {
                if (optionName.equals(
                        name
                )) {
                    return option;
                }
            }
        }
        return null;
    }

    private static boolean takesValue(
            OptionSpec option
    ) {
        return option.arity().max() > 0;
    }

    private static List<String> filter(
            List<String> candidates,
            String prefix
    ) {
        TreeSet<String> result = new TreeSet<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.startsWith(
                    prefix
            )) {
                result.add(
                        candidate
                );
            }
        }
        return List.copyOf(
                result
        );
    }
}
