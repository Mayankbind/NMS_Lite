package com.nms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nms.config.ApplicationConfig;
import com.nms.config.DatabaseConfig;
import com.nms.handlers.AuthHandler;
import com.nms.handlers.CredentialHandler;
import com.nms.handlers.DeviceHandler;
import com.nms.handlers.DiscoveryHandler;
import com.nms.handlers.HealthHandler;
import com.nms.middleware.AuthMiddleware;
import com.nms.middleware.CorsMiddleware;
import com.nms.middleware.LoggingMiddleware;
import com.nms.middleware.RateLimitMiddleware;
import com.nms.middleware.SecurityHeadersMiddleware;
import com.nms.services.AuthService;
import com.nms.services.CredentialService;
import com.nms.services.DatabaseService;
import com.nms.services.DeviceService;
import com.nms.services.DiscoveryServiceProxy;
import com.nms.services.IDiscoveryService;
import com.nms.utils.EncryptionUtils;
import com.nms.verticles.DiscoveryWorkerVerticle;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ResponseTimeHandler;
import io.vertx.pgclient.PgPool;

/**
 * Main Verticle for NMS Lite Backend Service
 * Handles HTTP server setup, middleware configuration, and routing
 */
public class Main extends AbstractVerticle {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    /**
     * Main entry point for the application
     * Creates a Vert.x instance and deploys the Main verticle
     */
    public static void main(String[] args) {
        logger.info("Starting NMS Lite Backend Application...");
        
        Vertx vertx = Vertx.vertx();
        
        vertx.deployVerticle(new Main())
            .onSuccess(id -> {
                logger.info("Main verticle deployed successfully with ID: {}", id);
            })
            .onFailure(throwable -> {
                logger.error("Failed to deploy Main verticle", throwable);
                System.exit(1);
            });
    }
    
    @Override
    public void start(Promise<Void> startPromise) {
        logger.info("Starting NMS Lite Backend Service...");
        
        // Load configuration
        ConfigRetriever.create(vertx)
            .getConfig()
            .compose(config -> {
                logger.info("Configuration loaded successfully");
                
                // Initialize application configuration
                ApplicationConfig appConfig = new ApplicationConfig(config);

                // Initialize database
                return initializeDatabase(appConfig);
            })
            .compose((PgPool pgPool) -> {
                // Deploy Discovery Worker Verticle first
                return deployDiscoveryWorkerVerticle()
                    .compose(deploymentId -> {
                        logger.info("Discovery Worker Verticle deployed with ID: {}", deploymentId);
                        
                        // Initialize services
                        var databaseService = new DatabaseService(pgPool);
                        var authService = new AuthService(databaseService, config());
                        
                        // Initialize encryption utility
                        String encryptionKey = config().getString("encryption.key", "default-encryption-key-change-in-production");
                        EncryptionUtils encryptionUtils = new EncryptionUtils(encryptionKey);
                        
                        // Initialize credential service
                        CredentialService credentialService = new CredentialService(pgPool, encryptionUtils);
                        
                        // Initialize discovery service proxy (communicates with worker via Event Bus)
                        IDiscoveryService discoveryService = new DiscoveryServiceProxy(vertx);
                        
                        // Initialize device service
                        DeviceService deviceService = new DeviceService(pgPool);
                        
                        // Setup HTTP server
                        return setupHttpServer(authService, databaseService, credentialService, discoveryService, deviceService);
                    });
            })
            .onSuccess(server -> {
                logger.info("NMS Lite Backend Service started successfully on port: {}", 
                    config().getInteger("server.port", 8080));
                startPromise.complete();
            })
            .onFailure(throwable -> {
                logger.error("Failed to start NMS Lite Backend Service", throwable);
                startPromise.fail(throwable);
            });
    }
    
    private Future<PgPool> initializeDatabase(ApplicationConfig appConfig) {
        try {
            DatabaseConfig dbConfig = new DatabaseConfig(appConfig.getConfig());
            PgPool pgPool = dbConfig.createPgPool(vertx);
            
            // Test database connection
            return pgPool.query("SELECT 1")
                .execute()
                .map(result -> {
                    logger.info("Database connection established successfully");
                    return pgPool;
                });
                
        } catch (Exception e) {
            logger.error("Failed to initialize database configuration", e);
            return Future.failedFuture(e);
        }
    }
    
    /**
     * Deploy Discovery Worker Verticle
     * This verticle runs in a worker thread pool to handle blocking discovery operations
     * The worker verticle creates its own database connection pool for isolation
     */
    private Future<String> deployDiscoveryWorkerVerticle() {
        // Configure worker verticle deployment options
        DeploymentOptions options = new DeploymentOptions();
        options.setWorker(true); // Run in worker thread pool
        options.setInstances(config().getInteger("discovery.worker.instances", 2)); // Number of worker instances
        options.setWorkerPoolName("discovery-worker-pool");
        options.setWorkerPoolSize(config().getInteger("discovery.worker.poolSize", 4)); // Worker pool size
        
        // Share configuration with worker verticle
        options.setConfig(config());
        
        logger.info("Deploying Discovery Worker Verticle with {} instances and pool size {}", 
            options.getInstances(), options.getWorkerPoolSize());
        
        return vertx.deployVerticle(DiscoveryWorkerVerticle.class.getName(), options);
    }
    
