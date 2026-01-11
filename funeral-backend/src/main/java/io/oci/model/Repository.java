package io.oci.model;

import java.time.LocalDateTime;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.bson.codecs.pojo.annotations.BsonProperty;

@RegisterForReflection
@MongoEntity(
        collection = "repositories"
)
public class Repository extends PanacheMongoEntity {

    public String name;

    @BsonProperty(
        "created_at"
    )
    public LocalDateTime createdAt;

    @BsonProperty(
        "updated_at"
    )
    public LocalDateTime updatedAt;

    public Repository() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Repository(
            String name
    ) {
        this();
        this.name = name;
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
