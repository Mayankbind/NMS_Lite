package com.nms.handlers;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.models.DeviceDTO;
import com.nms.models.DeviceStatus;
import com.nms.services.DeviceService;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler for device management operations
 * Provides REST API endpoints for managing devices
 */
public class DeviceHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceHandler.class);
    
    private final DeviceService deviceService;
    
    public DeviceHandler(DeviceService deviceService) {
        this.deviceService = deviceService;
    }
    
    /**
     * Handle getting all devices
     */
    public Handler<RoutingContext> getAllDevices = this::handleGetAllDevices;
    
    /**
     * Handle getting a specific device by ID
     */
    public Handler<RoutingContext> getDeviceById = this::handleGetDeviceById;
    
    /**
     * Handle creating a new device
     */
    public Handler<RoutingContext> createDevice = this::handleCreateDevice;
    
    /**
     * Handle updating a device
     */
    public Handler<RoutingContext> updateDevice = this::handleUpdateDevice;
    
    /**
     * Handle deleting a device
     */
    public Handler<RoutingContext> deleteDevice = this::handleDeleteDevice;
    
    /**
     * Handle getting devices by status
     */
    public Handler<RoutingContext> getDevicesByStatus = this::handleGetDevicesByStatus;
    
    /**
     * Handle searching devices
     */
    public Handler<RoutingContext> searchDevices = this::handleSearchDevices;
    
    /**
     * Handle updating device status
     */
    public Handler<RoutingContext> updateDeviceStatus = this::handleUpdateDeviceStatus;
    
    /**
     * Handle getting all devices
     * GET /api/devices
     */
    private void handleGetAllDevices(RoutingContext ctx) {
        try {
            // Get user ID from context
            UUID userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get devices
            deviceService.getAllDevices(userId)
                .onSuccess(devices -> {
                    logger.info("Retrieved {} devices for user {}", devices.size(), userId);
                    
                    JsonObject response = new JsonObject()
                        .put("success", true)
                        .put("devices", devices)
                        .put("count", devices.size())
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to get devices for user {}: {}", userId, throwable.getMessage(), throwable);
                    sendErrorResponse(ctx, 500, "Internal Server Error", "Failed to retrieve devices");
                });
                
        } catch (Exception e) {
            logger.error("Error in handleGetAllDevices", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle getting a specific device by ID
     * GET /api/devices/{id}
     */
    private void handleGetDeviceById(RoutingContext ctx) {
        try {
            // Get user ID from context
            UUID userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get device ID from path parameter
            String deviceIdStr = ctx.pathParam("id");
            if (deviceIdStr == null || deviceIdStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Device ID is required");
                return;
            }
            
            // Parse device ID
            UUID deviceId;
            try {
                deviceId = UUID.fromString(deviceIdStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid device ID format");
                return;
            }
            
            // Get device
            deviceService.getDeviceById(deviceId, userId)
                .onSuccess(device -> {
                    logger.info("Retrieved device: {} for user {}", device.getHostname(), userId);
                    
                    JsonObject response = new JsonObject()
                        .put("success", true)
                        .put("device", device)
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to get device {} for user {}: {}", deviceId, userId, throwable.getMessage(), throwable);
                    
                    int statusCode = 500;
                    if (throwable.getMessage() != null && throwable.getMessage().contains("not found")) {
                        statusCode = 404;
                    }
                    
                    sendErrorResponse(ctx, statusCode, "Internal Server Error", throwable.getMessage());
                });
                
        } catch (Exception e) {
            logger.error("Error in handleGetDeviceById", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle creating a new device
     * POST /api/devices
     */
    private void handleCreateDevice(RoutingContext ctx) {
        try {
            // Get user ID from context
            UUID userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Parse request body
            JsonObject requestBody = ctx.body().asJsonObject();
            if (requestBody == null) {
                sendErrorResponse(ctx, 400, "Bad Request", "Request body is required");
                return;
            }
            
            // Create DeviceDTO from request
            DeviceDTO deviceDTO;
            try {
                deviceDTO = mapJsonToDeviceDTO(requestBody);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid credentialProfileId format");
                return;
            }
            
            // Validate required fields
            if (deviceDTO.getHostname() == null || deviceDTO.getHostname().trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Field 'hostname' is required");
                return;
            }
            
            if (deviceDTO.getIpAddress() == null || deviceDTO.getIpAddress().trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Field 'ipAddress' is required");
                return;
            }
            
            if (deviceDTO.getCredentialProfileId() == null) {
                sendErrorResponse(ctx, 400, "Bad Request", "Field 'credentialProfileId' is required");
                return;
            }
            
            // Create device
            deviceService.createDevice(deviceDTO, userId)
                .onSuccess(createdDevice -> {
                    logger.info("Created device: {} for user {}", createdDevice.getHostname(), userId);
                    
                    JsonObject response = new JsonObject()
                        .put("success", true)
                        .put("message", "Device created successfully")
                        .put("device", createdDevice)
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to create device for user {}: {}", userId, throwable.getMessage(), throwable);
                    
                    String errorMessage = "Failed to create device";
                    if (throwable.getMessage() != null) {
                        errorMessage = throwable.getMessage();
                    }
                    
                    int statusCode = 500;
                    if (throwable.getMessage() != null && throwable.getMessage().contains("not found")) {
                        statusCode = 404;
                    } else if (throwable.getMessage() != null && throwable.getMessage().contains("required")) {
                        statusCode = 400;
                    }
                    
                    sendErrorResponse(ctx, statusCode, "Internal Server Error", errorMessage);
                });
                
        } catch (Exception e) {
            logger.error("Error in handleCreateDevice", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle updating a device
     * PUT /api/devices/{id}
     */
    private void handleUpdateDevice(RoutingContext ctx) {
        try {
            // Get user ID from context
            UUID userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get device ID from path parameter
            String deviceIdStr = ctx.pathParam("id");
            if (deviceIdStr == null || deviceIdStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Device ID is required");
                return;
            }
            
            // Parse device ID
            UUID deviceId;
            try {
                deviceId = UUID.fromString(deviceIdStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid device ID format");
                return;
            }
            
            // Parse request body
            JsonObject requestBody = ctx.body().asJsonObject();
            if (requestBody == null) {
                sendErrorResponse(ctx, 400, "Bad Request", "Request body is required");
                return;
            }
            
            // Create DeviceDTO from request
            DeviceDTO deviceDTO;
            try {
                deviceDTO = mapJsonToDeviceDTO(requestBody);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid credentialProfileId format");
                return;
            }
            
            // Validate required fields
            if (deviceDTO.getHostname() == null || deviceDTO.getHostname().trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Field 'hostname' is required");
                return;
            }
            
            if (deviceDTO.getIpAddress() == null || deviceDTO.getIpAddress().trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Field 'ipAddress' is required");
                return;
            }
            
            // Update device
            deviceService.updateDevice(deviceId, deviceDTO, userId)
                .onSuccess(updatedDevice -> {
                    logger.info("Updated device: {} for user {}", updatedDevice.getHostname(), userId);
                    
                    JsonObject response = new JsonObject()
                        .put("success", true)
                        .put("message", "Device updated successfully")
                        .put("device", updatedDevice)
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to update device {} for user {}: {}", deviceId, userId, throwable.getMessage(), throwable);
                    
                    String errorMessage = "Failed to update device";
                    if (throwable.getMessage() != null) {
                        errorMessage = throwable.getMessage();
                    }
                    
                    int statusCode = 500;
                    if (throwable.getMessage() != null && throwable.getMessage().contains("not found")) {
                        statusCode = 404;
                    } else if (throwable.getMessage() != null && throwable.getMessage().contains("required")) {
                        statusCode = 400;
                    }
                    
                    sendErrorResponse(ctx, statusCode, "Internal Server Error", errorMessage);
                });
                
        } catch (Exception e) {
            logger.error("Error in handleUpdateDevice", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle deleting a device
     * DELETE /api/devices/{id}
     */
    private void handleDeleteDevice(RoutingContext ctx) {
        try {
            // Get user ID from context
            UUID userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get device ID from path parameter
            String deviceIdStr = ctx.pathParam("id");
            if (deviceIdStr == null || deviceIdStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Device ID is required");
                return;
            }
            
            // Parse device ID
            UUID deviceId;
            try {
                deviceId = UUID.fromString(deviceIdStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid device ID format");
                return;
            }
            
            // Delete device
            deviceService.deleteDevice(deviceId, userId)
                .onSuccess(v -> {
                    logger.info("Deleted device {} for user {}", deviceId, userId);
                    
                    JsonObject response = new JsonObject()
                        .put("success", true)
                        .put("message", "Device deleted successfully")
                        .put("deviceId", deviceId.toString())
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to delete device {} for user {}: {}", deviceId, userId, throwable.getMessage(), throwable);
                    
                    int statusCode = 500;
                    if (throwable.getMessage() != null && throwable.getMessage().contains("not found")) {
                        statusCode = 404;
                    }
                    
                    sendErrorResponse(ctx, statusCode, "Internal Server Error", throwable.getMessage());
                });
                
        } catch (Exception e) {
            logger.error("Error in handleDeleteDevice", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle getting devices by status
     * GET /api/devices/status/{status}
     */
    private void handleGetDevicesByStatus(RoutingContext ctx) {
        try {
            // Get user ID from context
            UUID userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get status from path parameter
            String statusStr = ctx.pathParam("status");
            if (statusStr == null || statusStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Status is required");
                return;
            }
            
            // Parse status
            DeviceStatus status = DeviceStatus.fromValue(statusStr.trim());
            if (status == null) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid status. Valid values: online, offline, unknown, error");
                return;
            }
            
            // Get devices by status
            deviceService.getDevicesByStatus(status, userId)
                .onSuccess(devices -> {
                    logger.info("Retrieved {} devices with status {} for user {}", devices.size(), status.getValue(), userId);
                    
                    JsonObject response = new JsonObject()
                        .put("success", true)
                        .put("devices", devices)
                        .put("count", devices.size())
                        .put("status", status.getValue())
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to get devices by status {} for user {}: {}", status.getValue(), userId, throwable.getMessage(), throwable);
                    sendErrorResponse(ctx, 500, "Internal Server Error", "Failed to retrieve devices");
                });
                
        } catch (Exception e) {
            logger.error("Error in handleGetDevicesByStatus", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle searching devices
     * GET /api/devices/search?q={query}
     */
    private void handleSearchDevices(RoutingContext ctx) {
        try {
            // Get user ID from context
            UUID userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get query parameter
            String query = ctx.request().getParam("q");
            if (query == null || query.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Query parameter 'q' is required");
                return;
            }
            
            // Search devices
            deviceService.searchDevices(query.trim(), userId)
                .onSuccess(devices -> {
                    logger.info("Found {} devices matching query '{}' for user {}", devices.size(), query, userId);
                    
                    JsonObject response = new JsonObject()
                        .put("success", true)
                        .put("devices", devices)
                        .put("count", devices.size())
                        .put("query", query)
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to search devices with query '{}' for user {}: {}", query, userId, throwable.getMessage(), throwable);
                    sendErrorResponse(ctx, 500, "Internal Server Error", "Failed to search devices");
                });
                
        } catch (Exception e) {
            logger.error("Error in handleSearchDevices", e);
            sendErrorResponse(ctx, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }
    
    /**
     * Handle updating device status
     * PUT /api/devices/{id}/status
     */
    private void handleUpdateDeviceStatus(RoutingContext ctx) {
        try {
            // Get user ID from context
            UUID userId = getUserIdFromContext(ctx);
            if (userId == null) {
                sendErrorResponse(ctx, 401, "Unauthorized", "User not authenticated");
                return;
            }
            
            // Get device ID from path parameter
            String deviceIdStr = ctx.pathParam("id");
            if (deviceIdStr == null || deviceIdStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Device ID is required");
                return;
            }
            
            // Parse device ID
            UUID deviceId;
            try {
                deviceId = UUID.fromString(deviceIdStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid device ID format");
                return;
            }
            
            // Parse request body
            JsonObject requestBody = ctx.body().asJsonObject();
            if (requestBody == null) {
                sendErrorResponse(ctx, 400, "Bad Request", "Request body is required");
                return;
            }
            
            // Get status from request body
            String statusStr = requestBody.getString("status");
            if (statusStr == null || statusStr.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "Bad Request", "Field 'status' is required");
                return;
            }
            
            // Parse status
            DeviceStatus status = DeviceStatus.fromValue(statusStr.trim());
            if (status == null) {
                sendErrorResponse(ctx, 400, "Bad Request", "Invalid status. Valid values: online, offline, unknown, error");
                return;
            }
            
            // Update device status
            deviceService.updateDeviceStatus(deviceId, status, userId)
                .onSuccess(v -> {
                    logger.info("Updated device {} status to {} for user {}", deviceId, status.getValue(), userId);
                    
                    JsonObject response = new JsonObject()
                        .put("success", true)
                        .put("message", "Device status updated successfully")
                        .put("deviceId", deviceId.toString())
                        .put("status", status.getValue())
                        .put("timestamp", System.currentTimeMillis());
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                })
                .onFailure(throwable -> {
                    logger.error("Failed to update device {} status for user {}: {}", deviceId, userId, throwable.getMessage(), throwable);
                    
                    int statusCode = 500;
                    if (throwable.getMessage() != null && throwable.getMessage().contains("not found")) {
                        statusCode = 404;
                    }
                    
                    sendErrorResponse(ctx, statusCode, "Internal Server Error", throwable.getMessage());
                });
                
        } catch (Exception e) {
            logger.error("Error in handleUpdateDeviceStatus", e);
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
                String userIdStr = userContext.getString("userId");
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
        JsonObject errorResponse = new JsonObject()
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
     * Map JSON request to DeviceDTO
     */
    private DeviceDTO mapJsonToDeviceDTO(JsonObject json) {
        DeviceDTO dto = new DeviceDTO();
        
        dto.setHostname(json.getString("hostname"));
        dto.setIpAddress(json.getString("ipAddress"));
        dto.setDeviceType(json.getString("deviceType"));
        dto.setOsInfo(json.getJsonObject("osInfo"));
        dto.setStatus(json.getString("status"));
        
        // Handle credential profile ID - this will be validated in the handler methods
        String credentialProfileIdStr = json.getString("credentialProfileId");
        if (credentialProfileIdStr != null && !credentialProfileIdStr.trim().isEmpty()) {
            dto.setCredentialProfileId(UUID.fromString(credentialProfileIdStr));
        }
        
        return dto;
    }
}
