package io.oci.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

@RegisterForReflection
@MongoEntity(
        collection = "manifests"
)
public class Manifest extends PanacheMongoEntity {

    @RegisterForReflection
    public static class Subject {

        public String digest;

        @BsonProperty(
            "media_type"
        )
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

    @BsonProperty(
        "repository_id"
    )
    public ObjectId repositoryId;

    @BsonProperty(
        "repository_name"
    )
    public String repositoryName;

    public String digest;

    @BsonProperty(
        "media_type"
    )
    public String mediaType;

    public String content;

    @BsonProperty(
        "content_length"
    )
    public Long contentLength;

    public String tag;

    @BsonProperty(
        "artifact_type"
    )
    public String artifactType;

    public Subject subject;

    public Map<String, Object> annotations;

    @BsonProperty(
        "created_at"
    )
    public LocalDateTime createdAt;

    @BsonProperty(
        "updated_at"
    )
    public LocalDateTime updatedAt;

    // Additional fields for Docker tar import
    @BsonProperty(
        "config_digest"
    )
    public String configDigest;

    @BsonProperty(
        "layer_digests"
    )
    public List<String> layerDigests;

    public Manifest() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
