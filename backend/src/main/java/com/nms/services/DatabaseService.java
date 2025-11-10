package com.nms.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

/**
 * Database service for handling database operations
 */
public class DatabaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    
    private final PgPool pgPool;
    
    public DatabaseService(PgPool pgPool) {
        this.pgPool = pgPool;
    }
    
    /**
     * Test database connection
     */
    public Future<Boolean> testConnection() {
        Promise<Boolean> promise = Promise.promise();
        
        pgPool.query("SELECT 1")
            .execute()
            .onSuccess(result -> {
                logger.debug("Database connection test successful");
                promise.complete(true);
            })
            .onFailure(throwable -> {
                logger.error("Database connection test failed", throwable);
                promise.fail(throwable);
            });

        return promise.future();
    }
    
    /**
     * Create user
     */
    public Future<JsonObject> createUser(String username, String email, String passwordHash, String role) {
        Promise<JsonObject> promise = Promise.promise();

        var sql = "INSERT INTO users (username, email, password_hash, role) VALUES ($1, $2, $3, $4) RETURNING id, username, email, role, created_at";
        
        pgPool.preparedQuery(sql)
            .execute(Tuple.of(username, email, passwordHash, role))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    var row = rows.iterator().next();
                    var user = new JsonObject()
                        .put("id", row.getUUID("id").toString())
                        .put("username", row.getString("username"))
                        .put("email", row.getString("email"))
                        .put("role", row.getString("role"))
                        .put("createdAt", row.getLocalDateTime("created_at").toString());
                    promise.complete(user);
                } else {
                    promise.fail("Failed to create user");
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to create user", throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Find user by username
     */
    public Future<JsonObject> findUserByUsername(String username) {
        Promise<JsonObject> promise = Promise.promise();

        var sql = "SELECT id, username, email, password_hash, role, created_at, updated_at FROM users WHERE username = $1";
        
        pgPool.preparedQuery(sql)
            .execute(Tuple.of(username))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    var row = rows.iterator().next();
                    var user = new JsonObject()
                        .put("id", row.getUUID("id").toString())
                        .put("username", row.getString("username"))
                        .put("email", row.getString("email"))
                        .put("passwordHash", row.getString("password_hash"))
                        .put("role", row.getString("role"))
                        .put("createdAt", row.getLocalDateTime("created_at").toString())
                        .put("updatedAt", row.getLocalDateTime("updated_at").toString());
                    promise.complete(user);
                } else {
                    promise.complete(null);
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to find user by username", throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Find user by ID
     */
    public Future<JsonObject> findUserById(String userId) {
        Promise<JsonObject> promise = Promise.promise();

        var sql = "SELECT id, username, email, password_hash, role, created_at, updated_at FROM users WHERE id = $1";
        
        pgPool.preparedQuery(sql)
            .execute(Tuple.of(UUID.fromString(userId)))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    var row = rows.iterator().next();
                    var user = new JsonObject()
                        .put("id", row.getUUID("id").toString())
                        .put("username", row.getString("username"))
                        .put("email", row.getString("email"))
                        .put("passwordHash", row.getString("password_hash"))
                        .put("role", row.getString("role"))
                        .put("createdAt", row.getLocalDateTime("created_at").toString())
                        .put("updatedAt", row.getLocalDateTime("updated_at").toString());
                    promise.complete(user);
                } else {
                    promise.complete(null);
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to find user by ID", throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Update user last login
     */
    public Future<Void> updateUserLastLogin(String userId) {
        Promise<Void> promise = Promise.promise();

        var sql = "UPDATE users SET updated_at = CURRENT_TIMESTAMP WHERE id = $1";
        
        pgPool.preparedQuery(sql)
            .execute(Tuple.of(UUID.fromString(userId)))
            .onSuccess(result -> {
                logger.debug("Updated last login for user: {}", userId);
                promise.complete();
            })
            .onFailure(throwable -> {
                logger.error("Failed to update user last login", throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Get all users
     */
    public Future<List<JsonObject>> getAllUsers() {
        Promise<List<JsonObject>> promise = Promise.promise();

        var sql = "SELECT id, username, email, role, created_at, updated_at FROM users ORDER BY created_at DESC";
        
        pgPool.query(sql)
            .execute()
            .onSuccess(rows -> {
                List<JsonObject> users = new ArrayList<>();
                for (var row : rows) {
                    var user = new JsonObject()
                        .put("id", row.getUUID("id").toString())
                        .put("username", row.getString("username"))
                        .put("email", row.getString("email"))
                        .put("role", row.getString("role"))
                        .put("createdAt", row.getLocalDateTime("created_at").toString())
                        .put("updatedAt", row.getLocalDateTime("updated_at").toString());
                    users.add(user);
                }
                promise.complete(users);
            })
            .onFailure(throwable -> {
                logger.error("Failed to get all users", throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Delete user
     */
    public Future<Boolean> deleteUser(String userId) {
        Promise<Boolean> promise = Promise.promise();

        var sql = "DELETE FROM users WHERE id = $1";
        
        pgPool.preparedQuery(sql)
            .execute(Tuple.of(UUID.fromString(userId)))
            .onSuccess(result -> {
                var deleted = result.rowCount() > 0;
                promise.complete(deleted);
            })
            .onFailure(throwable -> {
                logger.error("Failed to delete user", throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Check if username exists
     */
    public Future<Boolean> usernameExists(String username) {
        Promise<Boolean> promise = Promise.promise();

        var sql = "SELECT COUNT(*) FROM users WHERE username = $1";
        
        pgPool.preparedQuery(sql)
            .execute(Tuple.of(username))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    var row = rows.iterator().next();
                    long count = row.getLong(0);
                    promise.complete(count > 0);
                } else {
                    promise.complete(false);
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to check username existence", throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
    
    /**
     * Check if email exists
     */
    public Future<Boolean> emailExists(String email) {
        Promise<Boolean> promise = Promise.promise();

        var sql = "SELECT COUNT(*) FROM users WHERE email = $1";
        
        pgPool.preparedQuery(sql)
            .execute(Tuple.of(email))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    var row = rows.iterator().next();
                    long count = row.getLong(0);
                    promise.complete(count > 0);
                } else {
                    promise.complete(false);
                }
            })
            .onFailure(throwable -> {
                logger.error("Failed to check email existence", throwable);
                promise.fail(throwable);
            });
        
        return promise.future();
    }
}
