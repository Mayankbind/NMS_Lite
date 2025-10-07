package com.nms.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Security headers middleware for adding security-related HTTP headers
 */
public class SecurityHeadersMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityHeadersMiddleware.class);
    
    public static SecurityHeadersMiddleware create() {
        return new SecurityHeadersMiddleware();
    }
    
    @Override
    public void handle(RoutingContext ctx) {
        // Add security headers
        ctx.response()
            // Prevent clickjacking
            .putHeader("X-Frame-Options", "DENY")
            // Prevent MIME type sniffing
            .putHeader("X-Content-Type-Options", "nosniff")
            // Enable XSS protection
            .putHeader("X-XSS-Protection", "1; mode=block")
            // Referrer policy
            .putHeader("Referrer-Policy", "strict-origin-when-cross-origin")
            // Content Security Policy
            .putHeader("Content-Security-Policy", 
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' data:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'")
            // Permissions Policy
            .putHeader("Permissions-Policy", 
                "geolocation=(), " +
                "microphone=(), " +
                "camera=(), " +
                "payment=(), " +
                "usb=(), " +
                "magnetometer=(), " +
                "gyroscope=(), " +
                "speaker=()")
            // Remove server information
            .putHeader("Server", "NMS-Lite")
            // Cache control for API responses
            .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            .putHeader("Pragma", "no-cache")
            .putHeader("Expires", "0");
        
        // Add HSTS header for HTTPS
        if (ctx.request().isSSL()) {
            ctx.response().putHeader("Strict-Transport-Security", 
                "max-age=31536000; includeSubDomains; preload");
        }
        
        logger.debug("Security headers added to response");
        ctx.next();
    }
}
