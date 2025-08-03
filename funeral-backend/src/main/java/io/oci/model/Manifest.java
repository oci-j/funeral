package io.oci.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import java.util.List;

@MongoEntity(collection = "manifests")
public class Manifest extends PanacheMongoEntity {

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

    @BsonProperty("created_at")
    public LocalDateTime createdAt;

    public Manifest() {
        this.createdAt = LocalDateTime.now();
    }

    public static Manifest findByRepositoryAndDigest(String repositoryName, String digest) {
        return find("repositoryName = ?1 and digest = ?2", repositoryName, digest).firstResult();
    }

    public static Manifest findByRepositoryAndTag(String repositoryName, String tag) {
        return find("repositoryName = ?1 and tag = ?2", repositoryName, tag).firstResult();
    }

    public static List<Manifest> findByRepository(String repositoryName) {
        return find("repositoryName", repositoryName).list();
    }

    public static List<String> findTagsByRepository(String repositoryName) {
        return find("repositoryName = ?1 and tag != null", repositoryName)
            .project(String.class)
            .list();
    }

    public static long countByRepository(String repositoryName) {
        return count("repositoryName = ?1 and tag != null", repositoryName);
    }
}
