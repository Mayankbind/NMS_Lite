package com.nms.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.config.ApplicationConfig;
import com.nms.config.DatabaseConfig;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;

/**
 * Factory for creating database connection pools
 * Each call creates a NEW pool instance for isolation
 * 
 * This utility eliminates code duplication while maintaining
 * separate pools for each verticle (Main, Worker, etc.)
 */
public class DatabasePoolFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabasePoolFactory.class);
    
    /**
     * Create a new database connection pool
     * 
     * IMPORTANT: Each call creates a NEW pool instance.
     * This is intentional for isolation between verticles.
     * 
     * @param vertx Vert.x instance
     * @param config Application configuration (JsonObject)
     * @return Future containing the PgPool
     */
    public static Future<PgPool> createDatabasePool(Vertx vertx, JsonObject config) {
        try {
            var appConfig = new ApplicationConfig(config);
            var dbConfig = new DatabaseConfig(appConfig.getConfig());
            var pgPool = dbConfig.createPgPool(vertx);
            
            // Test database connection
            return pgPool.query("SELECT 1")
                .execute()
                .map(result -> {
                    logger.info("Database connection pool created successfully");
                    return pgPool;
                });
                
        } catch (Exception e) {
            logger.error("Failed to create database connection pool", e);
            return Future.failedFuture(e);
        }
    }
    
    /**
     * Create a new database connection pool with custom log message
     * 
     * @param vertx Vert.x instance
     * @param config Application configuration (JsonObject)
     * @param logPrefix Prefix for log messages (e.g., "Main Verticle", "Discovery Worker")
     * @return Future containing the PgPool
     */
    public static Future<PgPool> createDatabasePool(Vertx vertx, JsonObject config, String logPrefix) {
        try {
            var appConfig = new ApplicationConfig(config);
            var dbConfig = new DatabaseConfig(appConfig.getConfig());
            var pgPool = dbConfig.createPgPool(vertx);
            
            // Test database connection
            return pgPool.query("SELECT 1")
                .execute()
                .map(result -> {
                    logger.info("{}: Database connection pool created successfully", logPrefix);
                    return pgPool;
                });
                
        } catch (Exception e) {
            logger.error("{}: Failed to create database connection pool", logPrefix, e);
            return Future.failedFuture(e);
        }
    }
}

