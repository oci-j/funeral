package io.oci.cli.complete;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class StaticCandidates {

    private StaticCandidates() {
    }

    public static class OutputType implements Iterable<String> {

        private static final List<String> VALUES = Arrays.asList(
                "local",
                "docker",
                "oci"
        );

        @Override
        public Iterator<String> iterator() {
            return VALUES.iterator();
        }
    }

    public static class Format implements Iterable<String> {

        private static final List<String> VALUES = Arrays.asList(
                "oci",
                "chartmuseum"
        );

        @Override
        public Iterator<String> iterator() {
            return VALUES.iterator();
        }
    }
}
