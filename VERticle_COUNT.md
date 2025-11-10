# Verticle Count in NMS Lite Application

## Total Verticles Deployed: **3 Instances**

### Breakdown:

1. **Main Verticle** - 1 instance
   - Type: Standard Verticle (Event Loop Thread)
   - Purpose: HTTP server, routing, API handling
   - Deployment: Line 57 in `Main.java`

2. **DiscoveryWorkerVerticle** - 2 instances (default)
   - Type: Worker Verticle (Worker Thread Pool)
   - Purpose: Discovery operations (network scanning, SSH)
   - Deployment: Line 159 in `Main.java`
   - Configuration: `discovery.worker.instances: 2` in `application.yml`

## Verticle Types: **2 Types**

1. `Main` - Standard verticle
2. `DiscoveryWorkerVerticle` - Worker verticle

## Configuration

```yaml
discovery:
  worker:
    instances: 2      # 2 instances of DiscoveryWorkerVerticle
    poolSize: 4       # 4 worker threads per instance
```

**Total Worker Threads**: 2 instances × 4 threads = **8 worker threads**

## Deployment Flow

```
1. Main.java main() method
   └─> Deploys Main Verticle (1 instance)
       └─> Main.start() method
           └─> Deploys DiscoveryWorkerVerticle (2 instances)
```

## Summary Table

| Verticle Type | Instances | Thread Type | Purpose |
|---------------|-----------|-------------|---------|
| Main | 1 | Event Loop | HTTP Server, API Routing |
| DiscoveryWorkerVerticle | 2 | Worker Pool | Discovery Operations |
| **Total** | **3** | - | - |

## Note

- The number of `DiscoveryWorkerVerticle` instances can be changed in `application.yml`
- Each `DiscoveryWorkerVerticle` instance runs in its own worker thread pool
- Total worker threads = instances × poolSize (default: 2 × 4 = 8 threads)

