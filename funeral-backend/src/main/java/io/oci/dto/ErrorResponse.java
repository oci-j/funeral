package io.oci.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public class ErrorResponse {
    public List<Error> errors;

    public ErrorResponse(List<Error> errors) {
        this.errors = errors;
    }

    public static class Error {
        public String code;
        public String message;
        public String detail;

        public Error(String code, String message, String detail) {
            this.code = code;
            this.message = message;
            this.detail = detail;
        }
    }
}
