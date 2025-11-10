# Why 2 Instances for DiscoveryWorkerVerticle?

## Quick Answer

We chose **2 instances** of `DiscoveryWorkerVerticle` to enable **parallel processing** and **load balancing** of discovery jobs through Vert.x Event Bus. This allows multiple discovery jobs to be processed simultaneously, improving throughput and system responsiveness.

---

## Detailed Explanation

### 1. Event Bus Load Balancing

When you deploy multiple instances of a verticle in Vert.x, the Event Bus automatically **load balances** messages across all instances. This is a key feature that enables parallel processing.

#### How It Works:

```
Event Bus Message → Vert.x Load Balancer → Distributes to available instances
                                            ├─> Instance 1 (if available)
                                            └─> Instance 2 (if Instance 1 is busy)
```

#### Example Scenario:

```java
// 2 instances deployed
DiscoveryWorkerVerticle Instance 1
DiscoveryWorkerVerticle Instance 2

// Event Bus receives 4 discovery job requests
Request 1 → Instance 1 (processes immediately)
Request 2 → Instance 2 (processes immediately - PARALLEL!)
Request 3 → Instance 1 (queued, processes after Request 1)
Request 4 → Instance 2 (queued, processes after Request 2)
```

**Result**: Requests 1 and 2 process in parallel (2x faster than sequential)

### 2. Benefits of 2 Instances

#### A. Parallel Processing

**With 1 Instance:**
```
Discovery Job 1: Starts at T=0s, Completes at T=120s
Discovery Job 2: Waits... Starts at T=120s, Completes at T=240s
Discovery Job 3: Waits... Starts at T=240s, Completes at T=360s

Total Time for 3 jobs: 360 seconds (6 minutes) - SEQUENTIAL
```

**With 2 Instances:**
```
Discovery Job 1: Instance 1 → Starts at T=0s, Completes at T=120s
Discovery Job 2: Instance 2 → Starts at T=0s, Completes at T=120s (PARALLEL!)
Discovery Job 3: Instance 1 → Starts at T=120s, Completes at T=240s

Total Time for 3 jobs: 240 seconds (4 minutes) - PARALLEL
```

**Improvement**: 2x faster for 2 concurrent jobs, 1.5x faster for 3 jobs

#### B. Better Resource Utilization

**With 1 Instance:**
```
CPU Utilization:
  Worker Thread 1: 80% (processing job)
  Worker Thread 2: 0% (idle)
  Worker Thread 3: 0% (idle)
  Worker Thread 4: 0% (idle)
  
Average: 20% utilization
```

**With 2 Instances:**
```
CPU Utilization:
  Instance 1 - Worker Thread 1: 80% (processing job 1)
  Instance 1 - Worker Thread 2: 60% (processing job 2)
  Instance 2 - Worker Thread 1: 80% (processing job 3)
  Instance 2 - Worker Thread 2: 70% (processing job 4)
  
Average: 72.5% utilization (3.6x better)
```

#### C. Improved Throughput

**Scenario**: 10 concurrent discovery jobs

**With 1 Instance (4 threads):**
```
Jobs processed: 4 at a time
Time per batch: 120 seconds
Total batches: 3 (4 + 4 + 2)
Total time: 360 seconds (6 minutes)
```

**With 2 Instances (8 threads total):**
```
Jobs processed: 8 at a time (4 per instance)
Time per batch: 120 seconds
Total batches: 2 (8 + 2)
Total time: 240 seconds (4 minutes)
```

**Improvement**: 1.5x faster throughput

#### D. Fault Tolerance

**With 1 Instance:**
```
If Instance 1 crashes:
  - All discovery jobs fail
  - Need to redeploy entire instance
  - No redundancy
```

**With 2 Instances:**
```
If Instance 1 crashes:
  - Instance 2 continues processing
  - Event Bus routes new requests to Instance 2
  - Instance 1 can be redeployed independently
  - Better fault tolerance
```

### 3. Why Not More Instances?

#### Trade-offs to Consider:

**More Instances = More Overhead:**
- Each instance creates its own database connection pool
- Each instance consumes memory
- More instances = more context switching
- Diminishing returns after a certain point

#### Optimal Number Depends On:

1. **CPU Cores**: Should not exceed number of CPU cores
2. **Memory**: Each instance consumes memory
3. **Database Connections**: Each instance needs its own connection pool
4. **Workload**: Number of concurrent discovery jobs expected

#### Recommended Configuration:

```yaml
# Low Traffic (1-2 concurrent jobs)
discovery:
  worker:
    instances: 1
    poolSize: 2

# Medium Traffic (2-4 concurrent jobs) - DEFAULT
discovery:
  worker:
    instances: 2
    poolSize: 4

# High Traffic (4-8 concurrent jobs)
discovery:
  worker:
    instances: 4
    poolSize: 4

# Very High Traffic (8+ concurrent jobs)
discovery:
  worker:
    instances: 4
    poolSize: 8
```

### 4. Why 2 Instances Instead of 1 Instance with More Threads?

#### Option 1: 1 Instance, 8 Threads
```yaml
instances: 1
poolSize: 8
```
**Pros:**
- Simpler deployment
- Single database connection pool
- Less memory overhead

**Cons:**
- Single point of failure
- All threads in one pool (less flexibility)
- Cannot scale instances independently

#### Option 2: 2 Instances, 4 Threads Each (Chosen)
```yaml
instances: 2
poolSize: 4
```
**Pros:**
- Better fault tolerance (2 instances)
- Event Bus load balancing across instances
- Can scale instances independently
- Better isolation between instances

