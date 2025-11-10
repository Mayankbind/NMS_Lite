# Refactoring Database Pool Initialization

## Problem

We have duplicate `initializeDatabase()` methods in both `Main.java` and `DiscoveryWorkerVerticle.java`.

## Solution

Create a shared `DatabasePoolFactory` utility class that both verticles can use, while **still maintaining separate pool instances** for isolation.

---

## Step 1: Create DatabasePoolFactory

**File**: `backend/src/main/java/com/nms/utils/DatabasePoolFactory.java`

```java
package com.nms.utils;

import com.nms.config.ApplicationConfig;
import com.nms.config.DatabaseConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabasePoolFactory {
    private static final Logger logger = LoggerFactory.getLogger(DatabasePoolFactory.class);
    
    public static Future<PgPool> createDatabasePool(Vertx vertx, JsonObject config) {
        try {
            ApplicationConfig appConfig = new ApplicationConfig(config);
            DatabaseConfig dbConfig = new DatabaseConfig(appConfig.getConfig());
            PgPool pgPool = dbConfig.createPgPool(vertx);
            
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
    
    public static Future<PgPool> createDatabasePool(Vertx vertx, JsonObject config, String logPrefix) {
        try {
            ApplicationConfig appConfig = new ApplicationConfig(config);
            DatabaseConfig dbConfig = new DatabaseConfig(appConfig.getConfig());
            PgPool pgPool = dbConfig.createPgPool(vertx);
            
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
```

---

## Step 2: Update Main.java

**Before:**
```java
private Future<PgPool> initializeDatabase(ApplicationConfig appConfig) {
    try {
        DatabaseConfig dbConfig = new DatabaseConfig(appConfig.getConfig());
        PgPool pgPool = dbConfig.createPgPool(vertx);
        
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
```

**After:**
```java
private Future<PgPool> initializeDatabase(ApplicationConfig appConfig) {
    return DatabasePoolFactory.createDatabasePool(vertx, config(), "Main Verticle");
}
```

**Full Updated Method:**
```java
.compose(config -> {
    logger.info("Configuration loaded successfully");
    ApplicationConfig appConfig = new ApplicationConfig(config);
    
    // Initialize database using factory
    return DatabasePoolFactory.createDatabasePool(vertx, config, "Main Verticle");
})
```

---

## Step 3: Update DiscoveryWorkerVerticle.java

**Before:**
```java
private Future<PgPool> initializeDatabase(ApplicationConfig appConfig) {
    try {
        DatabaseConfig dbConfig = new DatabaseConfig(appConfig.getConfig());
        PgPool pgPool = dbConfig.createPgPool(vertx);
        
        return pgPool.query("SELECT 1")
            .execute()
            .map(result -> {
                logger.info("Discovery Worker: Database connection established successfully");
                return pgPool;
            });
    } catch (Exception e) {
        logger.error("Discovery Worker: Failed to initialize database configuration", e);
        return Future.failedFuture(e);
    }
}
```

**After:**
```java
private Future<PgPool> initializeDatabase(ApplicationConfig appConfig) {
    return DatabasePoolFactory.createDatabasePool(vertx, config(), "Discovery Worker");
}
```

**Full Updated Method:**
```java
.compose(config -> {
    logger.info("Discovery Worker: Configuration loaded successfully");
    ApplicationConfig appConfig = new ApplicationConfig(config);
    
    // Initialize database using factory
    return DatabasePoolFactory.createDatabasePool(vertx, config, "Discovery Worker");
})
```

---

## Important: Separate Pool Instances

### Key Point

Even though we use the same factory method, **each call creates a NEW pool instance**:

```java
// Main Verticle
PgPool mainPool = DatabasePoolFactory.createDatabasePool(vertx, config);
// Creates: NEW pool instance (e.g., Pool #1)

// Worker Verticle Instance 1
PgPool workerPool1 = DatabasePoolFactory.createDatabasePool(vertx, config);
// Creates: NEW pool instance (e.g., Pool #2)

// Worker Verticle Instance 2
PgPool workerPool2 = DatabasePoolFactory.createDatabasePool(vertx, config);
// Creates: NEW pool instance (e.g., Pool #3)
```

