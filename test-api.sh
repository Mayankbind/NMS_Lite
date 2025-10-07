#!/bin/bash

# NMS Lite API Test Script
# This script tests the basic API endpoints

set -e

BASE_URL="http://localhost:8080"
echo "=== NMS Lite API Test Script ==="
echo "Testing API endpoints at: $BASE_URL"
echo

# Test health endpoint
echo "1. Testing health endpoint..."
HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/health")
HEALTH_HTTP_CODE=$(echo "$HEALTH_RESPONSE" | tail -n1)
HEALTH_BODY=$(echo "$HEALTH_RESPONSE" | head -n -1)

if [ "$HEALTH_HTTP_CODE" = "200" ]; then
    echo "✓ Health check passed (HTTP $HEALTH_HTTP_CODE)"
    echo "Response: $HEALTH_BODY"
else
    echo "✗ Health check failed (HTTP $HEALTH_HTTP_CODE)"
    echo "Response: $HEALTH_BODY"
fi
echo

# Test readiness endpoint
echo "2. Testing readiness endpoint..."
READINESS_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/ready")
READINESS_HTTP_CODE=$(echo "$READINESS_RESPONSE" | tail -n1)
READINESS_BODY=$(echo "$READINESS_RESPONSE" | head -n -1)

if [ "$READINESS_HTTP_CODE" = "200" ]; then
    echo "✓ Readiness check passed (HTTP $READINESS_HTTP_CODE)"
    echo "Response: $READINESS_BODY"
else
    echo "✗ Readiness check failed (HTTP $READINESS_HTTP_CODE)"
    echo "Response: $READINESS_BODY"
fi
echo

# Test liveness endpoint
echo "3. Testing liveness endpoint..."
LIVENESS_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/live")
LIVENESS_HTTP_CODE=$(echo "$LIVENESS_RESPONSE" | tail -n1)
LIVENESS_BODY=$(echo "$LIVENESS_RESPONSE" | head -n -1)

if [ "$LIVENESS_HTTP_CODE" = "200" ]; then
    echo "✓ Liveness check passed (HTTP $LIVENESS_HTTP_CODE)"
    echo "Response: $LIVENESS_BODY"
else
    echo "✗ Liveness check failed (HTTP $LIVENESS_HTTP_CODE)"
    echo "Response: $LIVENESS_BODY"
fi
echo

# Test login endpoint
echo "4. Testing login endpoint..."
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username": "admin", "password": "admin123"}')
LOGIN_HTTP_CODE=$(echo "$LOGIN_RESPONSE" | tail -n1)
LOGIN_BODY=$(echo "$LOGIN_RESPONSE" | head -n -1)

if [ "$LOGIN_HTTP_CODE" = "200" ]; then
    echo "✓ Login test passed (HTTP $LOGIN_HTTP_CODE)"
    echo "Response: $LOGIN_BODY"
    
    # Extract access token for further tests
    ACCESS_TOKEN=$(echo "$LOGIN_BODY" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$ACCESS_TOKEN" ]; then
        echo "Access token extracted: ${ACCESS_TOKEN:0:20}..."
    fi
else
    echo "✗ Login test failed (HTTP $LOGIN_HTTP_CODE)"
    echo "Response: $LOGIN_BODY"
fi
echo

# Test protected endpoint (if we have a token)
if [ -n "$ACCESS_TOKEN" ]; then
    echo "5. Testing protected endpoint..."
    PROTECTED_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/api/devices" \
        -H "Authorization: Bearer $ACCESS_TOKEN")
    PROTECTED_HTTP_CODE=$(echo "$PROTECTED_RESPONSE" | tail -n1)
    PROTECTED_BODY=$(echo "$PROTECTED_RESPONSE" | head -n -1)
    
    if [ "$PROTECTED_HTTP_CODE" = "200" ]; then
        echo "✓ Protected endpoint test passed (HTTP $PROTECTED_HTTP_CODE)"
        echo "Response: $PROTECTED_BODY"
    else
        echo "✗ Protected endpoint test failed (HTTP $PROTECTED_HTTP_CODE)"
        echo "Response: $PROTECTED_BODY"
    fi
else
    echo "5. Skipping protected endpoint test (no access token)"
fi
echo

echo "=== API Test Complete ==="
echo "If all tests passed, the NMS Lite application is working correctly!"
echo "You can now use the API endpoints as documented in README.md"

