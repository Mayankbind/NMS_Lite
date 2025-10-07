package com.nms.middleware;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

/**
 * Logging middleware for request/response logging and correlation IDs
 */
public class LoggingMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingMiddleware.class);
    
    public static LoggingMiddleware create() {
        return new LoggingMiddleware();
    }
    
    @Override
    public void handle(RoutingContext ctx) {
        // Generate correlation ID
        String correlationId = ctx.request().getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        // Set correlation ID in MDC for logging
        MDC.put("correlationId", correlationId);
        
        // Add correlation ID to response headers
        ctx.response().putHeader("X-Correlation-ID", correlationId);
        
        // Log request
        logRequest(ctx, correlationId);
        
        // Add response handler
        final String finalCorrelationId = correlationId;
        ctx.addHeadersEndHandler(v -> logResponse(ctx, finalCorrelationId));
        
        ctx.next();
    }
    
    private void logRequest(RoutingContext ctx, String correlationId) {
        String method = ctx.request().method().name();
        String uri = ctx.request().uri();
        String userAgent = ctx.request().getHeader(HttpHeaders.USER_AGENT);
        String remoteAddress = ctx.request().remoteAddress().host();
        int remotePort = ctx.request().remoteAddress().port();
        
        logger.info("{} {} from {}:{} - User-Agent: {} - Correlation-ID: {}", 
            method, uri, remoteAddress, remotePort, userAgent, correlationId);
    }
    
    private void logResponse(RoutingContext ctx, String correlationId) {
        int statusCode = ctx.response().getStatusCode();
        String method = ctx.request().method().name();
        String uri = ctx.request().uri();
        long responseTime = System.currentTimeMillis() - ctx.get("startTime", System.currentTimeMillis());
        
        if (statusCode >= 400) {
            logger.warn("{} {} - Status: {} - Response time: {}ms - Correlation-ID: {}", 
                method, uri, statusCode, responseTime, correlationId);
        } else {
            logger.info("{} {} - Status: {} - Response time: {}ms - Correlation-ID: {}", 
                method, uri, statusCode, responseTime, correlationId);
        }
        
        // Clear MDC
        MDC.clear();
    }
}
