package io.oci.model;

import java.time.LocalDateTime;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.bson.codecs.pojo.annotations.BsonProperty;

@RegisterForReflection
@MongoEntity(
        collection = "blobs"
)
public class Blob extends PanacheMongoEntity {

    public String digest;

    @BsonProperty(
        "content_length"
    )
    public Long contentLength;

    @BsonProperty(
        "media_type"
    )
    public String mediaType;

    @BsonProperty(
        "s3_key"
    )
    public String s3Key;

    @BsonProperty(
        "s3_bucket"
    )
    public String s3Bucket;

    @BsonProperty(
        "created_at"
    )
    public LocalDateTime createdAt;

    @BsonProperty(
        "updated_at"
    )
    public LocalDateTime updatedAt;

    public Blob() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
