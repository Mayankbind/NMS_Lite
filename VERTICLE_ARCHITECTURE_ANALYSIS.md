# Verticle Architecture Analysis for NMS Lite

## Current Architecture: Single Main Verticle

### Why You Currently Have One Main Verticle

1. **Simplicity**: Single deployment unit, easier to develop and debug
2. **Smaller Scale**: Current application size doesn't require complex deployment
3. **Shared Resources**: All services share the same database connection pool and Vertx instance
4. **Tight Coupling**: Services are closely related and share context

### Current Limitations

1. **Blocking Operations**: DiscoveryService uses `executeBlocking()` which can block the event loop if not carefully managed
2. **Resource Contention**: All HTTP requests and background tasks compete for the same event loop
3. **Scaling Limitations**: Cannot independently scale different components
4. **Failure Isolation**: If one component fails, it could affect the entire application
5. **Deployment Complexity**: Cannot update or restart individual components independently

---

## Benefits of Multiple Verticles

### 1. **Better Resource Management**
- **Worker Verticles**: Isolate blocking operations (network scanning, SSH connections)
- **Event Loop Protection**: Keep HTTP server responsive even during heavy discovery operations
- **Independent Scaling**: Scale discovery workers separately from HTTP server

### 2. **Improved Isolation**
- **Failure Isolation**: If discovery service crashes, HTTP server continues to serve requests
- **Independent Lifecycle**: Start, stop, and update components independently
- **Better Testing**: Test components in isolation

### 3. **Performance Optimization**
- **Parallel Processing**: Run multiple discovery jobs in parallel across worker verticles
- **Load Distribution**: Distribute work across multiple event loops
- **Resource Dedication**: Dedicate resources to specific tasks

### 4. **Scalability**
- **Horizontal Scaling**: Deploy multiple instances of specific verticles
- **Vertical Scaling**: Scale CPU-intensive operations independently
- **Clustering Support**: Distribute verticles across cluster nodes

### 5. **Maintainability**
- **Separation of Concerns**: Clear boundaries between components
- **Easier Debugging**: Isolate issues to specific verticles
- **Modular Development**: Teams can work on different verticles independently

---

## Recommended Architecture for NMS Lite

### Option 1: Multi-Verticle Architecture (Recommended for Production)

```
┌─────────────────────────────────────────────────────────────┐
│                    Main Verticle (HTTP Server)              │
│  - HTTP Server (Port 8080)                                  │
│  - Routing & Middleware                                      │
│  - Request Handling                                          │
│  - Service Orchestration                                     │
└─────────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
        ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Discovery   │  │   Polling    │  │   Metrics    │
│  Worker      │  │   Worker     │  │   Worker     │
│  Verticle    │  │   Verticle   │  │   Verticle   │
│              │  │              │  │              │
│  - Network   │  │  - Device    │  │  - Metrics   │
│    Scanning  │  │    Polling   │  │    Collection│
│  - SSH       │  │  - Data      │  │  - Aggregation│
│    Testing   │  │    Storage   │  │  - Retention │
│  - Device    │  │              │  │              │
│    Discovery │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
```

### Verticle Breakdown

#### 1. **Main Verticle (HTTP Server)**
- **Type**: Standard Verticle (Event Loop)
- **Responsibilities**:
  - HTTP server setup and routing
  - Request handling and authentication
  - Service orchestration
  - API endpoints
- **Deployment**: 1 instance (or multiple for load balancing)

#### 2. **Discovery Worker Verticle**
- **Type**: Worker Verticle (Dedicated Thread Pool)
- **Responsibilities**:
  - Network scanning (ping sweep)
  - SSH connection testing
  - Device information gathering
  - Background job processing
- **Deployment**: Multiple instances for parallel processing
- **Configuration**: Configurable worker pool size

#### 3. **Polling Worker Verticle** (Future)
- **Type**: Worker Verticle
- **Responsibilities**:
  - Device metrics collection
  - Scheduled polling tasks
  - Integration with Go polling engine
- **Deployment**: Multiple instances based on device count

#### 4. **Metrics Worker Verticle** (Future)
- **Type**: Worker Verticle
- **Responsibilities**:
  - Metrics aggregation
  - Data retention policies
  - Time-series data processing
- **Deployment**: 1-2 instances

---

## Implementation Strategy

### Phase 1: Extract Discovery Worker (Immediate Benefit)

**Why Start Here:**
- Discovery operations are CPU/IO intensive (network scanning, SSH)
- Currently uses `executeBlocking()` which can impact HTTP server
- Clear separation of concerns

**Benefits:**
- HTTP server remains responsive during discovery
- Can process multiple discovery jobs in parallel
- Better failure isolation

### Phase 2: Add Polling Worker (When Go Engine is Integrated)

**Why:**
- Polling operations are blocking and scheduled
- Need dedicated resources for metrics collection
- Can scale independently based on device count

### Phase 3: Add Metrics Worker (When Metrics Volume Grows)

**Why:**
- Metrics processing can be CPU-intensive
- Aggregation and retention policies need dedicated resources
- Can scale based on data volume

---

## Code Example: Multi-Verticle Setup

### Main Verticle (HTTP Server)

