#!/bin/bash

# The user's access token
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJubXMtbGl0ZSIsImF1ZCI6Im5tcy1saXRlLWFwaSIsInN1YiI6ImIyZWU5OWEzLTVkYzktNGMzMi05OWVhLTFlMmNjYzNlZWE4NiIsInVzZXJuYW1lIjoiYWRtaW4iLCJyb2xlIjoiYWRtaW4iLCJ0eXBlIjoiYWNjZXNzIiwianRpIjoiODZlZTlmODEtY2Q0MS00NmUyLTlkOWYtZTVhYTJlZGM0MWE3IiwiaWF0IjoxNzYwNDQ1Mjk4LCJleHAiOjE3NjA0NDg4OTh9.kjLTqYMtZHe8HYYEP6w5ta7uFE8UXJ7ruIeRgMIPlP0"

URL="http://localhost:8080/api/credentials"
HEADER="Authorization: Bearer $TOKEN"

echo "Sending 120 requests to $URL..."
echo "------------------------------------------"

for i in {1..120}; do
    # Use -w to write out the HTTP code and a newline
    # Use -s to silence progress meter and -o to discard body
    http_code=$(curl -s -o /dev/null -w "%""{http_code}""" -H "$HEADER" "$URL")
    echo "Request #$i: Status Code $http_code"
done

echo "------------------------------------------"
echo "Rate limit test completed."