package com.nms.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Database configuration and connection management
 */
public class DatabaseConfig {
    
    private final ApplicationConfig appConfig;
    
    public DatabaseConfig(JsonObject config) {
        this.appConfig = new ApplicationConfig(config);
    }
    
    /**
     * Create PostgreSQL connection pool
     */
    public PgPool createPgPool(Vertx vertx) {
        // Configure connection options
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(appConfig.getDatabaseHost())
            .setPort(appConfig.getDatabasePort())
            .setDatabase(appConfig.getDatabaseName())
            .setUser(appConfig.getDatabaseUsername())
            .setPassword(appConfig.getDatabasePassword())
            .setSsl(appConfig.isDatabaseSsl())
            .setConnectTimeout(5000)
            .setIdleTimeout(600)
            .setTcpKeepAlive(true)
            .setTcpNoDelay(true)
            .setCachePreparedStatements(true)
            .setPreparedStatementCacheMaxSize(250)
            .setPreparedStatementCacheSqlLimit(2048);
        
        // Configure pool options
        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(appConfig.getDatabaseMaxConnections())
            .setMaxWaitQueueSize(100)
            .setMaxWaitQueueSize(100)
            .setEventLoopSize(4);
        
        return PgPool.pool(vertx, connectOptions, poolOptions);
    }
    
    /**
     * Get database URL for JDBC connections (if needed)
     */
    public String getDatabaseUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s?ssl=%s",
            appConfig.getDatabaseHost(),
            appConfig.getDatabasePort(),
            appConfig.getDatabaseName(),
            appConfig.isDatabaseSsl());
    }
    
    /**
     * Get database properties for JDBC connections
     */
    public JsonObject getDatabaseProperties() {
        return new JsonObject()
            .put("user", appConfig.getDatabaseUsername())
            .put("password", appConfig.getDatabasePassword())
            .put("ssl", appConfig.isDatabaseSsl())
            .put("sslmode", appConfig.isDatabaseSsl() ? "require" : "disable");
    }
}
