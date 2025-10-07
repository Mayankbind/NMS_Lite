# NMS Lite - Network Monitoring System Architecture Plan

## Project Overview
A lightweight Network Monitoring System (NMS) similar to Datadog/Motadata AIOps with HTTP server, device discovery, monitoring, and metrics collection capabilities.

## Technology Stack
- **Backend Framework**: Java Vert.x (reactive, high-performance)
- **Polling Engine**: Go (for device polling and metrics collection)
- **Database**: PostgreSQL
- **Authentication**: JWT (JSON Web Tokens)
- **Communication**: HTTP REST APIs + Go plugins

## System Architecture

### 1. Core Components

#### 1.1 HTTP Server (Java Vert.x)
- **Port**: Configurable (default: 8080)
- **Security Features**:
  - JWT-based authentication
  - HTTPS support with configurable certificates
  - CORS configuration
  - Rate limiting
  - Request/Response logging
  - Security headers (HSTS, X-Frame-Options, etc.)
  - Input validation and sanitization

#### 1.2 Database Layer (PostgreSQL)
- **Tables**:
  - `users` - User authentication and authorization
  - `credential_profiles` - SSH credentials for devices
  - `devices` - Discovered and monitored devices
  - `device_metrics` - Time-series metrics data
  - `monitoring_configs` - Monitoring configurations
  - `discovery_jobs` - Discovery job status and results

#### 1.3 Polling Engine (Go)
- **Plugin Architecture**: Modular Go plugins for different device types
- **Metrics Collection**: CPU, Memory, Disk, Network interfaces
- **Scheduling**: Configurable polling intervals
- **Data Storage**: Direct PostgreSQL integration

### 2. API Endpoints

#### 2.1 Authentication APIs
```
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
```

#### 2.2 Credential Profile APIs
```
GET    /api/credentials
POST   /api/credentials
GET    /api/credentials/{id}
PUT    /api/credentials/{id}
DELETE /api/credentials/{id}
```

#### 2.3 Discovery APIs
```
POST   /api/discovery/start
GET    /api/discovery/status/{jobId}
GET    /api/discovery/results/{jobId}
DELETE /api/discovery/job/{jobId}
```

#### 2.4 Device Management APIs
```
GET    /api/devices
POST   /api/devices
GET    /api/devices/{id}
PUT    /api/devices/{id}
DELETE /api/devices/{id}
GET    /api/devices/{id}/metrics
```

#### 2.5 Monitoring Configuration APIs
```
GET    /api/monitoring/configs
POST   /api/monitoring/configs
PUT    /api/monitoring/configs/{id}
DELETE /api/monitoring/configs/{id}
```

### 3. Database Schema Design

#### 3.1 Users Table
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'user',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 3.2 Credential Profiles Table
```sql
CREATE TABLE credential_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_encrypted TEXT NOT NULL,
    private_key_encrypted TEXT,
    port INTEGER DEFAULT 22,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 3.3 Devices Table
```sql
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hostname VARCHAR(255) NOT NULL,
    ip_address INET NOT NULL,
    device_type VARCHAR(50) DEFAULT 'linux',
    os_info JSONB,
    credential_profile_id UUID REFERENCES credential_profiles(id),
    status VARCHAR(20) DEFAULT 'unknown',
    last_seen TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 3.4 Device Metrics Table
```sql
CREATE TABLE device_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID REFERENCES devices(id),
    metric_type VARCHAR(50) NOT NULL, -- cpu, memory, disk, network
    metric_name VARCHAR(100) NOT NULL,
    value DECIMAL(15,4) NOT NULL,
    unit VARCHAR(20),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);
```

### 4. Go Polling Plugin Architecture

#### 4.1 Plugin Interface
```go
type DevicePlugin interface {
    Connect(device Device, credentials Credentials) error
    CollectMetrics() ([]Metric, error)
    Disconnect() error
    GetSupportedMetrics() []string
}
```

#### 4.2 SSH Device Plugin
- **Connection**: SSH client with configurable timeouts
- **Metrics Collection**:
  - CPU usage (per core and overall)
  - Memory usage (total, used, free, cached)
  - Disk usage (per mount point)
  - Network interface statistics
  - System load averages
  - Process information

#### 4.3 Plugin Configuration
- **Polling Interval**: Configurable per device (default: 60 seconds)
- **Connection Timeout**: Configurable (default: 30 seconds)
- **Retry Logic**: Exponential backoff for failed connections
- **Concurrent Polling**: Configurable worker pool size

### 5. Security Considerations

#### 5.1 JWT Configuration
- **Algorithm**: RS256 (RSA with SHA-256)
- **Token Expiry**: Configurable (default: 1 hour)
- **Refresh Token**: Longer expiry (default: 7 days)
- **Secret Management**: Environment variables or secure vault

#### 5.2 Data Encryption
- **Credentials**: AES-256 encryption for stored passwords/keys
- **Database**: TLS connection to PostgreSQL
- **API Communication**: HTTPS with TLS 1.3