**Result**: 3 separate pool instances (1 for Main, 2 for Worker instances)

---

## Benefits of Refactoring

### 1. Eliminates Code Duplication
- ✅ Single source of truth for pool creation
- ✅ Easier to maintain and update
- ✅ Consistent pool configuration

### 2. Maintains Isolation
- ✅ Each verticle still gets its own pool instance
- ✅ No sharing of pools between verticles
- ✅ Independent resource management

### 3. Better Maintainability
- ✅ Changes to pool configuration in one place
- ✅ Easier to add new verticles (just call factory)
- ✅ Centralized logging and error handling

### 4. Easier Testing
- ✅ Can mock factory for testing
- ✅ Can test pool creation logic independently
- ✅ Consistent pool creation across tests

---

## Complete Refactored Code

### Main.java (Updated)

```java
.compose(config -> {
    logger.info("Configuration loaded successfully");
    
    // Initialize database using factory
    return DatabasePoolFactory.createDatabasePool(vertx, config, "Main Verticle");
})
.compose((PgPool pgPool) -> {
    // Deploy Discovery Worker Verticle first
    return deployDiscoveryWorkerVerticle()
        .compose(deploymentId -> {
            logger.info("Discovery Worker Verticle deployed with ID: {}", deploymentId);
            
            // Initialize services with main pool
            var databaseService = new DatabaseService(pgPool);
            // ... rest of initialization
        });
})
```

### DiscoveryWorkerVerticle.java (Updated)

```java
.compose(config -> {
    logger.info("Discovery Worker: Configuration loaded successfully");
    
    // Initialize database using factory (creates NEW pool instance)
    return DatabasePoolFactory.createDatabasePool(vertx, config, "Discovery Worker");
})
.compose(pgPool -> {
    // Initialize encryption utility
    String encryptionKey = config().getString("encryption.key", "default-encryption-key-change-in-production");
    this.encryptionUtils = new EncryptionUtils(encryptionKey);
    
    // Initialize discovery service with worker pool
    this.discoveryService = new DiscoveryService(pgPool, vertx, encryptionUtils);
    
    // Register Event Bus consumers
    registerEventBusConsumers();
    
    logger.info("Discovery Worker Verticle started successfully");
    startPromise.complete();
    return Future.succeededFuture();
})
```

---

## Verification

### Check That Pools Are Separate

Add logging to verify separate pools:

```java
// In DatabasePoolFactory
logger.info("Creating database pool. Pool ID: {}", System.identityHashCode(pgPool));
```

**Expected Output:**
```
Main Verticle: Creating database pool. Pool ID: 123456789
Discovery Worker: Creating database pool. Pool ID: 987654321
Discovery Worker: Creating database pool. Pool ID: 555555555
```

**Different Pool IDs = Separate Pool Instances** ✅

---

## Summary

### Before Refactoring:
- ❌ Duplicate `initializeDatabase()` methods
- ❌ Code duplication
- ✅ Separate pools (intentional)

### After Refactoring:
- ✅ Shared `DatabasePoolFactory` utility
- ✅ No code duplication
- ✅ Separate pools (still maintained)
- ✅ Better maintainability
- ✅ Easier to test

### Key Points:
1. **Factory creates NEW pool instances** (not shared)
2. **Each verticle gets its own pool** (isolation maintained)
3. **Code duplication eliminated** (shared utility)
4. **Better maintainability** (single source of truth)

---

## Conclusion

We can refactor to eliminate code duplication while **maintaining separate pool instances** for isolation. The `DatabasePoolFactory` utility provides:
- ✅ Code reuse
- ✅ Isolation (separate pools)
- ✅ Maintainability
- ✅ Testability

This is the best of both worlds: **no duplication** and **proper isolation**.

