package io.oci.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.time.LocalDateTime;

@MongoEntity(collection = "blobs")
public class Blob extends PanacheMongoEntity {

    public String digest;

    @BsonProperty("content_length")
    public Long contentLength;

    @BsonProperty("media_type")
    public String mediaType;

    @BsonProperty("s3_key")
    public String s3Key;

    @BsonProperty("s3_bucket")
    public String s3Bucket;

    @BsonProperty("created_at")
    public LocalDateTime createdAt;

    public Blob() {
        this.createdAt = LocalDateTime.now();
    }

    public static Blob findByDigest(String digest) {
        return find("digest", digest).firstResult();
    }
}
