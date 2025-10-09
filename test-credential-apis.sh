#!/bin/bash

# Test script for Credential Profile CRUD APIs
# This script tests the credential profile endpoints

echo "=== Testing NMS Lite Credential Profile APIs ==="
echo

# Base URL
BASE_URL="http://localhost:8080/api"

# Test 1: Health Check
echo "1. Testing Health Check..."
curl -s -X GET "$BASE_URL/../health" | jq '.' 2>/dev/null || echo "Health check response (no jq available)"
echo
echo

# Test 2: Login (to get JWT token)
echo "2. Testing Login..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}')

echo "Login response: $LOGIN_RESPONSE"
echo

# Extract JWT token
JWT_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.accessToken' 2>/dev/null)

if [ "$JWT_TOKEN" = "null" ] || [ -z "$JWT_TOKEN" ]; then
    echo "❌ Failed to get JWT token. Cannot proceed with credential API tests."
    echo "Make sure the application is running and the admin user exists in the database."
    exit 1
fi

echo "✅ JWT Token obtained: ${JWT_TOKEN:0:50}..."
echo

# Test 3: Create Credential Profile
echo "3. Testing Create Credential Profile..."
CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/credentials" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "name": "Ubuntu WiFi Devices",
    "username": "ubuntu",
    "password": "mySecurePassword123",
    "port": 22
  }')

echo "Create response: $CREATE_RESPONSE"
echo

# Extract credential profile ID
CREDENTIAL_ID=$(echo "$CREATE_RESPONSE" | jq -r '.data.id' 2>/dev/null)

if [ "$CREDENTIAL_ID" = "null" ] || [ -z "$CREDENTIAL_ID" ]; then
    echo "❌ Failed to create credential profile. Cannot proceed with other tests."
    exit 1
fi

echo "✅ Credential Profile created with ID: $CREDENTIAL_ID"
echo

# Test 4: Get All Credential Profiles
echo "4. Testing Get All Credential Profiles..."
curl -s -X GET "$BASE_URL/credentials" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.' 2>/dev/null || echo "Get all response (no jq available)"
echo

# Test 5: Get Specific Credential Profile
echo "5. Testing Get Specific Credential Profile..."
curl -s -X GET "$BASE_URL/credentials/$CREDENTIAL_ID" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.' 2>/dev/null || echo "Get specific response (no jq available)"
echo

# Test 6: Update Credential Profile
echo "6. Testing Update Credential Profile..."
UPDATE_RESPONSE=$(curl -s -X PUT "$BASE_URL/credentials/$CREDENTIAL_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "name": "Updated Ubuntu WiFi Devices",
    "username": "ubuntu",
    "password": "newSecurePassword456",
    "port": 22
  }')

echo "Update response: $UPDATE_RESPONSE"
echo

# Test 7: Delete Credential Profile
echo "7. Testing Delete Credential Profile..."
DELETE_RESPONSE=$(curl -s -X DELETE "$BASE_URL/credentials/$CREDENTIAL_ID" \
  -H "Authorization: Bearer $JWT_TOKEN")

echo "Delete response: $DELETE_RESPONSE"
echo

# Test 8: Verify Deletion
echo "8. Testing Get All Credential Profiles (after deletion)..."
curl -s -X GET "$BASE_URL/credentials" \
  -H "Authorization: Bearer $JWT_TOKEN" | jq '.' 2>/dev/null || echo "Get all after deletion (no jq available)"
echo

echo "=== Credential Profile API Tests Completed ==="