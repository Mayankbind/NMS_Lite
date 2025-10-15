#!/bin/bash

# Test script for Discovery APIs
# This script demonstrates how to use the discovery endpoints

# Configuration
BASE_URL="http://localhost:8080"
API_BASE="$BASE_URL/api"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Function to check if server is running
check_server() {
    print_status "Checking if server is running..."
    if curl -s "$BASE_URL/health" > /dev/null; then
        print_success "Server is running"
        return 0
    else
        print_error "Server is not running. Please start the server first."
        return 1
    fi
}

# Function to login and get token
login() {
    print_status "Logging in..."
    
    LOGIN_RESPONSE=$(curl -s -X POST "$API_BASE/auth/login" \
        -H "Content-Type: application/json" \
        -d '{
            "username": "admin",
            "password": "admin123"
        }')
    
    if echo "$LOGIN_RESPONSE" | grep -q '"success":true'; then
        TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        print_success "Login successful"
        echo "Token: $TOKEN"
        return 0
    else
        print_error "Login failed"
        echo "$LOGIN_RESPONSE"
        return 1
    fi
}

# Function to create a credential profile
create_credential_profile() {
    print_status "Creating credential profile..."
    
    CREDENTIAL_RESPONSE=$(curl -s -X POST "$API_BASE/credentials" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d '{
            "name": "Test Linux Servers",
            "username": "admin",
            "password": "admin123",
            "port": 22
        }')
    
    if echo "$CREDENTIAL_RESPONSE" | grep -q '"success":true'; then
        CREDENTIAL_ID=$(echo "$CREDENTIAL_RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
        print_success "Credential profile created with ID: $CREDENTIAL_ID"
        echo "$CREDENTIAL_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to create credential profile"
        echo "$CREDENTIAL_RESPONSE"
        return 1
    fi
}

# Function to start discovery
start_discovery() {
    print_status "Starting discovery job..."
    
    DISCOVERY_RESPONSE=$(curl -s -X POST "$API_BASE/discovery/start" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d "{
            \"name\": \"Test Network Discovery\",
            \"targetRange\": \"192.168.1.0/24\",
            \"credentialProfileId\": \"$CREDENTIAL_ID\"
        }")
    
    if echo "$DISCOVERY_RESPONSE" | grep -q '"success":true'; then
        JOB_ID=$(echo "$DISCOVERY_RESPONSE" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
        print_success "Discovery job started with ID: $JOB_ID"
        echo "$DISCOVERY_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to start discovery job"
        echo "$DISCOVERY_RESPONSE"
        return 1
    fi
}

# Function to check discovery status
check_discovery_status() {
    print_status "Checking discovery status..."
    
    STATUS_RESPONSE=$(curl -s -X GET "$API_BASE/discovery/status/$JOB_ID" \
        -H "Authorization: Bearer $TOKEN")
    
    if echo "$STATUS_RESPONSE" | grep -q '"success":true'; then
        print_success "Discovery status retrieved"
        echo "$STATUS_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to get discovery status"
        echo "$STATUS_RESPONSE"
        return 1
    fi
}

# Function to get discovery results
get_discovery_results() {
    print_status "Getting discovery results..."
    
    RESULTS_RESPONSE=$(curl -s -X GET "$API_BASE/discovery/results/$JOB_ID" \
        -H "Authorization: Bearer $TOKEN")
    
    if echo "$RESULTS_RESPONSE" | grep -q '"success":true'; then
        print_success "Discovery results retrieved"
        echo "$RESULTS_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to get discovery results"
        echo "$RESULTS_RESPONSE"
        return 1
    fi
}

# Function to cancel discovery (optional)
cancel_discovery() {
    print_status "Cancelling discovery job..."
    
    CANCEL_RESPONSE=$(curl -s -X DELETE "$API_BASE/discovery/job/$JOB_ID" \
        -H "Authorization: Bearer $TOKEN")
    
    if echo "$CANCEL_RESPONSE" | grep -q '"success":true'; then
        print_success "Discovery job cancelled"
        echo "$CANCEL_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to cancel discovery job"
        echo "$CANCEL_RESPONSE"
        return 1
    fi
}

# Main execution
main() {
    echo "=========================================="
    echo "NMS Lite Discovery API Test Script"
    echo "=========================================="
    echo
    
    # Check if jq is installed
    if ! command -v jq &> /dev/null; then
        print_warning "jq is not installed. JSON responses will not be formatted."
        print_warning "Install jq for better output formatting: sudo apt-get install jq"
        echo
    fi
    
    # Check server
    if ! check_server; then
        exit 1
    fi
    echo
    
    # Login
    if ! login; then
        exit 1
    fi
    echo
    
    # Create credential profile
    if ! create_credential_profile; then
        exit 1
    fi
    echo
    
    # Start discovery
    if ! start_discovery; then
        exit 1
    fi
    echo
    
    # Wait a moment for discovery to start
    print_status "Waiting 5 seconds for discovery to start..."
    sleep 5
    
    # Check status
    check_discovery_status
    echo
    
    # Wait for discovery to complete (this might take a while)
    print_status "Waiting 30 seconds for discovery to complete..."
    print_warning "Note: Discovery time depends on network size and device response times"
    sleep 30
    
    # Check status again
    check_discovery_status
    echo
    
    # Get results
    get_discovery_results
    echo
    
    print_success "Discovery API test completed!"
    echo
    print_status "You can also manually test these endpoints:"
    echo "  - POST $API_BASE/discovery/start"
    echo "  - GET  $API_BASE/discovery/status/{jobId}"
    echo "  - GET  $API_BASE/discovery/results/{jobId}"
    echo "  - DELETE $API_BASE/discovery/job/{jobId}"
}

# Run main function
main "$@"