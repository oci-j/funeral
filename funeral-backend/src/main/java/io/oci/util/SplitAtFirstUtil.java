package io.oci.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SplitAtFirstUtil {

    public record SplitAtFirstResult(
            @NotNull
            String first,
            @NotNull
            String second
    ) {
    }

    @NotNull
    public static SplitAtFirstUtil.SplitAtFirstResult splitAtFirstIndex(
            @NotNull
            String input,
            @Nullable
            String delimiter
    ) {
        if (delimiter == null) {
            return new SplitAtFirstResult(
                    input,
                    ""
            );
        }
        int index = input.indexOf(
                delimiter
        );
        if (index == -1) {
            return new SplitAtFirstResult(
                    input,
                    ""
            );
        }
        String firstPart = input.substring(
                0,
                index
        );
        String secondPart = input.substring(
                index + delimiter.length()
        );
        return new SplitAtFirstResult(
                firstPart,
                secondPart
        );
    }

    @Nullable
    public static SplitAtFirstUtil.SplitAtFirstResult splitAtFirstIndexNullable(
            @Nullable
            String input,
            @Nullable
            String delimiter
    ) {
        if (input == null) {
            return null;
        }
        return splitAtFirstIndex(
                input,
                delimiter
        );
    }

}
