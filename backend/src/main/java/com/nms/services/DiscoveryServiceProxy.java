package com.nms.services;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.models.Device;
import com.nms.models.DiscoveryJob;
import com.nms.models.DiscoveryJobDTO;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Proxy for DiscoveryService that communicates with DiscoveryWorkerVerticle via Event Bus
 * This allows the HTTP server to remain responsive while discovery operations run in worker threads
 */
public class DiscoveryServiceProxy implements IDiscoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServiceProxy.class);
    
    private final Vertx vertx;
    
    // Event Bus addresses
    private static final String DISCOVERY_START_ADDRESS = "discovery.start";
    private static final String DISCOVERY_STATUS_ADDRESS = "discovery.status";
    private static final String DISCOVERY_RESULTS_ADDRESS = "discovery.results";
    private static final String DISCOVERY_CANCEL_ADDRESS = "discovery.cancel";
    
    public DiscoveryServiceProxy(Vertx vertx) {
        this.vertx = vertx;
    }
    
    /**
     * Start a new discovery job
     * @param request the discovery job request
     * @param userId the user ID creating the job
     * @return Future containing the job ID
     */
    @Override
    public Future<UUID> startDiscovery(DiscoveryJobDTO request, UUID userId) {
        Promise<UUID> promise = Promise.promise();
        
        try {
            // Prepare request message
            JsonObject message = new JsonObject()
                .put("name", request.getName())
                .put("targetRange", request.getTargetRange())
                .put("credentialProfileId", request.getCredentialProfileId() != null ? request.getCredentialProfileId().toString() : null)
                .put("userId", userId.toString());
            
            // Send message to worker verticle via Event Bus
            vertx.eventBus().request(DISCOVERY_START_ADDRESS, message)
                .onSuccess(reply -> {
                    try {
                        JsonObject response = (JsonObject) reply.body();
                        if (response.getBoolean("success", false)) {
                            UUID jobId = UUID.fromString(response.getString("jobId"));
                            logger.debug("Discovery proxy: Started discovery job {} for user {}", jobId, userId);
                            promise.complete(jobId);
                        } else {
                            String error = response.getString("error", "Unknown error");
                            logger.error("Discovery proxy: Failed to start discovery job: {}", error);
                            promise.fail(new RuntimeException(error));
                        }
                    } catch (Exception e) {
                        logger.error("Discovery proxy: Error parsing start discovery response", e);
                        promise.fail(e);
                    }
                })
                .onFailure(throwable -> {
                    logger.error("Discovery proxy: Event Bus request failed for start discovery", throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.error("Discovery proxy: Error preparing start discovery request", e);
            promise.fail(e);
        }
        
        return promise.future();
    }
    
    /**
     * Get discovery job status
     * @param jobId the job ID
     * @param userId the user ID
     * @return Future containing the discovery job
     */
    @Override
    public Future<DiscoveryJob> getDiscoveryStatus(UUID jobId, UUID userId) {
        Promise<DiscoveryJob> promise = Promise.promise();
        
        try {
            // Prepare request message
            JsonObject message = new JsonObject()
                .put("jobId", jobId.toString())
                .put("userId", userId.toString());
            
            // Send message to worker verticle via Event Bus
            vertx.eventBus().request(DISCOVERY_STATUS_ADDRESS, message)
                .onSuccess(reply -> {
                    try {
                        JsonObject response = (JsonObject) reply.body();
                        if (response.getBoolean("success", false)) {
                            JsonObject jobJson = response.getJsonObject("job");
                            DiscoveryJob job = mapJsonToDiscoveryJob(jobJson);
                            logger.debug("Discovery proxy: Retrieved discovery job status for job {} and user {}", jobId, userId);
                            promise.complete(job);
                        } else {
                            String error = response.getString("error", "Unknown error");
                            logger.error("Discovery proxy: Failed to get discovery status: {}", error);
                            promise.fail(new RuntimeException(error));
                        }
                    } catch (Exception e) {
                        logger.error("Discovery proxy: Error parsing get status response", e);
                        promise.fail(e);
                    }
                })
                .onFailure(throwable -> {
                    logger.error("Discovery proxy: Event Bus request failed for get status", throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.error("Discovery proxy: Error preparing get status request", e);
            promise.fail(e);
        }
        
        return promise.future();
    }
    
    /**
     * Get discovery results (discovered devices)
     * @param jobId the job ID
     * @param userId the user ID
     * @return Future containing list of discovered devices
     */
    @Override
    public Future<List<Device>> getDiscoveryResults(UUID jobId, UUID userId) {
        Promise<List<Device>> promise = Promise.promise();
        
        try {
            // Prepare request message
            JsonObject message = new JsonObject()
                .put("jobId", jobId.toString())
                .put("userId", userId.toString());
            
            // Send message to worker verticle via Event Bus
            vertx.eventBus().request(DISCOVERY_RESULTS_ADDRESS, message)
                .onSuccess(reply -> {
                    try {
                        JsonObject response = (JsonObject) reply.body();
                        if (response.getBoolean("success", false)) {
                            List<Device> devices = mapJsonArrayToDevices(response.getJsonArray("devices"));
                            logger.debug("Discovery proxy: Retrieved discovery results for job {} and user {}: {} devices", 
                                jobId, userId, devices.size());
                            promise.complete(devices);
                        } else {
                            String error = response.getString("error", "Unknown error");
                            logger.error("Discovery proxy: Failed to get discovery results: {}", error);
                            promise.fail(new RuntimeException(error));
                        }
                    } catch (Exception e) {
                        logger.error("Discovery proxy: Error parsing get results response", e);
                        promise.fail(e);
                    }
                })
                .onFailure(throwable -> {
                    logger.error("Discovery proxy: Event Bus request failed for get results", throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.error("Discovery proxy: Error preparing get results request", e);
            promise.fail(e);
        }
        
        return promise.future();
    }
    
    /**
     * Cancel a discovery job
     * @param jobId the job ID
     * @param userId the user ID
     * @return Future indicating success
     */
    @Override
    public Future<Void> cancelDiscovery(UUID jobId, UUID userId) {
        Promise<Void> promise = Promise.promise();
        
        try {
            // Prepare request message
            JsonObject message = new JsonObject()
                .put("jobId", jobId.toString())
                .put("userId", userId.toString());
            
            // Send message to worker verticle via Event Bus
            vertx.eventBus().request(DISCOVERY_CANCEL_ADDRESS, message)
                .onSuccess(reply -> {
                    try {
                        JsonObject response = (JsonObject) reply.body();
                        if (response.getBoolean("success", false)) {
                            logger.info("Discovery proxy: Cancelled discovery job {} for user {}", jobId, userId);
                            promise.complete();
                        } else {
                            String error = response.getString("error", "Unknown error");
                            logger.error("Discovery proxy: Failed to cancel discovery job: {}", error);
                            promise.fail(new RuntimeException(error));
                        }
                    } catch (Exception e) {
                        logger.error("Discovery proxy: Error parsing cancel response", e);
                        promise.fail(e);
                    }
                })
                .onFailure(throwable -> {
                    logger.error("Discovery proxy: Event Bus request failed for cancel", throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.error("Discovery proxy: Error preparing cancel request", e);
            promise.fail(e);
        }
        
        return promise.future();
    }
    
    /**
     * Map JSON to DiscoveryJob
     */
    private DiscoveryJob mapJsonToDiscoveryJob(JsonObject jobJson) {
        DiscoveryJob job = new DiscoveryJob();
        job.setId(UUID.fromString(jobJson.getString("id")));
        job.setName(jobJson.getString("name"));
        
        String status = jobJson.getString("status");
        if (status != null) {
            job.setStatus(com.nms.models.DiscoveryJobStatus.fromValue(status));
        }
        
        String credentialProfileId = jobJson.getString("credentialProfileId");
        if (credentialProfileId != null) {
            job.setCredentialProfileId(UUID.fromString(credentialProfileId));
        }
        
        job.setTargetRange(jobJson.getString("targetRange"));
        
        String createdBy = jobJson.getString("createdBy");
        if (createdBy != null) {
            job.setCreatedBy(UUID.fromString(createdBy));
        }
        
        String createdAt = jobJson.getString("createdAt");
        if (createdAt != null) {
            job.setCreatedAt(java.time.LocalDateTime.parse(createdAt));
        }
        
        String startedAt = jobJson.getString("startedAt");
        if (startedAt != null) {
            job.setStartedAt(java.time.LocalDateTime.parse(startedAt));
        }
        
        String completedAt = jobJson.getString("completedAt");
        if (completedAt != null) {
            job.setCompletedAt(java.time.LocalDateTime.parse(completedAt));
        }
        
        JsonObject results = jobJson.getJsonObject("results");
        if (results != null) {
            job.setResults(results);
        }
        
        return job;
    }
    
    /**
     * Map JSON array to list of Devices
     */
    private List<Device> mapJsonArrayToDevices(io.vertx.core.json.JsonArray devicesArray) {
        return devicesArray.stream()
            .map(item -> {
                JsonObject deviceJson = (JsonObject) item;
                Device device = new Device();
                device.setId(UUID.fromString(deviceJson.getString("id")));
                device.setHostname(deviceJson.getString("hostname"));
                device.setIpAddress(deviceJson.getString("ipAddress"));
                device.setDeviceType(deviceJson.getString("deviceType"));
                
                String status = deviceJson.getString("status");
                if (status != null) {
                    device.setStatus(com.nms.models.DeviceStatus.fromValue(status));
                }
                
                String credentialProfileId = deviceJson.getString("credentialProfileId");
                if (credentialProfileId != null) {
                    device.setCredentialProfileId(UUID.fromString(credentialProfileId));
                }
                
                String createdAt = deviceJson.getString("createdAt");
                if (createdAt != null) {
                    device.setCreatedAt(java.time.LocalDateTime.parse(createdAt));
                }
                
                String updatedAt = deviceJson.getString("updatedAt");
                if (updatedAt != null) {
                    device.setUpdatedAt(java.time.LocalDateTime.parse(updatedAt));
                }
                
                String lastSeen = deviceJson.getString("lastSeen");
                if (lastSeen != null) {
                    device.setLastSeen(java.time.LocalDateTime.parse(lastSeen));
                }
                
                JsonObject osInfo = deviceJson.getJsonObject("osInfo");
                if (osInfo != null) {
                    device.setOsInfo(osInfo);
                }
                
                return device;
            })
            .toList();
    }
}

