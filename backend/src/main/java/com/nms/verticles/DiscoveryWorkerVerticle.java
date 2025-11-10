package com.nms.verticles;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.config.ApplicationConfig;
import com.nms.config.DatabaseConfig;
import com.nms.models.Device;
import com.nms.models.DiscoveryJob;
import com.nms.models.DiscoveryJobDTO;
import com.nms.services.DiscoveryService;
import com.nms.utils.EncryptionUtils;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;

/**
 * Worker Verticle for Discovery Service
 * Handles CPU/IO intensive discovery operations (network scanning, SSH connections)
 * Runs in a dedicated worker thread pool to avoid blocking the event loop
 */
public class DiscoveryWorkerVerticle extends AbstractVerticle {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryWorkerVerticle.class);
    
    private DiscoveryService discoveryService;
    private EncryptionUtils encryptionUtils;
    
    // Event Bus addresses
    private static final String DISCOVERY_START_ADDRESS = "discovery.start";
    private static final String DISCOVERY_STATUS_ADDRESS = "discovery.status";
    private static final String DISCOVERY_RESULTS_ADDRESS = "discovery.results";
    private static final String DISCOVERY_CANCEL_ADDRESS = "discovery.cancel";
    
    @Override
    public void start(Promise<Void> startPromise) {
        logger.info("Starting Discovery Worker Verticle...");
        
        // Load configuration
        ConfigRetriever.create(vertx)
            .getConfig()
            .compose(config -> {
                logger.info("Discovery Worker: Configuration loaded successfully");
                
                // Initialize application configuration
                var appConfig = new ApplicationConfig(config);
                
                // Initialize database connection pool
                return initializeDatabase(appConfig);
            })
            .compose(pgPool -> {
                // Initialize encryption utility
                var encryptionKey = config().getString("encryption.key", "default-encryption-key-change-in-production");
                this.encryptionUtils = new EncryptionUtils(encryptionKey);
                
                // Initialize discovery service with the database connection pool
                this.discoveryService = new DiscoveryService(pgPool, vertx, encryptionUtils);
                
                // Register Event Bus consumers
                registerEventBusConsumers();
                
                logger.info("Discovery Worker Verticle started successfully");
                startPromise.complete();
                return Future.succeededFuture();
            })
            .onFailure(throwable -> {
                logger.error("Failed to start Discovery Worker Verticle", throwable);
                startPromise.fail(throwable);
            });
    }
    
    /**
     * Initialize database connection pool
     */
    private Future<PgPool> initializeDatabase(ApplicationConfig appConfig) {
        try {
            var dbConfig = new DatabaseConfig(appConfig.getConfig());
            var pgPool = dbConfig.createPgPool(vertx);
            
            // Test database connection
            return pgPool.query("SELECT 1")
                .execute()
                .map(result -> {
                    logger.info("Discovery Worker: Database connection established successfully");
                    return pgPool;
                });
                
        } catch (Exception e) {
            logger.error("Discovery Worker: Failed to initialize database configuration", e);
            return Future.failedFuture(e);
        }
    }
    
    /**
     * Register Event Bus consumers for discovery operations
     */
    private void registerEventBusConsumers() {
        // Consumer for starting discovery jobs
        vertx.eventBus().consumer(DISCOVERY_START_ADDRESS, message -> {
            try {
                var requestJson = (JsonObject) message.body();
                logger.debug("Discovery Worker: Received start discovery request: {}", requestJson);
                
                // Parse request
                var request = parseDiscoveryRequest(requestJson);
                var userId = UUID.fromString(requestJson.getString("userId"));
                
                // Start discovery
                discoveryService.startDiscovery(request, userId)
                    .onSuccess(jobId -> {
                        logger.info("Discovery Worker: Started discovery job {} for user {}", jobId, userId);
                        var response = new JsonObject()
                            .put("success", true)
                            .put("jobId", jobId.toString());
                        message.reply(response);
                    })
                    .onFailure(throwable -> {
                        logger.error("Discovery Worker: Failed to start discovery job: {}", throwable.getMessage(), throwable);
                        var errorResponse = new JsonObject()
                            .put("success", false)
                            .put("error", throwable.getMessage());
                        message.fail(500, errorResponse.encode());
                    });
                    
            } catch (Exception e) {
                logger.error("Discovery Worker: Error processing start discovery request", e);
                var errorResponse = new JsonObject()
                    .put("success", false)
                    .put("error", e.getMessage());
                message.fail(500, errorResponse.encode());
            }
        });
        
        // Consumer for getting discovery status
        vertx.eventBus().consumer(DISCOVERY_STATUS_ADDRESS, message -> {
            try {
                var requestJson = (JsonObject) message.body();
                var jobId = UUID.fromString(requestJson.getString("jobId"));
                var userId = UUID.fromString(requestJson.getString("userId"));
                
                logger.debug("Discovery Worker: Received get status request for job {} and user {}", jobId, userId);
                
                discoveryService.getDiscoveryStatus(jobId, userId)
                    .onSuccess(job -> {
                        var response = new JsonObject()
                            .put("success", true)
                            .put("job", mapDiscoveryJobToJson(job));
                        message.reply(response);
                    })
                    .onFailure(throwable -> {
                        logger.error("Discovery Worker: Failed to get discovery status: {}", throwable.getMessage(), throwable);
                        var errorResponse = new JsonObject()
                            .put("success", false)
                            .put("error", throwable.getMessage());
                        message.fail(500, errorResponse.encode());
                    });
                    
            } catch (Exception e) {
                logger.error("Discovery Worker: Error processing get status request", e);
                var errorResponse = new JsonObject()
                    .put("success", false)
                    .put("error", e.getMessage());
                message.fail(500, errorResponse.encode());
            }
        });
        
        // Consumer for getting discovery results
        vertx.eventBus().consumer(DISCOVERY_RESULTS_ADDRESS, message -> {
            try {
                var requestJson = (JsonObject) message.body();
                var jobId = UUID.fromString(requestJson.getString("jobId"));
                var userId = UUID.fromString(requestJson.getString("userId"));
                
                logger.debug("Discovery Worker: Received get results request for job {} and user {}", jobId, userId);
                
                discoveryService.getDiscoveryResults(jobId, userId)
                    .onSuccess(devices -> {
                        var response = new JsonObject()
                            .put("success", true)
                            .put("devices", mapDevicesToJson(devices))
                            .put("count", devices.size());
                        message.reply(response);
                    })
                    .onFailure(throwable -> {
                        logger.error("Discovery Worker: Failed to get discovery results: {}", throwable.getMessage(), throwable);
                        var errorResponse = new JsonObject()
                            .put("success", false)
                            .put("error", throwable.getMessage());
                        message.fail(500, errorResponse.encode());
                    });
                    
            } catch (Exception e) {
                logger.error("Discovery Worker: Error processing get results request", e);
                var errorResponse = new JsonObject()
                    .put("success", false)
                    .put("error", e.getMessage());
                message.fail(500, errorResponse.encode());
            }
        });
        
        // Consumer for cancelling discovery jobs
        vertx.eventBus().consumer(DISCOVERY_CANCEL_ADDRESS, message -> {
            try {
                var requestJson = (JsonObject) message.body();
                var jobId = UUID.fromString(requestJson.getString("jobId"));
                var userId = UUID.fromString(requestJson.getString("userId"));
                
                logger.info("Discovery Worker: Received cancel request for job {} and user {}", jobId, userId);
                
                discoveryService.cancelDiscovery(jobId, userId)
                    .onSuccess(v -> {
                        logger.info("Discovery Worker: Cancelled discovery job {} for user {}", jobId, userId);
                        var response = new JsonObject()
                            .put("success", true)
                            .put("message", "Discovery job cancelled successfully");
                        message.reply(response);
                    })
                    .onFailure(throwable -> {
                        logger.error("Discovery Worker: Failed to cancel discovery job: {}", throwable.getMessage(), throwable);
                        var errorResponse = new JsonObject()
                            .put("success", false)
                            .put("error", throwable.getMessage());
                        message.fail(500, errorResponse.encode());
                    });
                    
            } catch (Exception e) {
                logger.error("Discovery Worker: Error processing cancel request", e);
                var errorResponse = new JsonObject()
                    .put("success", false)
                    .put("error", e.getMessage());
                message.fail(500, errorResponse.encode());
            }
        });
        
        logger.info("Discovery Worker: Event Bus consumers registered");
    }
    
    /**
     * Parse discovery request from JSON
     */
    private DiscoveryJobDTO parseDiscoveryRequest(JsonObject requestJson) {
        var request = new DiscoveryJobDTO();
        request.setName(requestJson.getString("name"));
        request.setTargetRange(requestJson.getString("targetRange"));

        var credentialProfileIdStr = requestJson.getString("credentialProfileId");
        if (credentialProfileIdStr != null) {
            request.setCredentialProfileId(UUID.fromString(credentialProfileIdStr));
        }
        
        return request;
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
            .map(device -> {
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
            })
            .toList();
    }
}

