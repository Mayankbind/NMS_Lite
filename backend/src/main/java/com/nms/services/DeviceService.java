package com.nms.services;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.models.DeviceDTO;
import com.nms.models.DeviceStatus;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/**
 * Service for managing devices
 * Handles CRUD operations for discovered and monitored devices
 */
public class DeviceService {

    
    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);
    
    private final PgPool dbPool;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    public DeviceService(PgPool dbPool) {
        this.dbPool = dbPool;
    }
    
    /**
     * Get all devices for a user
     * @param userId the user ID
     * @return Future containing list of devices
     */
    public Future<List<DeviceDTO>> getAllDevices(UUID userId) {
        Promise<List<DeviceDTO>> promise = Promise.promise();

        var sql = """
            SELECT d.id, d.hostname, d.ip_address, d.device_type, d.os_info, 
                   d.credential_profile_id, d.status, d.last_seen, d.created_at, d.updated_at,
                   cp.name as credential_profile_name
            FROM devices d
            LEFT JOIN credential_profiles cp ON d.credential_profile_id = cp.id
            WHERE cp.created_by = $1
            ORDER BY d.created_at DESC
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(userId))
            .onSuccess(rows -> {
                List<DeviceDTO> devices = new ArrayList<>();
                for (var row : rows) {
                    devices.add(mapRowToDeviceDTO(row));
                }
                logger.info("Retrieved {} devices for user", devices.size());
                promise.complete(devices);
            })
            .onFailure(throwable -> {
                logger.error("Failed to get devices: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Get a specific device by ID
     * @param deviceId the device ID
     * @param userId the user ID
     * @return Future containing the device
     */
    public Future<DeviceDTO> getDeviceById(UUID deviceId, UUID userId) {
        Promise<DeviceDTO> promise = Promise.promise();

        var sql = """
            SELECT d.id, d.hostname, d.ip_address, d.device_type, d.os_info, 
                   d.credential_profile_id, d.status, d.last_seen, d.created_at, d.updated_at,
                   cp.name as credential_profile_name
            FROM devices d
            LEFT JOIN credential_profiles cp ON d.credential_profile_id = cp.id
            WHERE d.id = $1 AND cp.created_by = $2
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(deviceId, userId))
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    var row = rows.iterator().next();
                    var device = mapRowToDeviceDTO(row);
                    logger.info("Retrieved device: {}", device.getHostname());
                    promise.complete(device);
                } else {
                    promise.fail(new RuntimeException("Device not found or access denied"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to get device: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Create a new device manually
     * @param deviceDTO the device data
     * @param userId the user ID
     * @return Future containing the created device
     */
    public Future<DeviceDTO> createDevice(DeviceDTO deviceDTO, UUID userId) {
        Promise<DeviceDTO> promise = Promise.promise();
        
        try {
            // Validate input
            if (deviceDTO.getHostname() == null || deviceDTO.getHostname().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Device hostname is required"));
                return promise.future();
            }
            
            if (deviceDTO.getIpAddress() == null || deviceDTO.getIpAddress().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Device IP address is required"));
                return promise.future();
            }
            
            if (deviceDTO.getCredentialProfileId() == null) {
                promise.fail(new IllegalArgumentException("Credential profile ID is required"));
                return promise.future();
            }
            
            // Validate that user has access to the credential profile
            validateCredentialProfileAccess(deviceDTO.getCredentialProfileId(), userId)
                .compose(hasAccess -> {
                    if (!hasAccess) {
                        return Future.failedFuture(new IllegalArgumentException("Credential profile not found or access denied"));
                    }
                    
                    // Create device
                    return createDeviceInDatabase(deviceDTO);
                })
                .onSuccess(createdDevice -> {
                    logger.info("Created device: {}", createdDevice.getHostname());
                    promise.complete(createdDevice);
                })
                .onFailure(throwable -> {
                    logger.error("Failed to create device: {}", throwable.getMessage(), throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.error("Error creating device", e);
            promise.fail(e);
        }
        
        return promise.future();
    }
    
    /**
     * Update an existing device
     * @param deviceId the device ID
     * @param deviceDTO the updated device data
     * @param userId the user ID
     * @return Future containing the updated device
     */
    public Future<DeviceDTO> updateDevice(UUID deviceId, DeviceDTO deviceDTO, UUID userId) {
        Promise<DeviceDTO> promise = Promise.promise();
        
        try {
            // Validate input
            if (deviceDTO.getHostname() == null || deviceDTO.getHostname().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Device hostname is required"));
                return promise.future();
            }
            
            if (deviceDTO.getIpAddress() == null || deviceDTO.getIpAddress().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Device IP address is required"));
                return promise.future();
            }
            
            // If credential profile is being changed, validate access first
            if (deviceDTO.getCredentialProfileId() != null) {
                validateCredentialProfileAccess(deviceDTO.getCredentialProfileId(), userId)
                    .compose(hasAccess -> {
                        if (!hasAccess) {
                            return Future.failedFuture(new IllegalArgumentException("Credential profile not found or access denied"));
                        }
                        return updateDeviceInDatabase(deviceId, deviceDTO, userId);
                    })
                    .onSuccess(updatedDevice -> {
                        logger.info("Updated device: {}", updatedDevice.getHostname());
                        promise.complete(updatedDevice);
                    })
                    .onFailure(throwable -> {
                        logger.error("Failed to update device: {}", throwable.getMessage(), throwable);
                        promise.fail(throwable);
                    });
            } else {
                // Update without credential profile change
                updateDeviceInDatabase(deviceId, deviceDTO, userId)
                    .onSuccess(updatedDevice -> {
                        logger.info("Updated device: {}", updatedDevice.getHostname());
                        promise.complete(updatedDevice);
                    })
                    .onFailure(throwable -> {
                        logger.error("Failed to update device: {}", throwable.getMessage(), throwable);
                        promise.fail(throwable);
                    });
            }
                
        } catch (Exception e) {
            logger.error("Error updating device", e);
            promise.fail(e);
        }
        
        return promise.future();
    }
    
    /**
     * Delete a device
     * @param deviceId the device ID
     * @param userId the user ID
     * @return Future indicating success
     */
    public Future<Void> deleteDevice(UUID deviceId, UUID userId) {
        Promise<Void> promise = Promise.promise();
        
        // Delete device with access check in a single query
        var sql = """
            DELETE FROM devices d
            USING credential_profiles cp
            WHERE d.id = $1
              AND d.credential_profile_id = cp.id
              AND cp.created_by = $2
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(deviceId, userId))
            .onSuccess(rows -> {
                if (rows.rowCount() > 0) {
                    logger.info("Deleted device with ID: {}", deviceId);
                    promise.complete();
                } else {
                    promise.fail(new RuntimeException("Device not found or access denied"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to delete device: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Update device status
     * @param deviceId the device ID
     * @param status the new status
     * @param userId the user ID
     * @return Future indicating success
     */
    public Future<Void> updateDeviceStatus(UUID deviceId, DeviceStatus status, UUID userId) {
        Promise<Void> promise = Promise.promise();
        
        // First check if device exists and user has access
        getDeviceById(deviceId, userId)
            .compose(device -> {
                var sql = "UPDATE devices SET status = $1, last_seen = $2, updated_at = CURRENT_TIMESTAMP WHERE id = $3";
                return dbPool.preparedQuery(sql)
                    .execute(Tuple.of(status.getValue(), LocalDateTime.now(), deviceId));
            })
            .onSuccess(rows -> {
                logger.info("Updated device {} status to {}", deviceId, status.getValue());
                promise.complete();
            })
            .onFailure(throwable -> {
                logger.error("Failed to update device status: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Get devices by status
     * @param status the device status
     * @param userId the user ID
     * @return Future containing list of devices
     */
    public Future<List<DeviceDTO>> getDevicesByStatus(DeviceStatus status, UUID userId) {
        Promise<List<DeviceDTO>> promise = Promise.promise();

        var sql = """
            SELECT d.id, d.hostname, d.ip_address, d.device_type, d.os_info, 
                   d.credential_profile_id, d.status, d.last_seen, d.created_at, d.updated_at,
                   cp.name as credential_profile_name
            FROM devices d
            LEFT JOIN credential_profiles cp ON d.credential_profile_id = cp.id
            WHERE d.status = $1 AND cp.created_by = $2
            ORDER BY d.created_at DESC
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(status.getValue(), userId))
            .onSuccess(rows -> {
                List<DeviceDTO> devices = new ArrayList<>();
                for (var row : rows) {
                    devices.add(mapRowToDeviceDTO(row));
                }
                logger.info("Retrieved {} devices with status {} for user", devices.size(), status.getValue());
                promise.complete(devices);
            })
            .onFailure(throwable -> {
                logger.error("Failed to get devices by status: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Search devices by hostname or IP address
     * @param query the search query
     * @param userId the user ID
     * @return Future containing list of matching devices
     */
    public Future<List<DeviceDTO>> searchDevices(String query, UUID userId) {
        Promise<List<DeviceDTO>> promise = Promise.promise();

        var sql = """
            SELECT d.id, d.hostname, d.ip_address, d.device_type, d.os_info, 
                   d.credential_profile_id, d.status, d.last_seen, d.created_at, d.updated_at,
                   cp.name as credential_profile_name
            FROM devices d
            LEFT JOIN credential_profiles cp ON d.credential_profile_id = cp.id
            WHERE (d.hostname ILIKE $1 OR d.ip_address::text ILIKE $1) AND cp.created_by = $2
            ORDER BY d.created_at DESC
            """;

        var searchPattern = "%" + query + "%";
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(searchPattern, userId))
            .onSuccess(rows -> {
                List<DeviceDTO> devices = new ArrayList<>();
                for (var row : rows) {
                    devices.add(mapRowToDeviceDTO(row));
                }
                logger.info("Found {} devices matching query '{}' for user", devices.size(), query);
                promise.complete(devices);
            })
            .onFailure(throwable -> {
                logger.error("Failed to search devices: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Validate that user has access to credential profile
     */
    private Future<Boolean> validateCredentialProfileAccess(UUID credentialProfileId, UUID userId) {
        Promise<Boolean> promise = Promise.promise();

        var sql = "SELECT id FROM credential_profiles WHERE id = $1 AND created_by = $2";
        
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
     * Create device in database
     */
    private Future<DeviceDTO> createDeviceInDatabase(DeviceDTO deviceDTO) {
        Promise<DeviceDTO> promise = Promise.promise();

        var sql = """
            INSERT INTO devices (hostname, ip_address, device_type, os_info, credential_profile_id, status, last_seen)
            VALUES ($1, $2, $3, $4, $5, $6, $7)
            RETURNING id, hostname, ip_address, device_type, os_info, credential_profile_id, status, last_seen, created_at, updated_at
            """;

        var now = LocalDateTime.now();
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(
                deviceDTO.getHostname().trim(),
                deviceDTO.getIpAddress().trim(),
                deviceDTO.getDeviceType() != null ? deviceDTO.getDeviceType() : "unknown",
                deviceDTO.getOsInfo(),
                deviceDTO.getCredentialProfileId(),
                deviceDTO.getStatus() != null ? deviceDTO.getStatus() : DeviceStatus.UNKNOWN.getValue(),
                now
            ))
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    var row = rows.iterator().next();
                    var createdDevice = mapRowToDeviceDTO(row);
                    promise.complete(createdDevice);
                } else {
                    promise.fail(new RuntimeException("Failed to create device"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to create device in database: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Update device in database with access check
     */
    private Future<DeviceDTO> updateDeviceInDatabase(UUID deviceId, DeviceDTO deviceDTO, UUID userId) {
        Promise<DeviceDTO> promise = Promise.promise();

        var sql = """
            UPDATE devices d
            SET hostname = $1, ip_address = $2, device_type = $3, os_info = $4, 
                credential_profile_id = $5, status = $6, updated_at = CURRENT_TIMESTAMP
            FROM credential_profiles cp
            WHERE d.id = $7
              AND d.credential_profile_id = cp.id
              AND cp.created_by = $8
            RETURNING d.id, d.hostname, d.ip_address, d.device_type, d.os_info, d.credential_profile_id, d.status, d.last_seen, d.created_at, d.updated_at
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(
                deviceDTO.getHostname().trim(),
                deviceDTO.getIpAddress().trim(),
                deviceDTO.getDeviceType() != null ? deviceDTO.getDeviceType() : "unknown",
                deviceDTO.getOsInfo(),
                deviceDTO.getCredentialProfileId(),
                deviceDTO.getStatus() != null ? deviceDTO.getStatus() : DeviceStatus.UNKNOWN.getValue(),
                deviceId,
                userId
            ))
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    var row = rows.iterator().next();
                    var updatedDevice = mapRowToDeviceDTO(row);
                    promise.complete(updatedDevice);
                } else {
                    promise.fail(new RuntimeException("Device not found or access denied"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to update device in database: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Map database row to DeviceDTO
     */
    private DeviceDTO mapRowToDeviceDTO(Row row) {
        var dto = new DeviceDTO();
        dto.setId(row.getUUID("id"));
        dto.setHostname(row.getString("hostname"));
        dto.setIpAddress(row.getString("ip_address"));
        dto.setDeviceType(row.getString("device_type"));
        dto.setOsInfo(row.getJsonObject("os_info"));
        dto.setCredentialProfileId(row.getUUID("credential_profile_id"));
        dto.setStatus(row.getString("status"));
        dto.setLastSeen(row.getLocalDateTime("last_seen") != null ? 
            row.getLocalDateTime("last_seen").format(dateTimeFormatter) : null);
        dto.setCreatedAt(row.getLocalDateTime("created_at").format(dateTimeFormatter));
        dto.setUpdatedAt(row.getLocalDateTime("updated_at").format(dateTimeFormatter));
        
        // Set credential profile name if available
        if (row.getValue("credential_profile_name") != null) {
            dto.setCredentialProfileName(row.getString("credential_profile_name"));
        }
        
        return dto;
    }
}
