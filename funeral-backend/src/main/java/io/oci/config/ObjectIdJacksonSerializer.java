package io.oci.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.bson.types.ObjectId;

import java.io.IOException;

public class ObjectIdJacksonSerializer {

    public static class ObjectIdSerializer extends JsonSerializer<ObjectId> {
        @Override
        public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value != null) {
                gen.writeString(value.toString());
            } else {
                gen.writeNull();
            }
        }
    }

    public static class ObjectIdDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<ObjectId> {
        @Override
        public ObjectId deserialize(com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            if (value != null && !value.isEmpty()) {
                return new ObjectId(value);
            }
            return null;
        }
    }

    // Mixin for ObjectId to use custom serialization
    @JsonSerialize(using = ObjectIdSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = ObjectIdDeserializer.class)
    public abstract static class ObjectIdMixin {
        // This mixin will be applied to ObjectId class
    }
}
