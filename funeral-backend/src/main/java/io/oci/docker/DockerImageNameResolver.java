package io.oci.docker;

import java.util.List;

import com.github.dockerjava.api.model.Image;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DockerImageNameResolver {

    public String resolveByRepositoryName(
            String repositoryName,
            List<Image> images
    ) {
        String prefix = repositoryName + ":";
        String libraryPrefix = "docker.io/library/" + prefix;
        String dockerIoPrefix = "docker.io/" + prefix;

        for (Image image : images) {
            String[] repoTags = image.getRepoTags();
            if (repoTags == null) {
                continue;
            }
            for (String repoTag : repoTags) {
                if (repoTag.startsWith(
                        prefix
                ) || repoTag.startsWith(
                        libraryPrefix
                ) || repoTag.startsWith(
                        dockerIoPrefix
                )) {
                    return repoTag;
                }
            }
        }

        return null;
    }

    public String resolve(
            String repositoryName,
            String tag,
            List<Image> images
    ) {
        String fullName = repositoryName + ":" + tag;
        String libraryName = "docker.io/library/" + fullName;
        String dockerIoName = "docker.io/" + fullName;

        for (String candidate : new String[] {
                fullName, libraryName, dockerIoName
        }) {
            String match = findMatch(
                    candidate,
                    images
            );
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    private String findMatch(
            String candidate,
            List<Image> images
    ) {
        for (Image image : images) {
            String[] repoTags = image.getRepoTags();
            if (repoTags == null) {
                continue;
            }
            for (String repoTag : repoTags) {
                if (candidate.equals(
                        repoTag
                )) {
                    return repoTag;
                }
            }
        }
        return null;
    }
}
