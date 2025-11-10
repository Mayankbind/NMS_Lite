package com.nms.handlers;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.models.CredentialProfileDTO;
import com.nms.services.CredentialService;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler for credential profile CRUD operations
 * Provides REST API endpoints for managing SSH credentials
 */
public class CredentialHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CredentialHandler.class);
    
    private final CredentialService credentialService;
    
    public CredentialHandler(CredentialService credentialService) {
        this.credentialService = credentialService;
    }
    
    /**
     * Handle creating a new credential profile
     */
    public Handler<RoutingContext> createCredentialProfile = this::handleCreateCredentialProfile;
    
    /**
     * Handle getting all credential profiles
     */
    public Handler<RoutingContext> getAllCredentialProfiles = this::handleGetAllCredentialProfiles;
    
    /**
     * Handle getting a specific credential profile
     */
    public Handler<RoutingContext> getCredentialProfile = this::handleGetCredentialProfile;
    
    /**
     * Handle updating a credential profile
     */
    public Handler<RoutingContext> updateCredentialProfile = this::handleUpdateCredentialProfile;
    
    /**
     * Handle deleting a credential profile
     */
    public Handler<RoutingContext> deleteCredentialProfile = this::handleDeleteCredentialProfile;
    
    private void handleCreateCredentialProfile(RoutingContext ctx) {
        try {
            var body = ctx.body().asJsonObject();
            if (body == null) {
                sendBadRequestResponse(ctx, "Request body is required");
                return;
            }
            
            // Extract user ID from JWT context (set by AuthMiddleware)
            var userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendUnauthorizedResponse(ctx, "User not authenticated");
                return;
            }
            
            // Create DTO from request body
            var dto = new CredentialProfileDTO();
            dto.setName(body.getString("name"));
            dto.setUsername(body.getString("username"));
            dto.setPassword(body.getString("password"));
            dto.setPrivateKey(body.getString("privateKey"));

            var port = body.getInteger("port");
            dto.setPort(port);
            
            // Create credential profile
            credentialService.createCredentialProfile(dto, userId)
                .onSuccess(result -> {
                    var response = JsonObject.mapFrom(result);
                    sendSuccessResponse(ctx, response, "Credential profile created successfully");
                })
                .onFailure(throwable -> {
                    logger.error("Failed to create credential profile", throwable);
                    if (throwable instanceof IllegalArgumentException) {
                        sendBadRequestResponse(ctx, throwable.getMessage());
                    } else {
                        sendInternalServerErrorResponse(ctx, "Failed to create credential profile");
                    }
                });
                
        } catch (Exception e) {
            logger.error("Error in handleCreateCredentialProfile", e);
            sendInternalServerErrorResponse(ctx, "Internal server error");
        }
    }
    
    private void handleGetAllCredentialProfiles(RoutingContext ctx) {
        try {
            // Extract user ID from JWT context
            var userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendUnauthorizedResponse(ctx, "User not authenticated");
                return;
            }
            
            // Get all credential profiles
            credentialService.getAllCredentialProfiles(userId)
                .onSuccess(profiles -> {
                    var response = new JsonObject()
                        .put("success", true)
                        .put("message", "Credential profiles retrieved successfully")
                        .put("data", profiles)
                        .put("count", profiles.size());
                    sendSuccessResponse(ctx, response);
                })
                .onFailure(throwable -> {
                    logger.error("Failed to get credential profiles", throwable);
                    sendInternalServerErrorResponse(ctx, "Failed to retrieve credential profiles");
                });
                
        } catch (Exception e) {
            logger.error("Error in handleGetAllCredentialProfiles", e);
            sendInternalServerErrorResponse(ctx, "Internal server error");
        }
    }
    
    private void handleGetCredentialProfile(RoutingContext ctx) {
        try {
            // Extract credential profile ID from path
            var idParam = ctx.pathParam("id");
            if (idParam == null || idParam.trim().isEmpty()) {
                sendBadRequestResponse(ctx, "Credential profile ID is required");
                return;
            }
            
            UUID credentialProfileId;
            try {
                credentialProfileId = UUID.fromString(idParam);
            } catch (IllegalArgumentException e) {
                sendBadRequestResponse(ctx, "Invalid credential profile ID format");
                return;
            }
            
            // Extract user ID from JWT context
            var userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendUnauthorizedResponse(ctx, "User not authenticated");
                return;
            }
            
            // Get credential profile
            credentialService.getCredentialProfileById(credentialProfileId, userId)
                .onSuccess(profile -> {
                    var response = JsonObject.mapFrom(profile);
                    sendSuccessResponse(ctx, response, "Credential profile retrieved successfully");
                })
                .onFailure(throwable -> {
                    logger.error("Failed to get credential profile", throwable);
                    if (throwable.getMessage().contains("not found")) {
                        sendNotFoundResponse(ctx, "Credential profile not found");
                    } else {
                        sendInternalServerErrorResponse(ctx, "Failed to retrieve credential profile");
                    }
                });
                
        } catch (Exception e) {
            logger.error("Error in handleGetCredentialProfile", e);
            sendInternalServerErrorResponse(ctx, "Internal server error");
        }
    }
    
    private void handleUpdateCredentialProfile(RoutingContext ctx) {
        try {
            var body = ctx.body().asJsonObject();
            if (body == null) {
                sendBadRequestResponse(ctx, "Request body is required");
                return;
            }
            
            // Extract credential profile ID from path
            var idParam = ctx.pathParam("id");
            if (idParam == null || idParam.trim().isEmpty()) {
                sendBadRequestResponse(ctx, "Credential profile ID is required");
                return;
            }
            
            UUID credentialProfileId;
            try {
                credentialProfileId = UUID.fromString(idParam);
            } catch (IllegalArgumentException e) {
                sendBadRequestResponse(ctx, "Invalid credential profile ID format");
                return;
            }
            
            // Extract user ID from JWT context
            var userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendUnauthorizedResponse(ctx, "User not authenticated");
                return;
            }
            
            // Create DTO from request body
            var dto = new CredentialProfileDTO();
            dto.setName(body.getString("name"));
            dto.setUsername(body.getString("username"));
            dto.setPassword(body.getString("password"));
            dto.setPrivateKey(body.getString("privateKey"));

            var port = body.getInteger("port");
            dto.setPort(port);
            
            // Update credential profile
            credentialService.updateCredentialProfile(credentialProfileId, dto, userId)
                .onSuccess(result -> {
                    var response = JsonObject.mapFrom(result);
                    sendSuccessResponse(ctx, response, "Credential profile updated successfully");
                })
                .onFailure(throwable -> {
                    logger.error("Failed to update credential profile", throwable);
                    if (throwable instanceof IllegalArgumentException) {
                        sendBadRequestResponse(ctx, throwable.getMessage());
                    } else if (throwable.getMessage().contains("not found")) {
                        sendNotFoundResponse(ctx, "Credential profile not found");
                    } else {
                        sendInternalServerErrorResponse(ctx, "Failed to update credential profile");
                    }
                });
                
        } catch (Exception e) {
            logger.error("Error in handleUpdateCredentialProfile", e);
            sendInternalServerErrorResponse(ctx, "Internal server error");
        }
    }
    
    private void handleDeleteCredentialProfile(RoutingContext ctx) {
        try {
            // Extract credential profile ID from path
            var idParam = ctx.pathParam("id");
            if (idParam == null || idParam.trim().isEmpty()) {
                sendBadRequestResponse(ctx, "Credential profile ID is required");
                return;
            }
            
            UUID credentialProfileId;
            try {
                credentialProfileId = UUID.fromString(idParam);
            } catch (IllegalArgumentException e) {
                sendBadRequestResponse(ctx, "Invalid credential profile ID format");
                return;
            }
            
            // Extract user ID from JWT context
            var userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendUnauthorizedResponse(ctx, "User not authenticated");
                return;
            }
            
            // Delete credential profile
            credentialService.deleteCredentialProfile(credentialProfileId, userId)
                .onSuccess(v -> {
                    var response = new JsonObject()
                        .put("success", true)
                        .put("message", "Credential profile deleted successfully");
                    sendSuccessResponse(ctx, response);
                })
                .onFailure(throwable -> {
                    logger.error("Failed to delete credential profile", throwable);
                    if (throwable.getMessage().contains("not found")) {
                        sendNotFoundResponse(ctx, "Credential profile not found");
                    } else {
                        sendInternalServerErrorResponse(ctx, "Failed to delete credential profile");
                    }
                });
                
        } catch (Exception e) {
            logger.error("Error in handleDeleteCredentialProfile", e);
            sendInternalServerErrorResponse(ctx, "Internal server error");
        }
    }
    
    /**
     * Extract user ID from JWT context (set by AuthMiddleware)
     */
    private UUID getUserIdFromContext(RoutingContext ctx) {
        try {
            JsonObject userContext = ctx.get("user");
            if (userContext != null && userContext.containsKey("userId")) {
                var userIdStr = userContext.getString("userId");
                if (userIdStr != null) {
                    return UUID.fromString(userIdStr);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to extract user ID from context", e);
        }
        return null;
    }
    
    /**
     * Send success response
     */
    private void sendSuccessResponse(RoutingContext ctx, JsonObject data) {
        sendSuccessResponse(ctx, data, "Operation completed successfully");
    }
    
    private void sendSuccessResponse(RoutingContext ctx, JsonObject data, String message) {
        var response = new JsonObject()
            .put("success", true)
            .put("message", message);
        
        if (data != null) {
            response.put("data", data);
        }
        
        ctx.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    /**
     * Send bad request response
     */
    private void sendBadRequestResponse(RoutingContext ctx, String message) {
        var response = new JsonObject()
            .put("success", false)
            .put("error", "Bad Request")
            .put("message", message);
        
        ctx.response()
            .setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    /**
     * Send unauthorized response
     */
    private void sendUnauthorizedResponse(RoutingContext ctx, String message) {
        var response = new JsonObject()
            .put("success", false)
            .put("error", "Unauthorized")
            .put("message", message);
        
        ctx.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    /**
     * Send not found response
     */
    private void sendNotFoundResponse(RoutingContext ctx, String message) {
        var response = new JsonObject()
            .put("success", false)
            .put("error", "Not Found")
            .put("message", message);
        
        ctx.response()
            .setStatusCode(404)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    /**
     * Send internal server error response
     */
    private void sendInternalServerErrorResponse(RoutingContext ctx, String message) {
        var response = new JsonObject()
            .put("success", false)
            .put("error", "Internal Server Error")
            .put("message", message);
        
        ctx.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
}