**Cons:**
- More memory overhead (2 connection pools)
- Slightly more complex deployment

#### Why We Chose Option 2:

1. **Fault Tolerance**: If one instance fails, the other continues
2. **Load Balancing**: Event Bus distributes load more evenly
3. **Isolation**: Each instance is isolated (better for debugging)
4. **Scalability**: Can scale instances independently (useful for clustering)

### 5. Performance Impact

#### Real-World Scenario:

**Test Case**: 5 concurrent discovery jobs, each scanning 254 IPs (takes ~120 seconds)

**With 1 Instance (4 threads):**
```
Job 1: Thread 1 → 120s
Job 2: Thread 2 → 120s
Job 3: Thread 3 → 120s
Job 4: Thread 4 → 120s
Job 5: Waits... Thread 1 → 120s (after Job 1 completes)

Total Time: 240 seconds (4 minutes)
```

**With 2 Instances (4 threads each = 8 total):**
```
Instance 1:
  Job 1: Thread 1 → 120s
  Job 2: Thread 2 → 120s
  Job 3: Thread 3 → 120s
  Job 4: Thread 4 → 120s

Instance 2:
  Job 5: Thread 1 → 120s

Total Time: 120 seconds (2 minutes)
```

**Improvement**: 2x faster (4 minutes → 2 minutes)

### 6. Configuration Tuning Guidelines

#### Factors to Consider:

1. **Number of CPU Cores**
   - Recommended: instances × poolSize ≤ CPU cores
   - Example: 4-core CPU → 2 instances × 4 threads = 8 threads (OK)
   - Example: 2-core CPU → 2 instances × 2 threads = 4 threads (OK)

2. **Expected Concurrent Jobs**
   - Low: 1-2 jobs → 1 instance, 2 threads
   - Medium: 2-4 jobs → 2 instances, 4 threads (default)
   - High: 4-8 jobs → 2 instances, 8 threads
   - Very High: 8+ jobs → 4 instances, 4 threads

3. **Memory Availability**
   - Each instance: ~50-100MB memory
   - Each database connection: ~1-2MB
   - Total: instances × (base memory + connections × 2MB)

4. **Database Connection Pool**
   - Each instance needs its own connection pool
   - Default: 20 connections per instance
   - 2 instances = 40 total connections
   - Make sure database can handle this

### 7. When to Adjust Instances

#### Increase Instances When:
- ✅ High number of concurrent discovery jobs
- ✅ Need better fault tolerance
- ✅ Have multiple CPU cores available
- ✅ Planning for clustering/distributed deployment

#### Decrease Instances When:
- ✅ Low number of concurrent discovery jobs
- ✅ Limited memory/resources
- ✅ Single CPU core
- ✅ Database connection limits

#### Increase poolSize When:
- ✅ Jobs are I/O intensive (waiting for network/SSH)
- ✅ Need more parallelism within each instance
- ✅ Have available CPU cores

#### Decrease poolSize When:
- ✅ Jobs are CPU intensive (competing for CPU)
- ✅ Limited CPU cores
- ✅ High context switching overhead

### 8. Monitoring and Optimization

#### Key Metrics to Monitor:

1. **Job Queue Length**: How many jobs are waiting?
2. **Average Job Processing Time**: How long does each job take?
3. **Instance Utilization**: Are instances being used efficiently?
4. **Thread Utilization**: Are threads being used efficiently?
5. **Database Connection Usage**: Are connections being utilized?

#### Optimization Strategy:

```yaml
# Start with default
instances: 2
poolSize: 4

# Monitor metrics
# If jobs are queuing → Increase instances or poolSize
# If instances are idle → Decrease instances
# If threads are idle → Decrease poolSize
# If CPU is saturated → Decrease instances or poolSize
```

### 9. Summary

#### Why 2 Instances?

1. **Parallel Processing**: 2 instances can process 2 jobs simultaneously
2. **Load Balancing**: Event Bus automatically distributes load
3. **Fault Tolerance**: If one instance fails, the other continues
4. **Better Resource Utilization**: Better CPU and thread utilization
5. **Scalability**: Can scale instances independently
6. **Balanced Configuration**: Good balance between performance and resource usage

#### Default Configuration:
```yaml
discovery:
  worker:
    instances: 2      # 2 instances for parallel processing
    poolSize: 4       # 4 threads per instance
    # Total: 8 worker threads
```

#### Performance Impact:
- **2x faster** for 2 concurrent jobs
- **1.5x faster** for 3-4 concurrent jobs
- **Better fault tolerance** (redundancy)
- **Better resource utilization** (72.5% vs 20%)

#### When to Change:
- **Low Traffic**: Use 1 instance, 2 threads
- **Medium Traffic**: Use 2 instances, 4 threads (default) ✅
- **High Traffic**: Use 2-4 instances, 4-8 threads
- **Very High Traffic**: Use 4 instances, 8 threads

---

## Conclusion

We chose **2 instances** as the default because it provides:
- ✅ Good balance between performance and resource usage
- ✅ Parallel processing capability (2x faster for concurrent jobs)
- ✅ Fault tolerance (redundancy)
- ✅ Event Bus load balancing
- ✅ Suitable for medium traffic workloads

The configuration is **easily tunable** via `application.yml` based on your specific workload and resources. Start with 2 instances and adjust based on monitoring and performance metrics.

