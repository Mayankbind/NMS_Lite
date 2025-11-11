package com.nms.verticles;

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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ResponseTimeHandler;
import io.vertx.pgclient.PgPool;

/**
 * HTTP Server Verticle for NMS Lite Backend Service
 * Handles HTTP server setup, middleware configuration, and routing
 */
public class HttpServerVerticle extends AbstractVerticle {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpServerVerticle.class);
    
    @Override
    public void start(Promise<Void> startPromise) {
        logger.info("Starting HttpServer Verticle...");
        
        // Initialize application configuration
        var appConfig = new ApplicationConfig(config());
        
        // Initialize database
        initializeDatabase(appConfig)
            .compose(pgPool -> {
                // Initialize services
                var databaseService = new DatabaseService(pgPool);
                var authService = new AuthService(databaseService, config());
                
                // Initialize encryption utility
                var encryptionKey = config().getString("encryption.key", "default-encryption-key-change-in-production");
                var encryptionUtils = new EncryptionUtils(encryptionKey);
                
                // Initialize credential service
                var credentialService = new CredentialService(pgPool, encryptionUtils);
                
                // Initialize discovery service proxy (communicates with worker via Event Bus)
                IDiscoveryService discoveryService = new DiscoveryServiceProxy(vertx);
                
                // Initialize device service
                var deviceService = new DeviceService(pgPool);
                
                // Setup HTTP server
                return setupHttpServer(authService, databaseService, credentialService, discoveryService, deviceService);
            })
            .onSuccess(server -> {
                logger.info("HttpServer Verticle started successfully on port: {}", 
                    config().getInteger("server.port", 8080));
                startPromise.complete();
            })
            .onFailure(throwable -> {
                logger.error("Failed to start HttpServer Verticle", throwable);
                startPromise.fail(throwable);
            });
    }
    
    private Future<PgPool> initializeDatabase(ApplicationConfig appConfig) {
        try {
            var dbConfig = new DatabaseConfig(appConfig.getConfig());
            var pgPool = dbConfig.createPgPool(vertx);
            
            // Test database connection
            return pgPool.query("SELECT 1")
                .execute()
                .map(result -> {
                    logger.info("HttpServer Verticle: Database connection established successfully");
                    return pgPool;
                });
                
        } catch (Exception e) {
            logger.error("HttpServer Verticle: Failed to initialize database configuration", e);
            return Future.failedFuture(e);
        }
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
                    logger.info("HttpServer Verticle: HTTP server started on {}:{}", 
                        config().getString("server.host", "0.0.0.0"),
                        config().getInteger("server.port", 8080));
                });
                
        } catch (Exception e) {
            logger.error("HttpServer Verticle: Failed to setup HTTP server", e);
            return Future.failedFuture(e);
        }
    }
    
    private Router createRouter(AuthService authService, DatabaseService databaseService, CredentialService credentialService, IDiscoveryService discoveryService, DeviceService deviceService) {
        var router = Router.router(vertx);
        
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
        var healthHandler = new HealthHandler(databaseService);
        router.get("/health").handler(healthHandler.health);
        router.get("/ready").handler(healthHandler.readiness);
        router.get("/live").handler(healthHandler.liveness);
        
        // Create separate routers for different route types
        var authRouter = Router.router(vertx);
        var protectedRouter = Router.router(vertx);
        
        // Authentication routes (no auth required)
        var authHandler = new AuthHandler(authService);
        authRouter.post("/login").handler(authHandler.login);
        authRouter.post("/refresh").handler(authHandler.refresh);
        authRouter.post("/logout").handler(AuthMiddleware.create(authService))
            .handler(authHandler.logout);
        
        // Protected routes (require authentication)
        protectedRouter.route().handler(AuthMiddleware.create(authService));
        
        // Credential profile routes
        var credentialHandler = new CredentialHandler(credentialService);
        protectedRouter.post("/credentials").handler(credentialHandler.createCredentialProfile);
        protectedRouter.get("/credentials").handler(credentialHandler.getAllCredentialProfiles);
        protectedRouter.get("/credentials/:id").handler(credentialHandler.getCredentialProfile);
        protectedRouter.put("/credentials/:id").handler(credentialHandler.updateCredentialProfile);
        protectedRouter.delete("/credentials/:id").handler(credentialHandler.deleteCredentialProfile);
        
        // Discovery routes
        var discoveryHandler = new DiscoveryHandler(discoveryService);
        protectedRouter.post("/discovery/start").handler(discoveryHandler.startDiscovery);
        protectedRouter.get("/discovery/status/:jobId").handler(discoveryHandler.getDiscoveryStatus);
        protectedRouter.get("/discovery/results/:jobId").handler(discoveryHandler.getDiscoveryResults);
        protectedRouter.delete("/discovery/job/:jobId").handler(discoveryHandler.cancelDiscovery);
        
        // Device routes
        var deviceHandler = new DeviceHandler(deviceService);
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
            logger.error("HttpServer Verticle: Internal server error", ctx.failure());
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

