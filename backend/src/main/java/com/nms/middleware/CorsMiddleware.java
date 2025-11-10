package com.nms.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * CORS middleware for handling cross-origin requests
 */
public class CorsMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(CorsMiddleware.class);
    
    private final JsonObject config;
    
    public CorsMiddleware(JsonObject config) {
        this.config = config;
    }
    
    public static CorsMiddleware create(JsonObject config) {
        return new CorsMiddleware(config);
    }
    
    @Override
    public void handle(RoutingContext ctx) {
        var corsConfig = config.getJsonObject("server.cors", new JsonObject());
        
        if (!corsConfig.getBoolean("enabled", true)) {
            ctx.next();
            return;
        }

        var origin = ctx.request().getHeader(HttpHeaders.ORIGIN);
        
        // Set CORS headers
        setCorsHeaders(ctx, corsConfig, origin);
        
        // Handle preflight requests
        if ("OPTIONS".equals(ctx.request().method().name())) {
            logger.debug("Handling CORS preflight request from origin: {}", origin);
            ctx.response().setStatusCode(200).end();
            return;
        }
        
        ctx.next();
    }
    
    private void setCorsHeaders(RoutingContext ctx, JsonObject corsConfig, String origin) {
        // Check if origin is allowed
        if (isOriginAllowed(origin, corsConfig)) {
            ctx.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        } else {
            // If no specific origin match, use the first allowed origin or *
            var allowedOrigins = getStringArray(corsConfig, "allowedOrigins");
            if (allowedOrigins.length > 0) {
                ctx.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigins[0]);
            }
        }
        
        // Set other CORS headers
        ctx.response()
            .putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, 
                      String.valueOf(corsConfig.getBoolean("allowCredentials", true)))
            .putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, 
                      String.join(", ", getStringArray(corsConfig, "allowedMethods")))
            .putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, 
                      String.join(", ", getStringArray(corsConfig, "allowedHeaders")))
            .putHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, 
                      "Content-Length, Content-Type, Authorization, X-Requested-With")
            .putHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
    }
    
    private boolean isOriginAllowed(String origin, JsonObject corsConfig) {
        if (origin == null) {
            return false;
        }

        var allowedOrigins = getStringArray(corsConfig, "allowedOrigins");
        
        // Check for wildcard
        for (var allowedOrigin : allowedOrigins) {
            if ("*".equals(allowedOrigin)) {
                return true;
            }
            if (origin.equals(allowedOrigin)) {
                return true;
            }
        }
        
        return false;
    }
    
    private String[] getStringArray(JsonObject config, String key) {
        try {
            return config.getJsonArray(key, new io.vertx.core.json.JsonArray())
                .stream()
                .map(Object::toString)
                .toArray(String[]::new);
        } catch (Exception e) {
            logger.warn("Failed to parse {} as string array", key);
            return new String[0];
        }
    }
}
