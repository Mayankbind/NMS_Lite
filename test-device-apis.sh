#!/bin/bash

# Test script for Device Management APIs
# This script demonstrates how to use the device management endpoints

# Configuration
BASE_URL="http://localhost:8080"
API_BASE="$BASE_URL/api"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Global variables
TOKEN=""
CREDENTIAL_ID=""
DEVICE_ID=""

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

# Function to get all devices
get_all_devices() {
    print_status "Getting all devices..."
    
    DEVICES_RESPONSE=$(curl -s -X GET "$API_BASE/devices" \
        -H "Authorization: Bearer $TOKEN")
    
    if echo "$DEVICES_RESPONSE" | grep -q '"success":true'; then
        print_success "Retrieved devices successfully"
        echo "$DEVICES_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to get devices"
        echo "$DEVICES_RESPONSE"
        return 1
    fi
}

# Function to create a device
create_device() {
    print_status "Creating a new device..."
    
    DEVICE_RESPONSE=$(curl -s -X POST "$API_BASE/devices" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d "{
            \"hostname\": \"test-server-01\",
            \"ipAddress\": \"192.168.1.100\",
            \"deviceType\": \"linux\",
            \"osInfo\": {
                \"os\": \"Ubuntu 20.04\",
                \"kernel\": \"5.4.0-42-generic\",
                \"architecture\": \"x86_64\"
            },
            \"credentialProfileId\": \"$CREDENTIAL_ID\",
            \"status\": \"online\"
        }")
    
    if echo "$DEVICE_RESPONSE" | grep -q '"success":true'; then
        DEVICE_ID=$(echo "$DEVICE_RESPONSE" | jq -r '.device.id')
        print_success "Device created with ID: $DEVICE_ID"
        echo "$DEVICE_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to create device"
        echo "$DEVICE_RESPONSE"
        return 1
    fi
}

# Function to get device by ID
get_device_by_id() {
    print_status "Getting device by ID: $DEVICE_ID"
    
    DEVICE_RESPONSE=$(curl -s -X GET "$API_BASE/devices/$DEVICE_ID" \
        -H "Authorization: Bearer $TOKEN")
    
    if echo "$DEVICE_RESPONSE" | grep -q '"success":true'; then
        print_success "Retrieved device successfully"
        echo "$DEVICE_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to get device"
        echo "$DEVICE_RESPONSE"
        return 1
    fi
}

# Function to update device
update_device() {
    print_status "Updating device: $DEVICE_ID"
    
    UPDATE_RESPONSE=$(curl -s -X PUT "$API_BASE/devices/$DEVICE_ID" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d "{
            \"hostname\": \"test-server-01-updated\",
            \"ipAddress\": \"192.168.1.101\",
            \"deviceType\": \"linux\",
            \"osInfo\": {
                \"os\": \"Ubuntu 22.04\",
                \"kernel\": \"5.15.0-52-generic\",
                \"architecture\": \"x86_64\"
            },
            \"credentialProfileId\": \"$CREDENTIAL_ID\",
            \"status\": \"online\"
        }")
    
    if echo "$UPDATE_RESPONSE" | grep -q '"success":true'; then
        print_success "Device updated successfully"
        echo "$UPDATE_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to update device"
        echo "$UPDATE_RESPONSE"
        return 1
    fi
}

# Function to update device status
update_device_status() {
    print_status "Updating device status: $DEVICE_ID"
    
    STATUS_RESPONSE=$(curl -s -X PUT "$API_BASE/devices/$DEVICE_ID/status" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d '{
            "status": "offline"
        }')
    
    if echo "$STATUS_RESPONSE" | grep -q '"success":true'; then
        print_success "Device status updated successfully"
        echo "$STATUS_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to update device status"
        echo "$STATUS_RESPONSE"
        return 1
    fi
}

# Function to get devices by status
get_devices_by_status() {
    print_status "Getting devices by status: offline"
    
    STATUS_DEVICES_RESPONSE=$(curl -s -X GET "$API_BASE/devices/status/offline" \
        -H "Authorization: Bearer $TOKEN")
    
    if echo "$STATUS_DEVICES_RESPONSE" | grep -q '"success":true'; then
        print_success "Retrieved devices by status successfully"
        echo "$STATUS_DEVICES_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to get devices by status"
        echo "$STATUS_DEVICES_RESPONSE"
        return 1
    fi
}

