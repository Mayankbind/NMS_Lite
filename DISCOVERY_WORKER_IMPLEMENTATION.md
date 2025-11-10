# Discovery Worker Verticle Implementation

## Overview

This document describes the implementation of a separate worker verticle for the Discovery Service in the NMS Lite application. This architectural change improves performance, isolation, and scalability by moving CPU/IO-intensive discovery operations to dedicated worker threads.

## Architecture Changes

### Before (Single Verticle)
```
┌─────────────────────────────────────┐
│      Main Verticle (Event Loop)     │
│  - HTTP Server                       │
│  - Discovery Service (blocking ops)  │
│  - All other services                │
└─────────────────────────────────────┘
```

**Problems:**
- Blocking operations (network scanning, SSH) could block the event loop
- HTTP server could become unresponsive during discovery
- No isolation between HTTP handling and discovery processing
- Limited scalability for discovery operations

### After (Multi-Verticle)
```
┌─────────────────────────────────────┐
│      Main Verticle (Event Loop)     │
│  - HTTP Server                       │
│  - DiscoveryServiceProxy             │
│  - Other services                    │
└─────────────────────────────────────┘
              │
              │ Event Bus
              ▼
┌─────────────────────────────────────┐
│  DiscoveryWorkerVerticle (Worker)   │
│  - DiscoveryService                  │
│  - Network Scanning                  │
│  - SSH Connections                   │
│  - Device Discovery                  │
└─────────────────────────────────────┘
```

**Benefits:**
- HTTP server remains responsive during discovery
- Blocking operations run in worker threads
- Better isolation and failure handling
- Can scale discovery workers independently
- Parallel processing of multiple discovery jobs

## Implementation Details

### 1. DiscoveryWorkerVerticle

**Location:** `backend/src/main/java/com/nms/verticles/DiscoveryWorkerVerticle.java`

**Characteristics:**
- Extends `AbstractVerticle`
- Deployed as a **worker verticle** (runs in worker thread pool)
- Creates its own database connection pool
- Listens on Event Bus addresses for discovery requests
- Handles all blocking operations (network scanning, SSH)

**Event Bus Addresses:**
- `discovery.start` - Start a new discovery job
- `discovery.status` - Get discovery job status
- `discovery.results` - Get discovery results
- `discovery.cancel` - Cancel a discovery job

### 2. DiscoveryServiceProxy

**Location:** `backend/src/main/java/com/nms/services/DiscoveryServiceProxy.java`

**Characteristics:**
- Implements `IDiscoveryService` interface
- Runs in the main verticle (event loop)
- Communicates with worker verticle via Event Bus
- Non-blocking, asynchronous operations
- Transparent to handlers (same interface as DiscoveryService)

### 3. IDiscoveryService Interface

**Location:** `backend/src/main/java/com/nms/services/IDiscoveryService.java`

**Purpose:**
- Common interface for `DiscoveryService` and `DiscoveryServiceProxy`
- Allows interchangeable usage
- Enables easy testing and mocking

### 4. Configuration

**Location:** `backend/src/main/resources/application.yml`

**Configuration Options:**
```yaml
discovery:
  worker:
    instances: 2      # Number of worker verticle instances
    poolSize: 4       # Worker thread pool size per instance
```

**Total Worker Threads:** `instances × poolSize = 2 × 4 = 8 threads`

### 5. Main Verticle Changes

**Location:** `backend/src/main/java/com/nms/Main.java`

**Changes:**
- Deploys `DiscoveryWorkerVerticle` before starting HTTP server
- Uses `DiscoveryServiceProxy` instead of `DiscoveryService`
- Configures worker verticle deployment options
- Maintains backward compatibility with handlers

## Communication Flow

### Starting a Discovery Job

```
1. HTTP Request → Main Verticle
2. DiscoveryHandler → DiscoveryServiceProxy
3. DiscoveryServiceProxy → Event Bus (discovery.start)
4. DiscoveryWorkerVerticle → Receives message
5. DiscoveryWorkerVerticle → DiscoveryService.startDiscovery()
6. DiscoveryService → Network scanning, SSH connections (blocking)
7. DiscoveryWorkerVerticle → Event Bus reply
8. DiscoveryServiceProxy → Returns Future<UUID>
9. DiscoveryHandler → HTTP Response
```

