package com.nms.config;

import io.vertx.core.json.JsonObject;

/**
 * Application configuration wrapper
 * Provides type-safe access to configuration values
 */
public class ApplicationConfig {
    
    private final JsonObject config;
    
    public ApplicationConfig(JsonObject config) {
        this.config = config;
    }
    
    public JsonObject getConfig() {
        return config;
    }
    
    // Server configuration
    public int getServerPort() {
        return config.getInteger("server.port", 8080);
    }
    
    public String getServerHost() {
        return config.getString("server.host", "0.0.0.0");
    }
    
    public boolean isSslEnabled() {
        return config.getJsonObject("server.ssl", new JsonObject()).getBoolean("enabled", false);
    }
    
    public String getSslKeystore() {
        return config.getJsonObject("server.ssl", new JsonObject()).getString("keystore");
    }
    
    public String getSslKeystorePassword() {
        return config.getJsonObject("server.ssl", new JsonObject()).getString("keystorePassword");
    }
    
    // Database configuration
    public String getDatabaseHost() {
        return config.getJsonObject("database", new JsonObject()).getString("host", "localhost");
    }
    
    public int getDatabasePort() {
        return config.getJsonObject("database", new JsonObject()).getInteger("port", 5432);
    }
    
    public String getDatabaseName() {
        return config.getJsonObject("database", new JsonObject()).getString("name", "nms_lite");
    }
    
    public String getDatabaseUsername() {
        return config.getJsonObject("database", new JsonObject()).getString("username", "nms_user");
    }
    
    public String getDatabasePassword() {
        return config.getJsonObject("database", new JsonObject()).getString("password", "nms_password");
    }
    
    public boolean isDatabaseSsl() {
        return config.getJsonObject("database", new JsonObject()).getBoolean("ssl", false);
    }
    
    public int getDatabaseMaxConnections() {
        return config.getJsonObject("database", new JsonObject()).getInteger("maxConnections", 20);
    }
    
    public int getDatabaseMinConnections() {
        return config.getJsonObject("database", new JsonObject()).getInteger("minConnections", 5);
    }
    
    // JWT configuration
    public String getJwtSecret() {
        return config.getJsonObject("jwt", new JsonObject()).getString("secret", "default-secret");
    }
    
    public String getJwtAlgorithm() {
        return config.getJsonObject("jwt", new JsonObject()).getString("algorithm", "HS256");
    }
    
    public int getJwtExpiration() {
        return config.getJsonObject("jwt", new JsonObject()).getInteger("expiration", 3600);
    }
    
    public int getJwtRefreshExpiration() {
        return config.getJsonObject("jwt", new JsonObject()).getInteger("refreshExpiration", 604800);
    }
    
    public String getJwtIssuer() {
        return config.getJsonObject("jwt", new JsonObject()).getString("issuer", "nms-lite");
    }
    
    public String getJwtAudience() {
        return config.getJsonObject("jwt", new JsonObject()).getString("audience", "nms-lite-api");
    }
    
    // CORS configuration
    public boolean isCorsEnabled() {
        return config.getJsonObject("server.cors", new JsonObject()).getBoolean("enabled", true);
    }
    
    public String[] getCorsAllowedOrigins() {
        return config.getJsonObject("server.cors", new JsonObject())
            .getJsonArray("allowedOrigins", new io.vertx.core.json.JsonArray().add("*"))
            .stream()
            .map(Object::toString)
            .toArray(String[]::new);
    }
    
    public String[] getCorsAllowedMethods() {
        return config.getJsonObject("server.cors", new JsonObject())
            .getJsonArray("allowedMethods", new io.vertx.core.json.JsonArray()
                .add("GET").add("POST").add("PUT").add("DELETE").add("OPTIONS"))
            .stream()
            .map(Object::toString)
            .toArray(String[]::new);
    }
    
    public String[] getCorsAllowedHeaders() {
        return config.getJsonObject("server.cors", new JsonObject())
            .getJsonArray("allowedHeaders", new io.vertx.core.json.JsonArray().add("*"))
            .stream()
            .map(Object::toString)
            .toArray(String[]::new);
    }
    
    public boolean isCorsAllowCredentials() {
        return config.getJsonObject("server.cors", new JsonObject()).getBoolean("allowCredentials", true);
    }
    
    // Rate limiting configuration
    public boolean isRateLimitEnabled() {
        return config.getJsonObject("server.rateLimit", new JsonObject()).getBoolean("enabled", true);
    }
    
    public int getRateLimitRequestsPerMinute() {
        return config.getJsonObject("server.rateLimit", new JsonObject()).getInteger("requestsPerMinute", 100);
    }
    
    public int getRateLimitBurstSize() {
        return config.getJsonObject("server.rateLimit", new JsonObject()).getInteger("burstSize", 20);
    }
    
    // Security configuration
    public int getPasswordMinLength() {
        return config.getJsonObject("security", new JsonObject()).getInteger("passwordMinLength", 8);
    }
    
    public boolean isPasswordRequireUppercase() {
        return config.getJsonObject("security", new JsonObject()).getBoolean("passwordRequireUppercase", true);
    }
    
    public boolean isPasswordRequireLowercase() {
        return config.getJsonObject("security", new JsonObject()).getBoolean("passwordRequireLowercase", true);
    }
    
    public boolean isPasswordRequireNumbers() {
        return config.getJsonObject("security", new JsonObject()).getBoolean("passwordRequireNumbers", true);
    }
    
    public boolean isPasswordRequireSpecialChars() {
        return config.getJsonObject("security", new JsonObject()).getBoolean("passwordRequireSpecialChars", true);
    }
    
    public int getSessionTimeout() {
        return config.getJsonObject("security", new JsonObject()).getInteger("sessionTimeout", 1800);
    }
    
    public int getMaxLoginAttempts() {
        return config.getJsonObject("security", new JsonObject()).getInteger("maxLoginAttempts", 5);
    }
    
    public int getLockoutDuration() {
        return config.getJsonObject("security", new JsonObject()).getInteger("lockoutDuration", 900);
    }
}

