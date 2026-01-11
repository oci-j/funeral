package io.oci.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(
    JsonInclude.Include.NON_NULL
)
public class ErrorResponse {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @JsonProperty(
        "errors"
    )
    public List<Error> errors;

    public ErrorResponse() {
    }

    public ErrorResponse(
            List<Error> errors
    ) {
        this.errors = errors;
    }

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(
                    this
            );
        }
        catch (JsonProcessingException e) {
            return "{\"errors\":[{\"code\":\"UNKNOWN\",\"message\":\"Failed to serialize error response\",\"detail\":\"\"}]}";
        }
    }

    @RegisterForReflection
    @JsonInclude(
        JsonInclude.Include.NON_NULL
    )
    public static class Error {
        @JsonProperty(
            "code"
        )
        public String code;

        @JsonProperty(
            "message"
        )
        public String message;

        @JsonProperty(
            "detail"
        )
        public String detail;

        public Error() {
        }

        public Error(
                String code,
                String message,
                String detail
        ) {
            this.code = code;
            this.message = message;
            this.detail = detail;
        }
    }
}