### Getting Discovery Status

```
1. HTTP Request → Main Verticle
2. DiscoveryHandler → DiscoveryServiceProxy
3. DiscoveryServiceProxy → Event Bus (discovery.status)
4. DiscoveryWorkerVerticle → Receives message
5. DiscoveryWorkerVerticle → DiscoveryService.getDiscoveryStatus()
6. DiscoveryWorkerVerticle → Event Bus reply
7. DiscoveryServiceProxy → Returns Future<DiscoveryJob>
8. DiscoveryHandler → HTTP Response
```

## Benefits

### 1. Performance
- **HTTP Server Responsiveness**: HTTP server remains responsive during discovery operations
- **Parallel Processing**: Multiple discovery jobs can run in parallel across worker instances
- **Resource Isolation**: Discovery operations don't compete with HTTP handling for event loop time

### 2. Scalability
- **Independent Scaling**: Can scale discovery workers independently based on load
- **Configurable Instances**: Adjust number of worker instances based on needs
- **Configurable Pool Size**: Adjust worker thread pool size based on CPU cores

### 3. Isolation
- **Failure Isolation**: If discovery service crashes, HTTP server continues to serve requests
- **Resource Isolation**: Discovery operations have dedicated threads
- **Database Isolation**: Each verticle has its own database connection pool

### 4. Maintainability
- **Separation of Concerns**: Clear separation between HTTP handling and discovery processing
- **Easier Testing**: Can test discovery service independently
- **Easier Debugging**: Isolate issues to specific verticles

## Deployment

### Default Configuration
- **Worker Instances**: 2
- **Worker Pool Size**: 4
- **Total Worker Threads**: 8

### Tuning Recommendations

**For Low Traffic:**
```yaml
discovery:
  worker:
    instances: 1
    poolSize: 2
```

**For High Traffic:**
```yaml
discovery:
  worker:
    instances: 4
    poolSize: 8
```

**For CPU-Intensive Discovery:**
```yaml
discovery:
  worker:
    instances: 2
    poolSize: 16  # More threads for CPU-intensive operations
```

## Monitoring

### Logging
- Worker verticle logs are prefixed with "Discovery Worker:"
- Main verticle logs show deployment status
- Event Bus communication is logged for debugging

### Metrics (Future)
- Discovery job queue size
- Worker thread utilization
- Discovery job completion time
- Event Bus message latency

## Testing

### Unit Tests
- Test `DiscoveryService` directly (blocking operations)
- Test `DiscoveryServiceProxy` with mock Event Bus
- Test `DiscoveryWorkerVerticle` with embedded Event Bus

### Integration Tests
- Test end-to-end discovery flow
- Test multiple concurrent discovery jobs
- Test failure scenarios

## Migration Notes

### Backward Compatibility
- Handlers use `IDiscoveryService` interface (no changes needed)
- API endpoints remain unchanged
- Response formats remain unchanged

### Breaking Changes
- None - fully backward compatible

### Configuration Changes
- New configuration section: `discovery.worker`
- Default values work out of the box
- No required configuration changes

## Future Enhancements

### 1. Dynamic Scaling
- Scale worker instances based on queue size
- Auto-scale based on CPU utilization
- Scale down during low traffic

### 2. Priority Queues
- High-priority discovery jobs
- Low-priority discovery jobs
- Different worker pools for different priorities

### 3. Distributed Deployment
- Deploy worker verticles on different nodes
- Clustering support
- Load balancing across nodes

### 4. Metrics and Monitoring
- Prometheus metrics
- Grafana dashboards
- Alerting on queue size and latency

## Conclusion

The implementation of a separate worker verticle for discovery operations provides significant benefits in terms of performance, scalability, and isolation. The architecture is flexible, maintainable, and ready for future enhancements.

The change is fully backward compatible and requires minimal configuration. The default configuration works well for most use cases, but can be tuned based on specific requirements.

