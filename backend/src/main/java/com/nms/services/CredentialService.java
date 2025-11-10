package com.nms.services;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.models.CredentialProfileDTO;
import com.nms.utils.EncryptionUtils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/**
 * Service for managing credential profiles
 * Handles CRUD operations for SSH credentials used in device monitoring
 */
public class CredentialService {
    
    private static final Logger logger = LoggerFactory.getLogger(CredentialService.class);
    
    private final PgPool dbPool;
    private final EncryptionUtils encryptionUtils;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    public CredentialService(PgPool dbPool, EncryptionUtils encryptionUtils) {
        this.dbPool = dbPool;
        this.encryptionUtils = encryptionUtils;
    }
    
    /**
     * Create a new credential profile
     */
    public Future<CredentialProfileDTO> createCredentialProfile(CredentialProfileDTO dto, UUID createdBy) {
        Promise<CredentialProfileDTO> promise = Promise.promise();
        
        try {
            // Validate input
            if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Credential profile name is required"));
                return promise.future();
            }
            
            if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Username is required"));
                return promise.future();
            }
            
            if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Password is required"));
                return promise.future();
            }
            
            // Encrypt password
            var encryptedPassword = encryptionUtils.encrypt(dto.getPassword());
            
            // Encrypt private key if provided
            String encryptedPrivateKey = null;
            if (dto.getPrivateKey() != null && !dto.getPrivateKey().trim().isEmpty()) {
                encryptedPrivateKey = encryptionUtils.encrypt(dto.getPrivateKey());
            }
            
            // Insert into database
            var sql = """
                INSERT INTO credential_profiles (name, username, password_encrypted, private_key_encrypted, port, created_by)
                VALUES ($1, $2, $3, $4, $5, $6)
                RETURNING id, name, username, password_encrypted, private_key_encrypted, port, created_by, created_at, updated_at
                """;
            
            dbPool.preparedQuery(sql)
                .execute(Tuple.of(
                    dto.getName().trim(),
                    dto.getUsername().trim(),
                    encryptedPassword,
                    encryptedPrivateKey,
                    dto.getPort() != null ? dto.getPort() : Integer.valueOf(22),
                    createdBy
                ))
                .onSuccess(rows -> {
                    if (rows.iterator().hasNext()) {
                        var row = rows.iterator().next();
                        var result = mapRowToDTO(row);
                        logger.info("Created credential profile: {}", result.getName());
                        promise.complete(result);
                    } else {
                        promise.fail(new RuntimeException("Failed to create credential profile"));
                    }
                })
                .onFailure(throwable -> {
                    logger.error("Failed to create credential profile: {}", throwable.getMessage(), throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.error("Error creating credential profile", e);
            promise.fail(e);
        }
        
        return promise.future();
    }
    
    /**
     * Get all credential profiles for a user
     */
    public Future<List<CredentialProfileDTO>> getAllCredentialProfiles(UUID userId) {
        Promise<List<CredentialProfileDTO>> promise = Promise.promise();

        var sql = """
            SELECT cp.id, cp.name, cp.username, cp.password_encrypted, cp.private_key_encrypted, 
                   cp.port, cp.created_by, cp.created_at, cp.updated_at, u.username as created_by_username
            FROM credential_profiles cp
            LEFT JOIN users u ON cp.created_by = u.id
            WHERE cp.created_by = $1
            ORDER BY cp.created_at DESC
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(userId))
            .onSuccess(rows -> {
                List<CredentialProfileDTO> profiles = new ArrayList<>();
                for (var row : rows) {
                    profiles.add(mapRowToDTO(row));
                }
                logger.info("Retrieved {} credential profiles for user", profiles.size());
                promise.complete(profiles);
            })
            .onFailure(throwable -> {
                logger.error("Failed to get credential profiles: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Get a specific credential profile by ID
     */
    public Future<CredentialProfileDTO> getCredentialProfileById(UUID id, UUID userId) {
        Promise<CredentialProfileDTO> promise = Promise.promise();

        var sql = """
            SELECT cp.id, cp.name, cp.username, cp.password_encrypted, cp.private_key_encrypted, 
                   cp.port, cp.created_by, cp.created_at, cp.updated_at, u.username as created_by_username
            FROM credential_profiles cp
            LEFT JOIN users u ON cp.created_by = u.id
            WHERE cp.id = $1 AND cp.created_by = $2
            """;
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(id, userId))
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    var row = rows.iterator().next();
                    var result = mapRowToDTO(row);
                    logger.info("Retrieved credential profile: {}", result.getName());
                    promise.complete(result);
                } else {
                    promise.fail(new RuntimeException("Credential profile not found"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to get credential profile: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Update a credential profile
     */
    public Future<CredentialProfileDTO> updateCredentialProfile(UUID id, CredentialProfileDTO dto, UUID userId) {
        Promise<CredentialProfileDTO> promise = Promise.promise();
        
        try {
            // Validate input
            if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Credential profile name is required"));
                return promise.future();
            }
            
            if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
                promise.fail(new IllegalArgumentException("Username is required"));
                return promise.future();
            }
            
            // Build dynamic SQL for partial updates
            var sqlBuilder = new StringBuilder("UPDATE credential_profiles SET ");
            List<Object> params = new ArrayList<>();
            var paramIndex = 1;
            
            sqlBuilder.append("name = $").append(paramIndex++).append(", ");
            params.add(dto.getName().trim());
            
            sqlBuilder.append("username = $").append(paramIndex++).append(", ");
            params.add(dto.getUsername().trim());
            
            // Only update password if provided
            if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
                var encryptedPassword = encryptionUtils.encrypt(dto.getPassword());
                sqlBuilder.append("password_encrypted = $").append(paramIndex++).append(", ");
                params.add(encryptedPassword);
            }
            
            // Only update private key if provided
            if (dto.getPrivateKey() != null && !dto.getPrivateKey().trim().isEmpty()) {
                var encryptedPrivateKey = encryptionUtils.encrypt(dto.getPrivateKey());
                sqlBuilder.append("private_key_encrypted = $").append(paramIndex++).append(", ");
                params.add(encryptedPrivateKey);
            }
            
            // Update port if provided
            if (dto.getPort() != null) {
                sqlBuilder.append("port = $").append(paramIndex++).append(", ");
                params.add(dto.getPort());
            }
            
            sqlBuilder.append("updated_at = CURRENT_TIMESTAMP ");
            sqlBuilder.append("WHERE id = $").append(paramIndex++).append(" AND created_by = $").append(paramIndex++);
            params.add(id);
            params.add(userId);
            
            sqlBuilder.append(" RETURNING id, name, username, password_encrypted, private_key_encrypted, port, created_by, created_at, updated_at");

            var sql = sqlBuilder.toString();
            
            dbPool.preparedQuery(sql)
                .execute(Tuple.tuple(params))
                .onSuccess(rows -> {
                    if (rows.iterator().hasNext()) {
                        var row = rows.iterator().next();
                        var result = mapRowToDTO(row);
                        logger.info("Updated credential profile: {}", result.getName());
                        promise.complete(result);
                    } else {
                        promise.fail(new RuntimeException("Credential profile not found or access denied"));
                    }
                })
                .onFailure(throwable -> {
                    logger.error("Failed to update credential profile: {}", throwable.getMessage(), throwable);
                    promise.fail(throwable);
                });
                
        } catch (Exception e) {
            logger.error("Error updating credential profile", e);
            promise.fail(e);
        }
        
        return promise.future();
    }
    
    /**
     * Delete a credential profile
     */
    public Future<Void> deleteCredentialProfile(UUID id, UUID userId) {
        Promise<Void> promise = Promise.promise();

        var sql = "DELETE FROM credential_profiles WHERE id = $1 AND created_by = $2";
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(id, userId))
            .onSuccess(rows -> {
                if (rows.rowCount() > 0) {
                    logger.info("Deleted credential profile with ID: {}", id);
                    promise.complete();
                } else {
                    promise.fail(new RuntimeException("Credential profile not found or access denied"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to delete credential profile: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Get decrypted password for a credential profile (used by polling engine)
     */
    public Future<String> getDecryptedPassword(UUID credentialProfileId) {
        Promise<String> promise = Promise.promise();

        var sql = "SELECT password_encrypted FROM credential_profiles WHERE id = $1";
        
        dbPool.preparedQuery(sql)
            .execute(Tuple.of(credentialProfileId))
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    var row = rows.iterator().next();
                    var encryptedPassword = row.getString("password_encrypted");
                    try {
                        var decryptedPassword = encryptionUtils.decrypt(encryptedPassword);
                        promise.complete(decryptedPassword);
                    } catch (Exception e) {
                        logger.error("Failed to decrypt password", e);
                        promise.fail(e);
                    }
                } else {
                    promise.fail(new RuntimeException("Credential profile not found"));
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to get credential profile for decryption: {}", throwable.getMessage(), throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Map database row to DTO
     */
    private CredentialProfileDTO mapRowToDTO(Row row) {
        var dto = new CredentialProfileDTO();
        dto.setId(row.getUUID("id"));
        dto.setName(row.getString("name"));
        dto.setUsername(row.getString("username"));
        dto.setPort(row.getInteger("port"));
        dto.setCreatedBy(row.getUUID("created_by"));
        
        // Set created by username if available
        if (row.getValue("created_by_username") != null) {
            dto.setCreatedByUsername(row.getString("created_by_username"));
        }
        
        // Format timestamps
        var createdAt = row.getLocalDateTime("created_at");
        if (createdAt != null) {
            dto.setCreatedAt(createdAt.format(dateTimeFormatter));
        }

        var updatedAt = row.getLocalDateTime("updated_at");
        if (updatedAt != null) {
            dto.setUpdatedAt(updatedAt.format(dateTimeFormatter));
        }
        
        // Never return encrypted passwords/keys in API responses
        dto.setPassword(null);
        dto.setPrivateKey(null);
        
        return dto;
    }
}