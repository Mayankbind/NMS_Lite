package com.nms.handlers;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.services.DatabaseService;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Health check handler for monitoring application status
 */
public class HealthHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthHandler.class);
    
    private final DatabaseService databaseService;
    
    public HealthHandler(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }
    
    /**
     * Basic health check
     */
    public Handler<RoutingContext> health = this::handleHealth;
    
    /**
     * Readiness check (for Kubernetes)
     */
    public Handler<RoutingContext> readiness = this::handleReadiness;
    
    /**
     * Liveness check (for Kubernetes)
     */
    public Handler<RoutingContext> liveness = this::handleLiveness;
    
    private void handleHealth(RoutingContext ctx) {
        try {
            var healthStatus = new JsonObject()
                .put("status", "UP")
                .put("timestamp", System.currentTimeMillis())
                .put("uptime", getUptime())
                .put("version", getApplicationVersion())
                .put("environment", getEnvironment());
            
            // Add system information
            healthStatus.put("system", getSystemInfo());
            
            // Check database connectivity
            databaseService.testConnection()
                .onSuccess(connected -> {
                    healthStatus.put("database", new JsonObject()
                        .put("status", connected ? "UP" : "DOWN")
                        .put("connected", connected));
                    
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(healthStatus.encode());
                })
                .onFailure(throwable -> {
                    healthStatus.put("database", new JsonObject()
                        .put("status", "DOWN")
                        .put("connected", false)
                        .put("error", throwable.getMessage()));
                    
                    ctx.response()
                        .setStatusCode(503)
                        .putHeader("Content-Type", "application/json")
                        .end(healthStatus.encode());
                });
                
        } catch (Exception e) {
            logger.error("Error in health check", e);
            sendErrorResponse(ctx, "Health check failed");
        }
    }
    
    private void handleReadiness(RoutingContext ctx) {
        try {
            // Check if application is ready to serve requests
            databaseService.testConnection()
                .onSuccess(connected -> {
                    if (connected) {
                        var readinessStatus = new JsonObject()
                            .put("status", "READY")
                            .put("timestamp", System.currentTimeMillis())
                            .put("checks", new JsonObject()
                                .put("database", "UP"));
                        
                        ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(readinessStatus.encode());
                    } else {
                        sendNotReadyResponse(ctx, "Database not available");
                    }
                })
                .onFailure(throwable -> {
                    logger.warn("Readiness check failed: {}", throwable.getMessage());
                    sendNotReadyResponse(ctx, "Database connection failed");
                });
                
        } catch (Exception e) {
            logger.error("Error in readiness check", e);
            sendNotReadyResponse(ctx, "Readiness check failed");
        }
    }
    
    private void handleLiveness(RoutingContext ctx) {
        try {
            // Simple liveness check - if we can respond, we're alive
            var livenessStatus = new JsonObject()
                .put("status", "ALIVE")
                .put("timestamp", System.currentTimeMillis())
                .put("uptime", getUptime());
            
            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(livenessStatus.encode());
                
        } catch (Exception e) {
            logger.error("Error in liveness check", e);
            sendErrorResponse(ctx, "Liveness check failed");
        }
    }
    
    private void sendNotReadyResponse(RoutingContext ctx, String message) {
        var response = new JsonObject()
            .put("status", "NOT_READY")
            .put("message", message)
            .put("timestamp", System.currentTimeMillis());
        
        ctx.response()
            .setStatusCode(503)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    private void sendErrorResponse(RoutingContext ctx, String message) {
        var response = new JsonObject()
            .put("status", "ERROR")
            .put("message", message)
            .put("timestamp", System.currentTimeMillis());
        
        ctx.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    private long getUptime() {
        var runtimeBean = ManagementFactory.getRuntimeMXBean();
        return runtimeBean.getUptime();
    }
    
    private String getApplicationVersion() {
        var pkg = this.getClass().getPackage();
        return pkg != null ? pkg.getImplementationVersion() : "unknown";
    }
    
    private String getEnvironment() {
        return System.getProperty("vertx.environment", "development");
    }
    
    private JsonObject getSystemInfo() {
        var memoryBean = ManagementFactory.getMemoryMXBean();
        
        return new JsonObject()
            .put("javaVersion", System.getProperty("java.version"))
            .put("javaVendor", System.getProperty("java.vendor"))
            .put("osName", System.getProperty("os.name"))
            .put("osVersion", System.getProperty("os.version"))
            .put("osArch", System.getProperty("os.arch"))
            .put("availableProcessors", Runtime.getRuntime().availableProcessors())
            .put("maxMemory", Runtime.getRuntime().maxMemory())
            .put("totalMemory", Runtime.getRuntime().totalMemory())
            .put("freeMemory", Runtime.getRuntime().freeMemory())
            .put("heapMemoryUsage", new JsonObject()
                .put("used", memoryBean.getHeapMemoryUsage().getUsed())
                .put("max", memoryBean.getHeapMemoryUsage().getMax())
                .put("committed", memoryBean.getHeapMemoryUsage().getCommitted()))
            .put("nonHeapMemoryUsage", new JsonObject()
                .put("used", memoryBean.getNonHeapMemoryUsage().getUsed())
                .put("max", memoryBean.getNonHeapMemoryUsage().getMax())
                .put("committed", memoryBean.getNonHeapMemoryUsage().getCommitted()));
    }
}
