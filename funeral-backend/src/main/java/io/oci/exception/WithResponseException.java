package io.oci.exception;

import jakarta.ws.rs.core.Response;

public class WithResponseException extends Exception {

    private final Response response;

    public Response getResponse() {
        return response;
    }

    public WithResponseException(Response response) {
        super("");
        this.response = response;
    }

    public WithResponseException(Response response, Throwable cause) {
        super(cause.getMessage(), cause);
        this.response = response;
    }

}
