package com.artezio.ws.rs;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.core.HttpHeaders.CACHE_CONTROL;
import static org.jboss.resteasy.util.HttpHeaderNames.PRAGMA;

@Priority(Priorities.HEADER_DECORATOR - 1)
@Provider
public class PragmaRemover implements ContainerResponseFilter {

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        if (headers.containsKey(CACHE_CONTROL)) {
            for (Object cacheAttribute : headers.get(HttpHeaders.CACHE_CONTROL)) {
                CacheControl cacheControl = (CacheControl) cacheAttribute;
                if(!cacheControl.isNoCache()){
                    headers.add(PRAGMA, "");
                }
            }
        }
    }

}
