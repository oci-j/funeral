package io.oci.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.List;

import org.jetbrains.annotations.Nullable;

@RegisterForReflection
@MongoEntity(collection = "manifests")
public class Manifest extends PanacheMongoEntity {

    @RegisterForReflection
    public static class Subject {

        public String digest;

        @BsonProperty("media_type")
        public String mediaType;

        public Long size;

        public Subject() {
        }

        public Subject(
                String digest,
                String mediaType,
                Long size
        ) {
            this.digest = digest;
            this.mediaType = mediaType;
            this.size = size;
        }
    }

    @BsonProperty("repository_id")
    public ObjectId repositoryId;

    @BsonProperty("repository_name")
    public String repositoryName;

    public String digest;

    @BsonProperty("media_type")
    public String mediaType;

    public String content;

    @BsonProperty("content_length")
    public Long contentLength;

    public String tag;

    @BsonProperty("artifact_type")
    public String artifactType;

    public Subject subject;

    public Map<String, Object> annotations;

    @BsonProperty("created_at")
    public LocalDateTime createdAt;

    @BsonProperty("updated_at")
    public LocalDateTime updatedAt;

    public Manifest() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static Manifest findByRepositoryAndDigest(String repositoryName, String digest) {
        return find("repository_name = ?1 and digest = ?2", Sort.by("updated_at", Sort.Direction.Descending), repositoryName, digest).firstResult();
    }

    public static Manifest findByRepositoryAndTag(String repositoryName, String tag) {
        return find("repository_name = ?1 and tag = ?2", Sort.by("updated_at", Sort.Direction.Descending), repositoryName, tag).firstResult();
    }

    public static List<Manifest> findByRepository(String repositoryName) {
        return find("repository_name", Sort.by("updated_at", Sort.Direction.Descending), repositoryName).list();
    }

    public static List<String> findTagsByRepository(
            String repositoryName,
            @Nullable String last,
            int limit
    ) {
        List<Manifest> manifests;
        if (last == null) {
            manifests = find("repository_name = ?1 and tag != ?2", Sort.by("updated_at", Sort.Direction.Descending), repositoryName, null)
                    .page(0, limit)
                    .list();
        } else {
            manifests = find("repository_name = ?1 and tag != ?2 and tag > ?3", Sort.by("updated_at", Sort.Direction.Descending), repositoryName, null, last)
                    .page(0, limit)
                    .list();
        }
        List<String> result = manifests.stream().map(manifest -> manifest.tag).toList();
        System.out.println("repositoryName : " + repositoryName);
        System.out.println("last : " + last);
        System.out.println("limit : " + limit);
        System.out.println(result);
        return result;
    }

    public static List<Manifest> findBySubjectDigest(String repositoryName, String subjectDigest) {
        return find("repository_name = ?1 and subject.digest = ?2", Sort.by("updated_at", Sort.Direction.Descending), repositoryName, subjectDigest).list();
    }

    public static List<Manifest> findBySubjectDigestAndArtifactType(String repositoryName, String subjectDigest, String artifactType) {
        return find("repository_name = ?1 and subject.digest = ?2 and artifact_type = ?3", Sort.by("updated_at", Sort.Direction.Descending), repositoryName, subjectDigest, artifactType).list();
    }

    public static long countByRepository(String repositoryName) {
        return count("repository_name = ?1 and tag != null",repositoryName);
    }
}
