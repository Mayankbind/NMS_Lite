package com.nms.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.services.AuthService;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Authentication handler for login, logout, and token refresh
 */
public class AuthHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);
    
    private final AuthService authService;
    
    public AuthHandler(AuthService authService) {
        this.authService = authService;
    }
    
    /**
     * Handle user login
     */
    public Handler<RoutingContext> login = this::handleLogin;
    
    /**
     * Handle token refresh
     */
    public Handler<RoutingContext> refresh = this::handleRefresh;
    
    /**
     * Handle user logout
     */
    public Handler<RoutingContext> logout = this::handleLogout;
    
    private void handleLogin(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                sendBadRequestResponse(ctx, "Request body is required");
                return;
            }
            
            String username = body.getString("username");
            String password = body.getString("password");
            
            if (username == null || username.trim().isEmpty()) {
                sendBadRequestResponse(ctx, "Username is required");
                return;
            }
            
            if (password == null || password.isEmpty()) {
                sendBadRequestResponse(ctx, "Password is required");
                return;
            }
            
            authService.authenticate(username.trim(), password)
                .onSuccess(authResult -> {
                    logger.info("User {} logged in successfully", username);
                    sendSuccessResponse(ctx, authResult);
                })
                .onFailure(throwable -> {
                    logger.warn("Login failed for user {}: {}", username, throwable.getMessage());
                    sendUnauthorizedResponse(ctx, throwable.getMessage());
                });
                
        } catch (Exception e) {
            logger.error("Error handling login request", e);
            sendInternalServerErrorResponse(ctx, "An unexpected error occurred");
        }
    }
    
    private void handleRefresh(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                sendBadRequestResponse(ctx, "Request body is required");
                return;
            }
            
            String refreshToken = body.getString("refreshToken");
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                sendBadRequestResponse(ctx, "Refresh token is required");
                return;
            }
            
            authService.refreshToken(refreshToken.trim())
                .onSuccess(tokenResult -> {
                    logger.info("Token refreshed successfully");
                    sendSuccessResponse(ctx, tokenResult);
                })
                .onFailure(throwable -> {
                    logger.warn("Token refresh failed: {}", throwable.getMessage());
                    sendUnauthorizedResponse(ctx, throwable.getMessage());
                });
                
        } catch (Exception e) {
            logger.error("Error handling token refresh request", e);
            sendInternalServerErrorResponse(ctx, "An unexpected error occurred");
        }
    }
    
    private void handleLogout(RoutingContext ctx) {
        try {
            // In a stateless JWT system, logout is typically handled client-side
            // by removing the token. However, we can log the logout event.
            String username = ctx.get("username");
            if (username != null) {
                logger.info("User {} logged out", username);
            }
            
            JsonObject response = new JsonObject()
                .put("message", "Logged out successfully")
                .put("timestamp", System.currentTimeMillis());
            
            sendSuccessResponse(ctx, response);
            
        } catch (Exception e) {
            logger.error("Error handling logout request", e);
            sendInternalServerErrorResponse(ctx, "An unexpected error occurred");
        }
    }
    
    private void sendSuccessResponse(RoutingContext ctx, JsonObject data) {
        ctx.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(data.encode());
    }
    
    private void sendBadRequestResponse(RoutingContext ctx, String message) {
        ctx.response()
            .setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("error", "Bad Request")
                .put("message", message)
                .put("timestamp", System.currentTimeMillis())
                .encode());
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
    
    private void sendInternalServerErrorResponse(RoutingContext ctx, String message) {
        ctx.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("error", "Internal Server Error")
                .put("message", message)
                .put("timestamp", System.currentTimeMillis())
                .encode());
    }
}
