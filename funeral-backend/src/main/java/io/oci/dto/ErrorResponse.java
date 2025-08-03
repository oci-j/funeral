package io.oci.dto;

import java.util.List;

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