# Function to search devices
search_devices() {
    print_status "Searching devices with query: 'test'"
    
    SEARCH_RESPONSE=$(curl -s -X GET "$API_BASE/devices/search?q=test" \
        -H "Authorization: Bearer $TOKEN")
    
    if echo "$SEARCH_RESPONSE" | grep -q '"success":true'; then
        print_success "Search completed successfully"
        echo "$SEARCH_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to search devices"
        echo "$SEARCH_RESPONSE"
        return 1
    fi
}

# Function to delete device
delete_device() {
    print_status "Deleting device: $DEVICE_ID"
    
    DELETE_RESPONSE=$(curl -s -X DELETE "$API_BASE/devices/$DEVICE_ID" \
        -H "Authorization: Bearer $TOKEN")
    
    if echo "$DELETE_RESPONSE" | grep -q '"success":true'; then
        print_success "Device deleted successfully"
        echo "$DELETE_RESPONSE" | jq '.'
        return 0
    else
        print_error "Failed to delete device"
        echo "$DELETE_RESPONSE"
        return 1
    fi
}

# Function to test error cases
test_error_cases() {
    print_status "Testing error cases..."
    
    # Test invalid device ID
    print_status "Testing invalid device ID..."
    INVALID_RESPONSE=$(curl -s -X GET "$API_BASE/devices/invalid-id" \
        -H "Authorization: Bearer $TOKEN")
    echo "$INVALID_RESPONSE" | jq '.'
    
    # Test missing required fields
    print_status "Testing missing required fields..."
    MISSING_FIELDS_RESPONSE=$(curl -s -X POST "$API_BASE/devices" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d '{
            "hostname": "test-server"
        }')
    echo "$MISSING_FIELDS_RESPONSE" | jq '.'
    
    # Test invalid UUID format
    print_status "Testing invalid UUID format for credentialProfileId..."
    INVALID_UUID_RESPONSE=$(curl -s -X POST "$API_BASE/devices" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d '{
            "hostname": "test-server",
            "ipAddress": "192.168.1.100",
            "credentialProfileId": "invalid-uuid-format"
        }')
    echo "$INVALID_UUID_RESPONSE" | jq '.'
    
    # Test invalid status
    print_status "Testing invalid status..."
    INVALID_STATUS_RESPONSE=$(curl -s -X GET "$API_BASE/devices/status/invalid" \
        -H "Authorization: Bearer $TOKEN")
    echo "$INVALID_STATUS_RESPONSE" | jq '.'
}

# Main execution
main() {
    echo "=========================================="
    echo "NMS Lite Device Management API Test Script"
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
    
    # Get all devices (should be empty initially)
    get_all_devices
    echo
    
    # Create a device
    if ! create_device; then
        exit 1
    fi
    echo
    
    # Get all devices (should now have one device)
    get_all_devices
    echo
    
    # Get device by ID
    get_device_by_id
    echo
    
    # Update device
    update_device
    echo
    
    # Update device status
    update_device_status
    echo
    
    # Get devices by status
    get_devices_by_status
    echo
    
    # Search devices
    search_devices
    echo
    
    # Test error cases
    test_error_cases
    echo
    
    # Delete device
    delete_device
    echo
    
    # Get all devices (should be empty again)
    get_all_devices
    echo
    
    print_success "Device Management API test completed!"
    echo
    print_status "You can also manually test these endpoints:"
    echo "  - GET    $API_BASE/devices"
    echo "  - GET    $API_BASE/devices/{id}"
    echo "  - POST   $API_BASE/devices"
    echo "  - PUT    $API_BASE/devices/{id}"
    echo "  - DELETE $API_BASE/devices/{id}"
    echo "  - GET    $API_BASE/devices/status/{status}"
    echo "  - GET    $API_BASE/devices/search?q={query}"
    echo "  - PUT    $API_BASE/devices/{id}/status"
}

# Run main function
main "$@"