#!/bin/bash

# NMS Lite Startup Script
# This script builds and starts the NMS Lite application

set -e

echo "=== NMS Lite Startup Script ==="
echo "Starting NMS Lite Network Monitoring System..."
echo

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed. Please install Java 17 or higher."
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed. Please install Maven 3.8 or higher."
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "Error: Java 17 or higher is required. Current version: $JAVA_VERSION"
    exit 1
fi

echo "✓ Java version check passed: $(java -version 2>&1 | head -n 1)"

# Check if PostgreSQL is running
if ! pg_isready -q; then
    echo "Warning: PostgreSQL is not running. Please start PostgreSQL before running the application."
    echo "You can start PostgreSQL with: sudo systemctl start postgresql"
    echo
fi

# Build the application
echo "Building NMS Lite Backend..."
cd backend
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Error: Build failed. Please check the error messages above."
    exit 1
fi

echo "✓ Build completed successfully"
echo

# Check if JAR file exists
JAR_FILE="target/nms-lite-backend-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    exit 1
fi

echo "Starting NMS Lite Backend Service..."
echo "Application will be available at: http://localhost:8080"
echo "Health check: http://localhost:8080/health"
echo "API documentation: See README.md for API endpoints"
echo
echo "Press Ctrl+C to stop the application"
echo

# Start the application
java -jar "$JAR_FILE"

