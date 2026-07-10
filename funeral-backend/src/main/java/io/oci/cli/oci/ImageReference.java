package io.oci.cli.oci;

public class ImageReference {

    public String name;

    public String tag;

    public static ImageReference parse(
            String ref
    ) {
        ImageReference result = new ImageReference();
        int at = ref.lastIndexOf(
                "@"
        );
        if (at != -1) {
            result.name = ref.substring(
                    0,
                    at
            );
            result.tag = ref.substring(
                    at + 1
            );
            return result;
        }
        int slash = ref.lastIndexOf(
                "/"
        );
        int colon = ref.lastIndexOf(
                ":"
        );
        if (colon > slash) {
            result.name = ref.substring(
                    0,
                    colon
            );
            result.tag = ref.substring(
                    colon + 1
            );
        }
        else {
            result.name = ref;
            result.tag = "latest";
        }
        return result;
    }
}
