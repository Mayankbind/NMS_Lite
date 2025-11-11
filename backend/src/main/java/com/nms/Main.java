package com.nms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.verticles.DiscoveryWorkerVerticle;
import com.nms.verticles.HttpServerVerticle;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Bootstrap class for NMS Lite Backend Application
 * Responsible for creating Vert.x instance, loading configuration, and deploying verticles
 */
public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    /**
     * Main entry point for the application
     * Creates a Vert.x instance, loads configuration, and deploys verticles
     */
    public static void main(String[] args) {
        logger.info("Starting NMS Lite Backend Application...");

        var vertx = Vertx.vertx();
        
        // Load configuration
        ConfigRetriever.create(vertx)
            .getConfig()
            .compose(config -> {
                logger.info("Configuration loaded successfully");
                
                // Deploy Discovery Worker Verticle first
                // IMPORTANT: Worker must start before HttpServer to ensure Event Bus consumers
                // are registered before HTTP endpoints that use them become available.
                // This prevents race conditions where discovery requests fail because
                // no consumer is ready to process them.
                return deployDiscoveryWorkerVerticle(vertx, config)
                    .compose(workerDeploymentId -> {
                        logger.info("Discovery Worker Verticle deployed with ID: {}", workerDeploymentId);
                        
                        // Deploy HttpServer Verticle (after worker is ready)
                        return deployHttpServerVerticle(vertx, config)
                            .map(httpServerDeploymentId -> {
                                logger.info("HttpServer Verticle deployed with ID: {}", httpServerDeploymentId);
                                return httpServerDeploymentId;
                            });
                    });
            })
            .onSuccess(deploymentId -> {
                logger.info("NMS Lite Backend Application started successfully");
                logger.info("All verticles deployed successfully");
            })
            .onFailure(throwable -> {
                logger.error("Failed to start NMS Lite Backend Application", throwable);
                System.exit(1);
            });
    }
    
    /**
     * Deploy HttpServer Verticle
     * This verticle handles HTTP server, routing, middleware, and service initialization
     */
    private static Future<String> deployHttpServerVerticle(Vertx vertx, JsonObject config) {
        var options = new DeploymentOptions();
        options.setConfig(config);
        
        logger.info("Deploying HttpServer Verticle...");
        
        return vertx.deployVerticle(HttpServerVerticle.class.getName(), options);
    }
    
    /**
     * Deploy Discovery Worker Verticle
     * This verticle runs in a worker thread pool to handle blocking discovery operations
     * The worker verticle creates its own database connection pool for isolation
     */
    private static Future<String> deployDiscoveryWorkerVerticle(Vertx vertx, JsonObject config) {
        // Configure worker verticle deployment options
        var options = new DeploymentOptions();
        options.setWorker(true); // Run in worker thread pool
        options.setInstances(config.getInteger("discovery.worker.instances", 2)); // Number of worker instances
        options.setWorkerPoolName("discovery-worker-pool");
        options.setWorkerPoolSize(config.getInteger("discovery.worker.poolSize", 4)); // Worker pool size
        
        // Share configuration with worker verticle
        options.setConfig(config);
        
        logger.info("Deploying Discovery Worker Verticle with {} instances and pool size {}", 
            options.getInstances(), options.getWorkerPoolSize());
        
        return vertx.deployVerticle(DiscoveryWorkerVerticle.class.getName(), options);
    }
}