```java
public class MainVerticle extends AbstractVerticle {
    
    @Override
    public void start(Promise<Void> startPromise) {
        // Initialize services
        PgPool pgPool = initializeDatabase();
        
        // Deploy Discovery Worker Verticle
        DeploymentOptions discoveryOptions = new DeploymentOptions()
            .setWorker(true)
            .setInstances(4) // 4 worker instances for parallel processing
            .setWorkerPoolName("discovery-worker-pool")
            .setWorkerPoolSize(8);
        
        vertx.deployVerticle(DiscoveryWorkerVerticle.class.getName(), discoveryOptions)
            .compose(id -> {
                logger.info("Discovery worker deployed: {}", id);
                // Deploy HTTP server
                return setupHttpServer(pgPool);
            })
            .onSuccess(server -> {
                logger.info("HTTP server started");
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }
}
```

### Discovery Worker Verticle

```java
public class DiscoveryWorkerVerticle extends AbstractVerticle {
    
    private DiscoveryService discoveryService;
    
    @Override
    public void start(Promise<Void> startPromise) {
        // Initialize services
        PgPool pgPool = getPgPool(); // Shared connection pool
        EncryptionUtils encryptionUtils = getEncryptionUtils();
        
        discoveryService = new DiscoveryService(pgPool, vertx, encryptionUtils);
        
        // Register event bus consumer for discovery jobs
        vertx.eventBus().consumer("discovery.start", message -> {
            JsonObject jobRequest = (JsonObject) message.body();
            discoveryService.startDiscovery(jobRequest, userId)
                .onSuccess(jobId -> message.reply(jobId.toString()))
                .onFailure(error -> message.fail(500, error.getMessage()));
        });
        
        startPromise.complete();
    }
}
```

### Communication via Event Bus

```java
// In Main Verticle (HTTP Handler)
vertx.eventBus().request("discovery.start", discoveryRequest)
    .onSuccess(reply -> {
        UUID jobId = UUID.fromString(reply.body().toString());
        ctx.response().end(new JsonObject().put("jobId", jobId).encode());
    })
    .onFailure(error -> {
        ctx.response().setStatusCode(500).end(error.getMessage());
    });
```

---

## When to Use Multiple Verticles

### ✅ Use Multiple Verticles When:

1. **Blocking Operations**: You have CPU/IO intensive operations that block the event loop
2. **Independent Scaling**: Different components need different scaling strategies
3. **Failure Isolation**: One component's failure shouldn't affect others
4. **Resource Dedication**: Specific tasks need dedicated resources
5. **Parallel Processing**: Need to process multiple tasks in parallel
6. **Complex Architecture**: Application is large enough to benefit from separation

### ❌ Stick with Single Verticle When:

1. **Simple Application**: Small application with minimal complexity
2. **No Blocking Operations**: All operations are non-blocking
3. **Tight Coupling**: Components are tightly coupled and share significant state
4. **Development Phase**: Early development where simplicity is more important
5. **Low Traffic**: Application handles low traffic and doesn't need scaling

---

## Recommendations for NMS Lite

### Immediate Actions (Current State)

1. **Keep Single Verticle for Now** if:
   - Application is in early development
   - Traffic is low
   - Team is small
   - Simplicity is prioritized

2. **Consider Extracting Discovery Worker** if:
   - Discovery operations are impacting HTTP server performance
   - You're processing multiple discovery jobs
   - Users report slow API responses during discovery

### Future Actions (As You Scale)

1. **Extract Discovery Worker** when:
   - Discovery jobs become frequent
   - Network scanning impacts HTTP server
   - You need parallel discovery processing

2. **Add Polling Worker** when:
   - Go polling engine is integrated
   - You have many devices to poll
   - Polling operations impact other services

3. **Add Metrics Worker** when:
   - Metrics volume grows significantly
   - Aggregation becomes CPU-intensive
   - You need dedicated resources for metrics processing

---

## Performance Considerations

### Single Verticle
- **Pros**: Lower overhead, simpler deployment, shared resources
- **Cons**: Resource contention, blocking operations affect all services, limited scaling

### Multiple Verticles
- **Pros**: Better isolation, independent scaling, dedicated resources, parallel processing
- **Cons**: Higher overhead, more complex deployment, need event bus communication

### Best Practice
- Start with single verticle for simplicity
- Extract workers when you identify bottlenecks
- Use worker verticles for blocking operations
- Use standard verticles for non-blocking operations
- Scale based on actual needs, not theoretical needs

---

## Conclusion

**Current State**: Single verticle is acceptable for your current scale and complexity.

**Recommended Path**:
1. **Now**: Keep single verticle, but optimize `executeBlocking()` usage
2. **Near Future**: Extract Discovery Worker when discovery becomes a bottleneck
3. **Future**: Add Polling and Metrics workers as you scale

**Key Principle**: Extract verticles when you have a clear benefit (performance, isolation, scalability), not just because it's "better architecture."

---

## Next Steps

1. **Monitor Performance**: Track HTTP server response times during discovery
2. **Identify Bottlenecks**: Use Vert.x metrics to identify blocking operations
3. **Plan Extraction**: Design worker verticle interfaces before implementing
4. **Test Incrementally**: Extract one verticle at a time and measure impact
5. **Document Decisions**: Keep track of why you made architectural decisions

