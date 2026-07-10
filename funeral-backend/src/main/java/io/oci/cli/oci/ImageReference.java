package io.oci.cli.oci;

import java.util.Locale;

public class ImageReference {

    public static final String DEFAULT_REGISTRY = "docker.io";

    public final String registry;

    public final String repository;

    public final String tag;

    public final String digest;

    public ImageReference(
            String registry,
            String repository,
            String tag,
            String digest
    ) {
        this.registry = registry;
        this.repository = repository;
        this.tag = tag;
        this.digest = digest;
    }

    public String reference() {
        return digest != null ? digest : tag;
    }

    public boolean isTagged() {
        return tag != null;
    }

    public boolean isDigested() {
        return digest != null;
    }

    public String fullName() {
        return registry + "/" + repository;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(
                registry
        )
                .append(
                        "/"
                )
                .append(
                        repository
                );
        if (digest != null) {
            sb.append(
                    "@"
            )
                    .append(
                            digest
                    );
        }
        else if (tag != null) {
            sb.append(
                    ":"
            )
                    .append(
                            tag
                    );
        }
        return sb.toString();
    }

    public static ImageReference parse(
            String ref
    ) {
        if (ref == null || ref.isEmpty()) {
            throw new IllegalArgumentException(
                    "image reference is empty"
            );
        }

        String imageSpec = ref;
        String digest = null;
        int at = ref.lastIndexOf(
                '@'
        );
        if (at != -1) {
            imageSpec = ref.substring(
                    0,
                    at
            );
            digest = ref.substring(
                    at + 1
            );
        }

        String tag = null;
        int slash = imageSpec.lastIndexOf(
                '/'
        );
        int colon = imageSpec.lastIndexOf(
                ':'
        );
        if (colon > slash) {
            tag = imageSpec.substring(
                    colon + 1
            );
            imageSpec = imageSpec.substring(
                    0,
                    colon
            );
        }

        String registry;
        String repository;
        int firstSlash = imageSpec.indexOf(
                '/'
        );
        if (firstSlash == -1) {
            registry = DEFAULT_REGISTRY;
            repository = imageSpec;
        }
        else {
            String first = imageSpec.substring(
                    0,
                    firstSlash
            );
            if (isRegistryHost(
                    first
            )) {
                registry = first;
                repository = imageSpec.substring(
                        firstSlash + 1
                );
            }
            else {
                registry = DEFAULT_REGISTRY;
                repository = imageSpec;
            }
        }

        if (repository.isEmpty()) {
            throw new IllegalArgumentException(
                    "repository name is empty: " + ref
            );
        }

        if (DEFAULT_REGISTRY.equals(
                registry
        ) && !repository.contains(
                "/"
        )) {
            repository = "library/" + repository;
        }

        repository = repository.replaceAll(
                "^/+|/+$",
                ""
        );

        if (tag == null && digest == null) {
            tag = "latest";
        }

        return new ImageReference(
                registry,
                repository,
                tag,
                digest
        );
    }

    private static boolean isRegistryHost(
            String s
    ) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        String lower = s.toLowerCase(
                Locale.ROOT
        );
        if ("localhost".equals(
                lower
        )) {
            return true;
        }
        if (s.contains(
                "."
        ) || s.contains(
                ":"
        )) {
            return true;
        }
        return false;
    }
}