    private Future<HttpServer> setupHttpServer(AuthService authService, DatabaseService databaseService, CredentialService credentialService, IDiscoveryService discoveryService, DeviceService deviceService) {
        try {
            var router = createRouter(authService, databaseService, credentialService, discoveryService, deviceService);
            
            // Create HTTP server
            return vertx.createHttpServer()
                .requestHandler(router)
                .listen(config().getInteger("server.port", 8080), 
                       config().getString("server.host", "0.0.0.0"))
                .onSuccess(server -> {
                    logger.info("HTTP server started on {}:{}", 
                        config().getString("server.host", "0.0.0.0"),
                        config().getInteger("server.port", 8080));
                });
                
        } catch (Exception e) {
            logger.error("Failed to setup HTTP server", e);
            return Future.failedFuture(e);
        }
    }
    
    private Router createRouter(AuthService authService, DatabaseService databaseService, CredentialService credentialService, IDiscoveryService discoveryService, DeviceService deviceService) {
        Router router = Router.router(vertx);
        
        // Global middleware
        router.route().handler(LoggingMiddleware.create());
        router.route().handler(SecurityHeadersMiddleware.create());
        router.route().handler(ResponseTimeHandler.create());
        
        // CORS middleware
        if (config().getJsonObject("server.cors", new JsonObject()).getBoolean("enabled", true)) {
            router.route().handler(CorsMiddleware.create(config()));
        }
        
        // Rate limiting middleware
        if (config().getJsonObject("server.rateLimit", new JsonObject()).getBoolean("enabled", true)) {
            router.route().handler(RateLimitMiddleware.create(config()));
        }
        
        // Body handler for parsing request bodies
        router.route().handler(BodyHandler.create()
            .setBodyLimit(10 * 1024 * 1024) // 10MB in bytes
            .setUploadsDirectory("uploads")
            .setMergeFormAttributes(true)
            .setDeleteUploadedFilesOnEnd(true));
        
        // Health check endpoints (no authentication required)
        HealthHandler healthHandler = new HealthHandler(databaseService);
        router.get("/health").handler(healthHandler.health);
        router.get("/ready").handler(healthHandler.readiness);
        router.get("/live").handler(healthHandler.liveness);
        
        // Create separate routers for different route types
        Router authRouter = Router.router(vertx);
        Router protectedRouter = Router.router(vertx);
        
        // Authentication routes (no auth required)
        AuthHandler authHandler = new AuthHandler(authService);
        authRouter.post("/login").handler(authHandler.login);
        authRouter.post("/refresh").handler(authHandler.refresh);
        authRouter.post("/logout").handler(AuthMiddleware.create(authService))
            .handler(authHandler.logout);
        
        // Protected routes (require authentication)
        protectedRouter.route().handler(AuthMiddleware.create(authService));
        
        // Credential profile routes
        CredentialHandler credentialHandler = new CredentialHandler(credentialService);
        protectedRouter.post("/credentials").handler(credentialHandler.createCredentialProfile);
        protectedRouter.get("/credentials").handler(credentialHandler.getAllCredentialProfiles);
        protectedRouter.get("/credentials/:id").handler(credentialHandler.getCredentialProfile);
        protectedRouter.put("/credentials/:id").handler(credentialHandler.updateCredentialProfile);
        protectedRouter.delete("/credentials/:id").handler(credentialHandler.deleteCredentialProfile);
        
        // Discovery routes
        DiscoveryHandler discoveryHandler = new DiscoveryHandler(discoveryService);
        protectedRouter.post("/discovery/start").handler(discoveryHandler.startDiscovery);
        protectedRouter.get("/discovery/status/:jobId").handler(discoveryHandler.getDiscoveryStatus);
        protectedRouter.get("/discovery/results/:jobId").handler(discoveryHandler.getDiscoveryResults);
        protectedRouter.delete("/discovery/job/:jobId").handler(discoveryHandler.cancelDiscovery);
        
        // Device routes
        DeviceHandler deviceHandler = new DeviceHandler(deviceService);
        protectedRouter.get("/devices").handler(deviceHandler.getAllDevices);
        protectedRouter.get("/devices/:id").handler(deviceHandler.getDeviceById);
        protectedRouter.post("/devices").handler(deviceHandler.createDevice);
        protectedRouter.put("/devices/:id").handler(deviceHandler.updateDevice);
        protectedRouter.delete("/devices/:id").handler(deviceHandler.deleteDevice);
        protectedRouter.get("/devices/status/:status").handler(deviceHandler.getDevicesByStatus);
        protectedRouter.get("/devices/search").handler(deviceHandler.searchDevices);
        protectedRouter.put("/devices/:id/status").handler(deviceHandler.updateDeviceStatus);
        
        // TODO: Add other protected routes here
        
        // Mount routers
        router.route("/api/auth/*").subRouter(authRouter);
        router.route("/api/*").subRouter(protectedRouter);
        
        // 404 handler
        router.route().last().handler(ctx -> {
            ctx.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Not Found")
                    .put("message", "The requested resource was not found")
                    .put("timestamp", System.currentTimeMillis())
                    .encode());
        });
        
        // Error handler
        router.errorHandler(500, ctx -> {
            logger.error("Internal server error", ctx.failure());
            ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Internal Server Error")
                    .put("message", "An unexpected error occurred")
                    .put("timestamp", System.currentTimeMillis())
                    .encode());
        });

        return router;
    }
}
