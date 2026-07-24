package io.oci.provider;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultExceptionHandlerTest {

    private final DefaultExceptionHandler handler = new DefaultExceptionHandler();

    @Test
    public void testNotFoundExceptionMapsTo404() {
        Response response = handler.toResponse(
                new NotFoundException(
                        "resource missing"
                )
        );
        assertEquals(
                404,
                response.getStatus()
        );
    }

    @Test
    public void testGenericExceptionMapsTo400() {
        Response response = handler.toResponse(
                new RuntimeException(
                        "something broke"
                )
        );
        assertEquals(
                400,
                response.getStatus()
        );
        assertEquals(
                "something broke",
                response.getEntity()
        );
    }

    @Test
    public void testExceptionWithNullMessage() {
        Response response = handler.toResponse(
                new IllegalStateException()
        );
        assertEquals(
                400,
                response.getStatus()
        );
    }
}
