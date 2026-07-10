package io.oci.docker.containerd;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.xenoamess.bbolt.BboltDB;
import com.xenoamess.bbolt.Bucket;
import com.xenoamess.bbolt.ReadOnlyTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MetadataDbImageIdFinder {

    private static final Logger log = LoggerFactory.getLogger(
            MetadataDbImageIdFinder.class
    );

    public Optional<String> findImageId(
            Path dockerRoot,
            Path containerdRoot,
            String repositoryName,
            String reference
    ) {
        List<Path> dbPaths = new ArrayList<>();
        if (containerdRoot != null) {
            dbPaths.add(
                    containerdRoot.resolve(
                            "io.containerd.metadata.v1.bolt/meta.db"
                    )
            );
        }
        if (dockerRoot != null) {
            dbPaths.add(
                    dockerRoot.resolve(
                            "containerd/daemon/io.containerd.metadata.v1.bolt/meta.db"
                    )
            );
        }
        for (Path dbPath : dbPaths) {
            if (!Files.isRegularFile(
                    dbPath
            )) {
                continue;
            }
            Optional<String> result = findInDb(
                    dbPath,
                    repositoryName,
                    reference
            );
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    public Optional<String> findImageId(
            Path dockerRoot,
            String repositoryName,
            String reference
    ) {
        return findImageId(
                dockerRoot,
                null,
                repositoryName,
                reference
        );
    }

    private Optional<String> findInDb(
            Path dbPath,
            String repositoryName,
            String reference
    ) {
        try (
                BboltDB db = BboltDB.open(
                        dbPath
                );
                ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket root = tx.getRootBucket();
            Bucket v1 = root.getBucket(
                    "v1"
            );
            if (v1 == null) {
                return Optional.empty();
            }
            Bucket moby = v1.getBucket(
                    "moby"
            );
            if (moby == null) {
                return Optional.empty();
            }
            Bucket images = moby.getBucket(
                    "images"
            );
            if (images == null) {
                return Optional.empty();
            }
            List<String> names = buildCandidateNames(
                    repositoryName,
                    reference
            );
            for (String name : names) {
                Bucket image = images.getBucket(
                        name.getBytes(
                                StandardCharsets.UTF_8
                        )
                );
                if (image == null) {
                    continue;
                }
                Bucket target = image.getBucket(
                        "target"
                );
                if (target == null) {
                    continue;
                }
                byte[] digest = target.get(
                        "digest".getBytes(
                                StandardCharsets.UTF_8
                        )
                );
                if (digest != null) {
                    return Optional.of(
                            new String(
                                    digest,
                                    StandardCharsets.UTF_8
                            )
                    );
                }
            }
        }
        catch (Exception e) {
            log.warn(
                    "Failed to read bbolt metadata {} for {}:{}: {}",
                    dbPath,
                    repositoryName,
                    reference,
                    e.getMessage()
            );
        }
        return Optional.empty();
    }

    private List<String> buildCandidateNames(
            String repositoryName,
            String reference
    ) {
        List<String> result = new ArrayList<>();
        if (repositoryName == null || reference == null) {
            return result;
        }
        String fullName = repositoryName + ":" + reference;
        result.add(
                fullName
        );
        if (repositoryName.contains(
                "/"
        ) || repositoryName.contains(
                "."
        )) {
            return result;
        }
        result.add(
                "docker.io/library/" + fullName
        );
        result.add(
                "docker.io/" + fullName
        );
        return result;
    }
}
