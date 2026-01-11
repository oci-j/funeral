package io.oci.filter;

import io.oci.resource.OciV2Resource;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) {
        try {
            System.out.println(">>> " + requestContext.getMethod() + " " + requestContext.getUriInfo().getRequestUri());
        }catch (Exception e){
            log.error("filter error",e);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        try {
            System.out.println("<<< " + responseContext.getStatus()
                    + " " + requestContext.getMethod()
                    + " " + requestContext.getUriInfo().getRequestUri());
        }catch (Exception e){
            log.error("filter error",e);
        }
    }
}