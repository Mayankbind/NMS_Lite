# NMS Lite - Network Monitoring System

A lightweight Network Monitoring System built with Java Vert.x, Go, and PostgreSQL, designed for monitoring SSH devices with comprehensive metrics collection.

## Features

- **HTTP Server**: Java Vert.x with JWT authentication and comprehensive security features
- **Device Monitoring**: SSH device support with CPU, Memory, Disk, and Network metrics
- **Configurable Polling**: Timer-based metrics collection with configurable intervals
- **Database Storage**: PostgreSQL for storing device information and metrics
- **REST APIs**: Complete API for credential management, device discovery, and monitoring
- **Security**: JWT authentication, rate limiting, CORS, and security headers

## Technology Stack

- **Backend**: Java 17 + Vert.x 4.4.6
- **Database**: PostgreSQL 13+
- **Polling Engine**: Go (to be implemented)
- **Authentication**: JWT with RS256/HS256
- **Build Tool**: Maven 3.8+

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.8 or higher
- PostgreSQL 13 or higher
- Go 1.19 or higher (for polling engine)

### Database Setup

1. Create PostgreSQL database:
```sql
CREATE DATABASE nms_lite;
CREATE USER nms_user WITH PASSWORD 'nms_password';
GRANT ALL PRIVILEGES ON DATABASE nms_lite TO nms_user;
```

2. Initialize database schema:
```bash
psql -U nms_user -d nms_lite -f database/init/schema.sql
```

### Backend Setup

1. Navigate to backend directory:
```bash
cd backend
```

2. Build the application:
```bash
mvn clean package
```

3. Run the application:
```bash
java -jar target/nms-lite-backend-1.0.0.jar
```

The application will start on `http://localhost:8080`

### Configuration

Edit `backend/src/main/resources/application.yml` to configure:

- Server port and host
- Database connection details
- JWT secret and expiration
- CORS settings
- Rate limiting
- Security settings

## API Endpoints

### Authentication
- `POST /api/auth/login` - User login
- `POST /api/auth/refresh` - Refresh access token
- `POST /api/auth/logout` - User logout

### Health Checks
- `GET /health` - Basic health check
- `GET /ready` - Readiness check
- `GET /live` - Liveness check

### Device Management (Protected)
- `GET /api/devices` - List all devices
- `POST /api/devices` - Add new device
- `GET /api/devices/{id}` - Get device details
- `PUT /api/devices/{id}` - Update device
- `DELETE /api/devices/{id}` - Delete device

### Credential Profiles (Protected)
- `GET /api/credentials` - List credential profiles
- `POST /api/credentials` - Create credential profile
- `GET /api/credentials/{id}` - Get credential profile
- `PUT /api/credentials/{id}` - Update credential profile
- `DELETE /api/credentials/{id}` - Delete credential profile

### Discovery (Protected)
- `POST /api/discovery/start` - Start device discovery
- `GET /api/discovery/status/{jobId}` - Get discovery status
- `GET /api/discovery/results/{jobId}` - Get discovery results

## Default Credentials

- **Username**: admin
- **Password**: admin123
- **Role**: admin

## Security Features

- JWT-based authentication with configurable expiration
- Password hashing using BCrypt
- Rate limiting with token bucket algorithm
- CORS configuration
- Security headers (X-Frame-Options, CSP, etc.)
- Input validation and sanitization
- Account lockout after failed login attempts

## Development

### Project Structure

```
NMS_lite1/
├── backend/                    # Java Vert.x application
│   ├── src/main/java/com/nms/
│   │   ├── Main.java          # Main application class
│   │   ├── config/            # Configuration classes
│   │   ├── handlers/          # Request handlers
│   │   ├── middleware/        # Middleware components
│   │   ├── services/          # Business logic services
│   │   └── utils/             # Utility classes
│   ├── src/main/resources/
│   │   ├── application.yml    # Application configuration
│   │   └── logback.xml        # Logging configuration
│   └── pom.xml               # Maven dependencies
├── database/
│   └── init/
│       └── schema.sql        # Database schema
└── README.md
```

### Building

```bash
# Build the project
mvn clean package

# Run tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Monitoring

The application provides several monitoring endpoints:

- **Health Check**: `/health` - Overall application health
- **Readiness**: `/ready` - Application readiness for traffic
- **Liveness**: `/live` - Application liveness

## Logging

Logs are written to:
- Console (INFO level and above)
- `logs/nms-lite.log` (all levels)
- `logs/nms-lite-error.log` (ERROR level only)

Log rotation is configured with:
- Daily rotation
- 100MB max file size
- 30 days retention
- 3GB total size cap

## Next Steps

1. **Polling Engine**: Implement Go-based polling engine for device metrics collection
2. **Device Discovery**: Add network scanning and device discovery functionality
3. **Metrics Storage**: Implement time-series data storage and retrieval
4. **Alerting**: Add threshold-based alerting system
5. **Web UI**: Create web-based user interface

## License

This project is licensed under the MIT License.

