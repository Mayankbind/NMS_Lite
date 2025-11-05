package com.nms.services;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.models.Device;
import com.nms.models.DeviceStatus;
import com.nms.models.DiscoveryJob;
import com.nms.models.DiscoveryJobDTO;
import com.nms.models.DiscoveryJobStatus;
import com.nms.utils.EncryptionUtils;
import com.nms.utils.NetworkUtils;
import com.nms.utils.SshUtils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/**
 * Service for managing device discovery operations
 * Handles discovery job creation, execution, and result management
 */
public class DiscoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryService.class);
    
    private final PgPool dbPool;
    private final Vertx vertx;
    private final EncryptionUtils encryptionUtils;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    // Discovery configuration
    private static final int DEFAULT_SSH_TIMEOUT = 5000; // 5 seconds
    private static final int DEFAULT_PING_TIMEOUT = 1000; // 1 second
    
    public DiscoveryService(PgPool dbPool, Vertx vertx, EncryptionUtils encryptionUtils) {
        this.dbPool = dbPool;
        this.vertx = vertx;
        this.encryptionUtils = encryptionUtils;
    }
    
    /**
     * Start a new discovery job
     * @param request the discovery job request
     * @param userId the user ID creating the job
     * @return Future containing the job ID
     */
    public Future<UUID> startDiscovery(DiscoveryJobDTO request, UUID userId) {
        Promise<UUID> promise = Promise.promise();
        
        try {
            // Validate input
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Discovery job name is required"));
                return promise.future();
            }
            
            if (request.getTargetRange() == null || request.getTargetRange().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Target range is required"));
                return promise.future();
            }
            
            if (request.getCredentialProfileId() == null) {
                promise.fail(new IllegalArgumentException("Credential profile ID is required"));
                return promise.future();
            }
            
            // Validate CIDR format
            if (!NetworkUtils.isValidCidr(request.getTargetRange())) {
                promise.fail(new IllegalArgumentException("Invalid CIDR format: " + request.getTargetRange()));
                return promise.future();
            }
            
            // Validate credential profile exists and user has access
            validateCredentialProfileAccess(request.getCredentialProfileId(), userId)
                .compose(valid -> {
                    if (!valid) {
                        return Future.failedFuture(new IllegalArgumentException("Credential profile not found or access denied"));
                    }
                    
                    // Create discovery job
                    return createDiscoveryJob(request, userId);
                })
                .compose(jobId -> {
                    // Start background discovery process
                    startBackgroundDiscovery(jobId, request.getTargetRange(), request.getCredentialProfileId());
                    promise.complete(jobId);
                    return Future.succeededFuture();
                })
                .onFailure(throwable -> {
                    logger.error("Failed to start discovery: {}", throwable.getMessage(), throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.error("Error starting discovery", e);
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
    public Future<DiscoveryJob> getDiscoveryStatus(UUID jobId, UUID userId) {
        Promise<DiscoveryJob> promise = Promise.promise();
        
        String sql = """
            SELECT id, name, status, credential_profile_id, target_range, results, 
                   started_at, completed_at, created_by, created_at
            FROM discovery_jobs 
            WHERE id = $1 AND created_by = $2
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(jobId, userId))
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    DiscoveryJob job = mapRowToDiscoveryJob(row);
                    logger.debug("Found discovery job {} for user {}", jobId, userId);
                    promise.complete(job);
                } else {
                    logger.warn("Discovery job {} not found for user {}", jobId, userId);
                    promise.fail(new RuntimeException("Discovery job not found or access denied"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to get discovery status: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Get discovery results (discovered devices)
     * @param jobId the job ID
     * @param userId the user ID
     * @return Future containing list of discovered devices
     */
    public Future<List<Device>> getDiscoveryResults(UUID jobId, UUID userId) {
        Promise<List<Device>> promise = Promise.promise();
        
        // First verify the job exists and user has access
        getDiscoveryStatus(jobId, userId)
            .compose(job -> {
                // Get devices discovered by this job
                String sql = """
                    SELECT d.id, d.hostname, d.ip_address, d.device_type, d.os_info, 
                           d.credential_profile_id, d.status, d.last_seen, d.created_at, d.updated_at
                    FROM devices d
                    WHERE d.credential_profile_id = $1
                    ORDER BY d.created_at DESC
                    """;
                
                return dbPool.preparedQuery(sql)
                    .execute(Tuple.of(job.getCredentialProfileId()));
            })
            .onSuccess(rows -> {
                List<Device> devices = new ArrayList<>();
                for (Row row : rows) {
                    devices.add(mapRowToDevice(row));
                }
                promise.complete(devices);
            })
            .onFailure(throwable -> {
                logger.error("Failed to get discovery results: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Cancel a discovery job
     * @param jobId the job ID
     * @param userId the user ID
     * @return Future indicating success
     */
    public Future<Void> cancelDiscovery(UUID jobId, UUID userId) {
        Promise<Void> promise = Promise.promise();
        
        String sql = """
            UPDATE discovery_jobs 
            SET status = 'failed', completed_at = CURRENT_TIMESTAMP,
                results = COALESCE(results, '{}'::jsonb) || '{"cancelled": true, "cancelled_at": "' || CURRENT_TIMESTAMP || '"}'::jsonb
            WHERE id = $1 AND created_by = $2 AND status IN ('pending', 'running')
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(jobId, userId))
            .onSuccess(rows -> {
                if (rows.rowCount() > 0) {
                    logger.info("Cancelled discovery job: {}", jobId);
                    promise.complete();
                } else {
                    promise.fail(new RuntimeException("Discovery job not found, access denied, or cannot be cancelled"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to cancel discovery: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Validate that user has access to credential profile
     */
    private Future<Boolean> validateCredentialProfileAccess(UUID credentialProfileId, UUID userId) {
        Promise<Boolean> promise = Promise.promise();
        
        String sql = "SELECT id FROM credential_profiles WHERE id = $1 AND created_by = $2";
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(credentialProfileId, userId))
            .onSuccess(rows -> {
                promise.complete(rows.iterator().hasNext());
            })
            .onFailure(throwable -> {
                logger.error("Error validating credential profile access: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Create discovery job in database
     */
    private Future<UUID> createDiscoveryJob(DiscoveryJobDTO request, UUID userId) {
        Promise<UUID> promise = Promise.promise();
        
        String sql = """
            INSERT INTO discovery_jobs (name, status, credential_profile_id, target_range, created_by)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING id
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(
                request.getName().trim(),
                DiscoveryJobStatus.PENDING.getValue(),
                request.getCredentialProfileId(),
                request.getTargetRange().trim(),
                userId
            ))
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    UUID jobId = rows.iterator().next().getUUID("id");
                    logger.info("Created discovery job: {} for user: {}", jobId, userId);
                    promise.complete(jobId);
                } else {
                    promise.fail(new RuntimeException("Failed to create discovery job"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to create discovery job: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Start background discovery process
     */
    private void startBackgroundDiscovery(UUID jobId, String targetRange, UUID credentialProfileId) {
        // Update job status to running (async)
        updateJobStatusAsync(jobId, DiscoveryJobStatus.RUNNING, LocalDateTime.now(), null)
            .compose(v -> getCredentialProfileDetailsAsync(credentialProfileId))
            .compose(credentials -> {
                if (credentials == null) {
                    return Future.failedFuture(new RuntimeException("Credential profile not found"));
                }
                
                // Perform network scan (this is the only blocking operation)
                return vertx.executeBlocking(() -> {
                    List<String> reachableIps = NetworkUtils.pingSweepCidr(targetRange, DEFAULT_PING_TIMEOUT);
                    logger.info("Discovery job {} found {} reachable IPs", jobId, reachableIps.size());
                    return reachableIps;
                }, true); // Use ordered execution to prevent thread blocking warnings
            })
            .compose(reachableIps -> {
                // Process each IP asynchronously
                List<Future<Device>> deviceFutures = new ArrayList<>();
                
                for (String ip : reachableIps) {
                    Future<Device> deviceFuture = processDeviceAsync(ip, credentialProfileId)
                        .recover(throwable -> {
                            logger.warn("Error processing IP {}: {}", ip, throwable.getMessage());
                            return Future.succeededFuture(null); // Return null for failed devices
                        });
                    deviceFutures.add(deviceFuture);
                }
                
                // Wait for all device processing to complete
                return Future.all(deviceFutures)
                    .map(results -> {
                        List<Device> discoveredDevices = new ArrayList<>();
                        for (Future<Device> future : deviceFutures) {
                            if (future.succeeded() && future.result() != null) {
                                discoveredDevices.add(future.result());
                            }
                        }
                        // Return both the discovered devices and the total IPs scanned
                        JsonObject result = new JsonObject();
                        result.put("discoveredDevices", discoveredDevices);
                        result.put("totalIpsScanned", reachableIps.size());
                        return result;
                    });
            })
            .compose(result -> {
                @SuppressWarnings("unchecked")
                List<Device> discoveredDevices = result.getJsonArray("discoveredDevices").getList();
                int totalIpsScanned = result.getInteger("totalIpsScanned");
                
                // Update results
                JsonObject results = new JsonObject();
                results.put("totalIpsScanned", totalIpsScanned);
                results.put("devicesDiscovered", discoveredDevices.size());
                results.put("devices", discoveredDevices.stream().map(Device::getHostname).toList());
                
                // Update job status to completed and results
                return updateJobResultsAsync(jobId, results)
                    .compose(v -> updateJobStatusAsync(jobId, DiscoveryJobStatus.COMPLETED, null, LocalDateTime.now()))
                    .map(v -> {
                        logger.info("Discovery job {} completed: {} devices discovered", jobId, discoveredDevices.size());
                        return discoveredDevices;
                    });
            })
            .onFailure(throwable -> {
                logger.error("Discovery job {} failed: {}", jobId, throwable.getMessage(), throwable);
                
                // Update job status to failed
                JsonObject errorResults = new JsonObject();
                errorResults.put("error", throwable.getMessage());
                errorResults.put("failedAt", LocalDateTime.now().format(dateTimeFormatter));
                
                updateJobResultsAsync(jobId, errorResults)
                    .compose(v -> updateJobStatusAsync(jobId, DiscoveryJobStatus.FAILED, null, LocalDateTime.now()))
                    .onFailure(error -> logger.error("Failed to update job status to failed: {}", error.getMessage()));
            });
    }
    
    /**
     * Process a single device asynchronously
     */
    private Future<Device> processDeviceAsync(String ip, UUID credentialProfileId) {
        return getCredentialProfileDetailsAsync(credentialProfileId)
            .compose(credentials -> {
                if (credentials == null) {
                    return Future.failedFuture(new RuntimeException("Credential profile not found"));
                }
                
                // Test SSH connection (blocking operation)
                return vertx.executeBlocking(() -> {
                    boolean sshConnected = SshUtils.testConnection(
                        ip, 
                        credentials.getString("username"),
                        credentials.getString("password"),
                        credentials.getInteger("port", 22),
                        DEFAULT_SSH_TIMEOUT
                    );
                    
                    if (sshConnected) {
                        // Gather device information (blocking operation)
                        JsonObject deviceInfo = SshUtils.gatherDeviceInfo(
                            ip,
                            credentials.getString("username"),
                            credentials.getString("password"),
                            credentials.getInteger("port", 22),
                            DEFAULT_SSH_TIMEOUT
                        );
                        
                        // Create device record
                        Device device = createDeviceFromDiscovery(ip, deviceInfo, credentialProfileId);
                        logger.info("Discovered device: {} ({})", device.getHostname(), ip);
                        return device;
                    }
                    
                    return null; // Device not accessible
                }, false);
            })
            .compose(device -> {
                if (device != null) {
                    // Store device in database asynchronously
                    return storeDiscoveredDeviceAsync(device)
                        .map(v -> device);
                }
                return Future.succeededFuture(null);
            });
    }
    
    /**
     * Get credential profile details for discovery (async)
     */
    private Future<JsonObject> getCredentialProfileDetailsAsync(UUID credentialProfileId) {
        Promise<JsonObject> promise = Promise.promise();
        
        String sql = """
            SELECT username, password_encrypted, port 
            FROM credential_profiles 
            WHERE id = $1
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(credentialProfileId))
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    try {
                        // Decrypt the password
                        String encryptedPassword = row.getString("password_encrypted");
                        String decryptedPassword = encryptionUtils.decrypt(encryptedPassword);
                        
                        JsonObject credentials = new JsonObject();
                        credentials.put("username", row.getString("username"));
                        credentials.put("password", decryptedPassword); // Now using decrypted password
                        credentials.put("port", row.getInteger("port"));
//                        System.out.println("This is CREDENTIAL::::::"+credentials.encodePrettily());
                        promise.complete(credentials);
                    } catch (Exception e) {
                        logger.error("Error decrypting password for credential profile {}: {}", credentialProfileId, e.getMessage(), e);
                        promise.fail(e);
                    }
                } else {
                    promise.complete(null);
                }
            })
            .onFailure(throwable -> {
                logger.error("Error getting credential profile details: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Create device from discovery information
     */
    private Device createDeviceFromDiscovery(String ip, JsonObject deviceInfo, UUID credentialProfileId) {
        Device device = new Device();
        device.setHostname(deviceInfo.getString("hostname", "unknown"));
        device.setIpAddress(ip);
        device.setDeviceType(deviceInfo.getString("deviceType", "unknown"));
        device.setOsInfo(deviceInfo);
        device.setCredentialProfileId(credentialProfileId);
        device.setStatus(DeviceStatus.ONLINE);
        device.setLastSeen(LocalDateTime.now());
        device.setCreatedAt(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        return device;
    }
    
    /**
     * Store discovered device in database (async)
     */
    private Future<Void> storeDiscoveredDeviceAsync(Device device) {
        Promise<Void> promise = Promise.promise();
        
        String sql = """
            INSERT INTO devices (hostname, ip_address, device_type, os_info, credential_profile_id, status, last_seen, created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(
                device.getHostname(),
                device.getIpAddress(),
                device.getDeviceType(),
                device.getOsInfo(),
                device.getCredentialProfileId(),
                device.getStatus().getValue(),
                device.getLastSeen(),
                device.getCreatedAt(),
                device.getUpdatedAt()
            ))
            .onSuccess(rows -> {
                logger.debug("Stored discovered device: {}", device.getHostname());
                promise.complete();
            })
            .onFailure(throwable -> {
                logger.error("Error storing discovered device {}: {}", device.getHostname(), throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Update job status (async)
     */
    private Future<Void> updateJobStatusAsync(UUID jobId, DiscoveryJobStatus status, LocalDateTime startedAt, LocalDateTime completedAt) {
        Promise<Void> promise = Promise.promise();
        
        String sql;
        Tuple params;
        
        if (startedAt != null && completedAt == null) {
            // Starting job
            sql = "UPDATE discovery_jobs SET status = $1, started_at = $2 WHERE id = $3";
            params = Tuple.of(status.getValue(), startedAt, jobId);
        } else if (completedAt != null) {
            // Completing job
            sql = "UPDATE discovery_jobs SET status = $1, completed_at = $2 WHERE id = $3";
            params = Tuple.of(status.getValue(), completedAt, jobId);
        } else {
            // Just updating status
            sql = "UPDATE discovery_jobs SET status = $1 WHERE id = $2";
            params = Tuple.of(status.getValue(), jobId);
        }
        
        dbPool.preparedQuery(sql)
            .execute(params)
            .onSuccess(rows -> {
                logger.debug("Updated job {} status to {}", jobId, status.getValue());
                promise.complete();
            })
            .onFailure(throwable -> {
                logger.error("Error updating job status: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Update job results (async)
     */
    private Future<Void> updateJobResultsAsync(UUID jobId, JsonObject results) {
        Promise<Void> promise = Promise.promise();
        
        String sql = "UPDATE discovery_jobs SET results = $1 WHERE id = $2";
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(results, jobId))
            .onSuccess(rows -> {
                logger.debug("Updated job {} results", jobId);
                promise.complete();
            })
            .onFailure(throwable -> {
                logger.error("Error updating job results: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Map database row to DiscoveryJob
     */
    private DiscoveryJob mapRowToDiscoveryJob(Row row) {
        DiscoveryJob job = new DiscoveryJob();
        job.setId(row.getUUID("id"));
        job.setName(row.getString("name"));
        job.setStatus(DiscoveryJobStatus.fromValue(row.getString("status")));
        job.setCredentialProfileId(row.getUUID("credential_profile_id"));
        job.setTargetRange(row.getString("target_range"));
        job.setResults(row.getJsonObject("results"));
        job.setStartedAt(row.getLocalDateTime("started_at"));
        job.setCompletedAt(row.getLocalDateTime("completed_at"));
        job.setCreatedBy(row.getUUID("created_by"));
        job.setCreatedAt(row.getLocalDateTime("created_at"));
        return job;
    }
    
    /**
     * Map database row to Device
     */
    private Device mapRowToDevice(Row row) {
        Device device = new Device();
        device.setId(row.getUUID("id"));
        device.setHostname(row.getString("hostname"));
        device.setIpAddress(row.getString("ip_address"));
        device.setDeviceType(row.getString("device_type"));
        device.setOsInfo(row.getJsonObject("os_info"));
        device.setCredentialProfileId(row.getUUID("credential_profile_id"));
        device.setStatus(DeviceStatus.fromValue(row.getString("status")));
        device.setLastSeen(row.getLocalDateTime("last_seen"));
        device.setCreatedAt(row.getLocalDateTime("created_at"));
        device.setUpdatedAt(row.getLocalDateTime("updated_at"));
        return device;
    }
}
