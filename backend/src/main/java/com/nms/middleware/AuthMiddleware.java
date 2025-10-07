package com.nms.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.services.AuthService;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Authentication middleware for protecting routes
 */
public class AuthMiddleware implements Handler<RoutingContext> {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthMiddleware.class);
    
    private final AuthService authService;
    
    public AuthMiddleware(AuthService authService) {
        this.authService = authService;
    }
    
    public static AuthMiddleware create(AuthService authService) {
        return new AuthMiddleware(authService);
    }
    
    @Override
    public void handle(RoutingContext ctx) {
        String authorization = ctx.request().getHeader(HttpHeaders.AUTHORIZATION);
        
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            sendUnauthorizedResponse(ctx, "Missing or invalid authorization header");
            return;
        }
        
        String token = authorization.substring(7); // Remove "Bearer " prefix
        
        authService.validateToken(token)
            .onSuccess(userInfo -> {
                // Add user information to context
                ctx.put("user", userInfo);
                ctx.put("userId", userInfo.getString("userId"));
                ctx.put("username", userInfo.getString("username"));
                ctx.put("role", userInfo.getString("role"));
                
                logger.debug("User {} authenticated successfully", userInfo.getString("username"));
                ctx.next();
            })
            .onFailure(throwable -> {
                logger.warn("Authentication failed: {}", throwable.getMessage());
                sendUnauthorizedResponse(ctx, "Invalid or expired token");
            });
    }
    
    private void sendUnauthorizedResponse(RoutingContext ctx, String message) {
        ctx.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .putHeader("WWW-Authenticate", "Bearer")
            .end(new JsonObject()
                .put("error", "Unauthorized")
                .put("message", message)
                .put("timestamp", System.currentTimeMillis())
                .encode());
    }
}