#### 5.3 Access Control
- **Role-based Access**: Admin, User, Read-only roles
- **API Rate Limiting**: Per-user and global limits
- **Input Validation**: Comprehensive validation for all inputs

### 6. Configuration Management

#### 6.1 Application Configuration
```yaml
server:
  port: 8080
  host: "0.0.0.0"
  ssl:
    enabled: true
    keystore: "/path/to/keystore.jks"
    keystorePassword: "password"
  cors:
    allowedOrigins: ["*"]
    allowedMethods: ["GET", "POST", "PUT", "DELETE"]
    allowedHeaders: ["*"]

database:
  host: "localhost"
  port: 5432
  name: "nms_lite"
  username: "nms_user"
  password: "nms_password"
  ssl: true
  maxConnections: 20

jwt:
  secret: "your-secret-key"
  expiration: 3600
  refreshExpiration: 604800

polling:
  defaultInterval: 60
  maxConcurrent: 10
  timeout: 30
  retryAttempts: 3
```

### 7. Project Structure

```
NMS_lite1/
├── backend/                    # Java Vert.x application
│   ├── src/main/java/
│   │   ├── com/nms/
│   │   │   ├── Main.java
│   │   │   ├── config/
│   │   │   ├── handlers/
│   │   │   ├── services/
│   │   │   ├── models/
│   │   │   └── utils/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── logback.xml
│   ├── pom.xml
│   └── Dockerfile
├── polling-engine/             # Go polling engine
│   ├── main.go
│   ├── plugins/
│   │   ├── ssh/
│   │   └── interfaces/
│   ├── config/
│   ├── go.mod
│   └── go.sum
├── database/
│   ├── init/
│   │   └── schema.sql
│   └── migrations/
├── docker-compose.yml
├── README.md
└── ARCHITECTURE_PLAN.md
```

### 8. Implementation Phases

#### Phase 1: Core Infrastructure
1. Set up project structure
2. Configure PostgreSQL database
3. Implement basic HTTP server with Vert.x
4. Set up JWT authentication
5. Create basic API endpoints

#### Phase 2: Device Management
1. Implement credential profile management
2. Create device discovery functionality
3. Build device management APIs
4. Implement basic device status tracking

#### Phase 3: Polling Engine
1. Develop Go polling engine
2. Create SSH device plugin
3. Implement metrics collection
4. Set up data storage and retrieval

#### Phase 4: Monitoring & Alerting
1. Implement monitoring configurations
2. Create metrics visualization APIs
3. Add basic alerting capabilities
4. Performance optimization

#### Phase 5: Production Readiness
1. Security hardening
2. Performance testing
3. Documentation
4. Deployment scripts

### 9. Key Features Implementation

#### 9.1 Configurable HTTP Server
- **Port Configuration**: Environment variable or config file
- **SSL/TLS Support**: Configurable certificates and protocols
- **CORS Configuration**: Flexible cross-origin resource sharing
- **Request Size Limits**: Configurable request body size
- **Connection Pooling**: Database and HTTP connection management

#### 9.2 Device Discovery
- **Network Scanning**: IP range scanning with configurable CIDR
- **SSH Connectivity Test**: Verify SSH access with provided credentials
- **Device Fingerprinting**: OS detection and device type identification
- **Bulk Discovery**: Support for multiple credential profiles

#### 9.3 Metrics Collection
- **Real-time Metrics**: CPU, Memory, Disk, Network
- **Historical Data**: Time-series data storage
- **Data Aggregation**: Min, Max, Average calculations
- **Data Retention**: Configurable data retention policies

### 10. Monitoring & Observability

#### 10.1 Application Metrics
- **HTTP Request Metrics**: Response times, status codes, throughput
- **Database Metrics**: Connection pool, query performance
- **Polling Engine Metrics**: Success/failure rates, collection times
- **System Metrics**: JVM memory, CPU usage

#### 10.2 Logging
- **Structured Logging**: JSON format with correlation IDs
- **Log Levels**: Configurable per component
- **Log Rotation**: Size and time-based rotation
- **Centralized Logging**: Optional integration with external log systems

## Questions for Clarification

1. **Device Types**: Besides SSH devices, do you want to support other device types (SNMP, WMI, etc.) in the future?

2. **Metrics Storage**: Do you prefer time-series database (InfluxDB) or PostgreSQL for metrics storage? PostgreSQL is simpler but InfluxDB is optimized for time-series data.

3. **UI Requirements**: Do you need a web-based UI or just REST APIs? If UI is needed, what technology preference (React, Vue, Angular)?

4. **Deployment**: Do you want Docker containerization? Kubernetes deployment?

5. **Alerting**: What kind of alerting do you need (email, webhook, Slack integration)?

6. **Scale Requirements**: Expected number of devices to monitor? Concurrent users?

7. **Data Retention**: How long should metrics data be retained? Any specific retention policies?

8. **High Availability**: Do you need clustering or just single instance deployment?

9. **Integration**: Any specific integrations needed (Grafana, Prometheus, etc.)?

10. **Performance**: What are the expected response time requirements for APIs?