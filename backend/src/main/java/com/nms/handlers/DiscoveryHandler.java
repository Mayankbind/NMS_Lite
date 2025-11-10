package com.nms.handlers;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.models.Device;
import com.nms.models.DiscoveryJob;
import com.nms.models.DiscoveryJobDTO;
import com.nms.services.IDiscoveryService;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler for device discovery operations
 * Provides REST API endpoints for managing discovery jobs
 */
public class DiscoveryHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryHandler.class);
    
    private final IDiscoveryService discoveryService;
    
    public DiscoveryHandler(IDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }
    
    /**
     * Handle starting a new discovery job
     */
    public Handler<RoutingContext> startDiscovery = this::handleStartDiscovery;
    
    /**
     * Handle getting discovery job status
     */
    public Handler<RoutingContext> getDiscoveryStatus = this::handleGetDiscoveryStatus;
    
    /**
     * Handle getting discovery results
     */
    public Handler<RoutingContext> getDiscoveryResults = this::handleGetDiscoveryResults;
    
    /**
     * Handle cancelling a discovery job
     */
    public Handler<RoutingContext> cancelDiscovery = this::handleCancelDiscovery;
    
    /**
     * Handle starting a new discovery job
     * POST /api/discovery/start
     */
    private void handleStartDiscovery(RoutingContext ctx) {
        try {
            // Get user ID from context (set by AuthMiddleware)
            var userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Parse request body
            var requestBody = ctx.body().asJsonObject();
            if (requestBody == null) {
                sendErrorResponse(ctx, 400, "Bad Request", "Request body is required");
                return;
            }
            
            // Validate required fields
            var name = requestBody.getString("name");
            var targetRange = requestBody.getString("targetRange");
            var credentialProfileIdStr = requestBody.getString("credentialProfileId");
            
            if (name == null || name.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Field 'name' is required");
                return;
            }
            
            if (targetRange == null || targetRange.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Field 'targetRange' is required");
                return;
            }
            
            if (credentialProfileIdStr == null || credentialProfileIdStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Field 'credentialProfileId' is required");
                return;
            }
            
            // Parse credential profile ID
            UUID credentialProfileId;
            try {
                credentialProfileId = UUID.fromString(credentialProfileIdStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid credentialProfileId format");
                return;
            }
            
            // Create DTO
            var request = new DiscoveryJobDTO();
            request.setName(name.trim());
            request.setTargetRange(targetRange.trim());
            request.setCredentialProfileId(credentialProfileId);
            
            // Start discovery
            discoveryService.startDiscovery(request, userId)
                .onSuccess(jobId -> {
                    logger.info("Started discovery job {} for user {}", jobId, userId);

                    var response = new JsonObject()
                        .put("success", true)
                        .put("message", "Discovery job started successfully")
                        .put("jobId", jobId.toString())
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to start discovery job for user {}: {}", userId, throwable.getMessage(), throwable);

                    var errorMessage = "Failed to start discovery job";
                    if (throwable.getMessage() != null) {
                        errorMessage = throwable.getMessage();
                    }
                    
                    sendErrorResponse(ctx, 500, "Internal Server Error", errorMessage);
                });
                
        } catch (Exception e) {
            logger.error("Error in handleStartDiscovery", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle getting discovery job status
     * GET /api/discovery/status/{jobId}
     */
    private void handleGetDiscoveryStatus(RoutingContext ctx) {
        try {
            // Get user ID from context
            var userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get job ID from path parameter
            var jobIdStr = ctx.pathParam("jobId");
            if (jobIdStr == null || jobIdStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Job ID is required");
                return;
            }
            
            // Parse job ID
            UUID jobId;
            try {
                jobId = UUID.fromString(jobIdStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid job ID format");
                return;
            }
            
            // Get discovery status
            discoveryService.getDiscoveryStatus(jobId, userId)
                .onSuccess(job -> {
                    logger.debug("Retrieved discovery job status for job {} and user {}", jobId, userId);

                    var response = new JsonObject()
                        .put("success", true)
                        .put("job", mapDiscoveryJobToJson(job))
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to get discovery status for job {} and user {}: {}", jobId, userId, throwable.getMessage(), throwable);

                    var errorMessage = "Failed to get discovery status";
                    if (throwable.getMessage() != null) {
                        errorMessage = throwable.getMessage();
                    }

                    var statusCode = 500;
                    if (throwable.getMessage() != null && throwable.getMessage().contains("not found")) {
                        statusCode = 404;
                    }
                    
                    sendErrorResponse(ctx, statusCode, "Internal Server Error", errorMessage);
                });
                
        } catch (Exception e) {
            logger.error("Error in handleGetDiscoveryStatus", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle getting discovery results
     * GET /api/discovery/results/{jobId}
     */
    private void handleGetDiscoveryResults(RoutingContext ctx) {
        try {
            // Get user ID from context
            var userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get job ID from path parameter
            var jobIdStr = ctx.pathParam("jobId");
            if (jobIdStr == null || jobIdStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Job ID is required");
                return;
            }
            
            // Parse job ID
            UUID jobId;
            try {
                jobId = UUID.fromString(jobIdStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid job ID format");
                return;
            }
            
            // Get discovery results
            discoveryService.getDiscoveryResults(jobId, userId)
                .onSuccess(devices -> {
                    logger.debug("Retrieved discovery results for job {} and user {}: {} devices", jobId, userId, devices.size());

                    var response = new JsonObject()
                        .put("success", true)
                        .put("devices", mapDevicesToJson(devices))
                        .put("count", devices.size())
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to get discovery results for job {} and user {}: {}", jobId, userId, throwable.getMessage(), throwable);

                    var errorMessage = "Failed to get discovery results";
                    if (throwable.getMessage() != null) {
                        errorMessage = throwable.getMessage();
                    }

                    var statusCode = 500;
                    if (throwable.getMessage() != null && throwable.getMessage().contains("not found")) {
                        statusCode = 404;
                    }
                    
                    sendErrorResponse(ctx, statusCode, "Internal Server Error", errorMessage);
                });
                
        } catch (Exception e) {
            logger.error("Error in handleGetDiscoveryResults", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle cancelling a discovery job
     * DELETE /api/discovery/job/{jobId}
     */
    private void handleCancelDiscovery(RoutingContext ctx) {
        try {
            // Get user ID from context
            var userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get job ID from path parameter
            var jobIdStr = ctx.pathParam("jobId");
            if (jobIdStr == null || jobIdStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Job ID is required");
                return;
            }
            
            // Parse job ID
            UUID jobId;
            try {
                jobId = UUID.fromString(jobIdStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid job ID format");
                return;
            }
            
            // Cancel discovery
            discoveryService.cancelDiscovery(jobId, userId)
                .onSuccess(v -> {
                    logger.info("Cancelled discovery job {} for user {}", jobId, userId);

                    var response = new JsonObject()
                        .put("success", true)
                        .put("message", "Discovery job cancelled successfully")
                        .put("jobId", jobId.toString())
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to cancel discovery job {} for user {}: {}", jobId, userId, throwable.getMessage(), throwable);

                    var errorMessage = "Failed to cancel discovery job";
                    if (throwable.getMessage() != null) {
                        errorMessage = throwable.getMessage();
                    }

                    var statusCode = 500;
                    if (throwable.getMessage() != null && throwable.getMessage().contains("not found")) {
                        statusCode = 404;
                    }
                    
                    sendErrorResponse(ctx, statusCode, "Internal Server Error", errorMessage);
                });
                
        } catch (Exception e) {
            logger.error("Error in handleCancelDiscovery", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Get user ID from context
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
     * Send error response
     */
    private void sendErrorResponse(RoutingContext ctx, int statusCode, String error, String message) {
        var errorResponse = new JsonObject()
            .put("success", false)
            .put("error", error)
            .put("message", message)
            .put("timestamp", System.currentTimeMillis());
        
        ctx.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(errorResponse.encode());
    }
    
    /**
     * Map DiscoveryJob to JSON
     */
    private JsonObject mapDiscoveryJobToJson(DiscoveryJob job) {
        var jobJson = new JsonObject()
            .put("id", job.getId().toString())
            .put("name", job.getName())
            .put("status", job.getStatus() != null ? job.getStatus().getValue() : null)
            .put("credentialProfileId", job.getCredentialProfileId() != null ? job.getCredentialProfileId().toString() : null)
            .put("targetRange", job.getTargetRange())
            .put("createdBy", job.getCreatedBy() != null ? job.getCreatedBy().toString() : null)
            .put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
        
        if (job.getStartedAt() != null) {
            jobJson.put("startedAt", job.getStartedAt().toString());
        }
        
        if (job.getCompletedAt() != null) {
            jobJson.put("completedAt", job.getCompletedAt().toString());
        }
        
        if (job.getResults() != null) {
            jobJson.put("results", job.getResults());
        }
        
        return jobJson;
    }
    
    /**
     * Map list of Devices to JSON array
     */
    private List<JsonObject> mapDevicesToJson(List<Device> devices) {
        return devices.stream()
            .map(this::mapDeviceToJson)
            .toList();
    }
    
    /**
     * Map Device to JSON
     */
    private JsonObject mapDeviceToJson(Device device) {
        var deviceJson = new JsonObject()
            .put("id", device.getId().toString())
            .put("hostname", device.getHostname())
            .put("ipAddress", device.getIpAddress())
            .put("deviceType", device.getDeviceType())
            .put("status", device.getStatus() != null ? device.getStatus().getValue() : null)
            .put("credentialProfileId", device.getCredentialProfileId() != null ? device.getCredentialProfileId().toString() : null)
            .put("createdAt", device.getCreatedAt() != null ? device.getCreatedAt().toString() : null)
            .put("updatedAt", device.getUpdatedAt() != null ? device.getUpdatedAt().toString() : null);
        
        if (device.getOsInfo() != null) {
            deviceJson.put("osInfo", device.getOsInfo());
        }
        
        if (device.getLastSeen() != null) {
            deviceJson.put("lastSeen", device.getLastSeen().toString());
        }
        
        return deviceJson;
    }
}
