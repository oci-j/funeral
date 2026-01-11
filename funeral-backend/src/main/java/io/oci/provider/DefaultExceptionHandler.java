package io.oci.provider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@ApplicationScoped
public class DefaultExceptionHandler implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(
            Exception exception
    ) {
        exception.printStackTrace();
        if (exception instanceof NotFoundException) {
            return Response.status(
                    Response.Status.NOT_FOUND
            )
                    .entity(
                            exception.getMessage()
                    )
                    .build();
        }
        return Response.status(
                Response.Status.BAD_REQUEST
        )
                .entity(
                        exception.getMessage()
                )
                .build();
    }

}
