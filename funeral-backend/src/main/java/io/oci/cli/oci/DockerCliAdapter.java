package io.oci.cli.oci;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DockerCliAdapter {

    private final int timeoutSeconds;

    public DockerCliAdapter() {
        this(
                300
        );
    }

    public DockerCliAdapter(
            int timeoutSeconds
    ) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker",
                    "version",
                    "--format",
                    "{{.Server.Version}}"
            );
            pb.inheritIO();
            Process process = pb.start();
            boolean finished = process.waitFor(
                    5,
                    TimeUnit.SECONDS
            );
            return finished && process.exitValue() == 0;
        }
        catch (Exception e) {
            return false;
        }
    }

    public boolean imageExists(
            String imageRef
    ) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker",
                    "inspect",
                    "--type",
                    "image",
                    imageRef
            );
            pb.redirectErrorStream(
                    true
            );
            Process process = pb.start();
            boolean finished = process.waitFor(
                    10,
                    TimeUnit.SECONDS
            );
            return finished && process.exitValue() == 0;
        }
        catch (Exception e) {
            return false;
        }
    }

    public void saveImage(
            String imageRef,
            Path tarOut
    ) {
        runDockerCommand(
                Arrays.asList(
                        "docker",
                        "save",
                        imageRef,
                        "-o",
                        tarOut.toString()
                )
        );
    }

    public void loadImage(
            Path tarIn
    ) {
        runDockerCommand(
                Arrays.asList(
                        "docker",
                        "load",
                        "-i",
                        tarIn.toString()
                )
        );
    }

    public void pushImage(
            String imageRef
    ) {
        runDockerCommand(
                Arrays.asList(
                        "docker",
                        "push",
                        imageRef
                )
        );
    }

    private void runDockerCommand(
            List<String> command
    ) {
        if (!isAvailable()) {
            throw new RuntimeException(
                    "docker CLI is not available"
            );
        }
        ProcessBuilder pb = new ProcessBuilder(
                command
        );
        pb.redirectErrorStream(
                true
        );
        try {
            Process process = pb.start();
            String output = new String(
                    process.getInputStream().readAllBytes()
            );
            boolean finished = process.waitFor(
                    timeoutSeconds,
                    TimeUnit.SECONDS
            );
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException(
                        "docker command timed out: " + command
                );
            }
            if (process.exitValue() != 0) {
                throw new RuntimeException(
                        "docker command failed: " + command + "\n" + output
                );
            }
        }
        catch (IOException e) {
            throw new RuntimeException(
                    "Failed to run docker command: " + command,
                    e
            );
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "Interrupted while running docker command: " + command,
                    e
            );
        }
    }
}
