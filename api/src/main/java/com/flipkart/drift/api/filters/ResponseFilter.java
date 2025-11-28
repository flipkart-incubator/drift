package com.flipkart.drift.api.filters;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class ResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) {
        containerResponseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
        containerResponseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        String requestHeader = containerRequestContext.getHeaders().getFirst("Access-Control-Request-Headers");
        if (requestHeader != null) {
            containerResponseContext.getHeaders().add("Access-Control-Allow-Headers", requestHeader);
        }
        containerResponseContext.getHeaders().add("X_SERVER_TRACE_ID", MDC.get("id"));
        this.removeFromMDC();
    }

    private void removeFromMDC() {
        MDC.remove("id");
        RequestThreadContext.remove();
    }
}




