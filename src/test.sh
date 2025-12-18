#!/bin/bash

## This is all pure ai code
echo "🧪 Testing Delivery Tracker Backend"
echo "===================================="

# Test 1
echo ""
echo "📍 Test 1: Updating courier1 location..."
curl -s -X POST http://localhost:8080/api/locations/update \
  -H "Content-Type: application/json" \
  -d '{
    "courierId": "courier1",
    "latitude": 40.7128,
    "longitude": -74.0060,
    "heading": 90.0,
    "timestamp": "2024-12-17T10:30:00Z",
    "status": "DELIVERING"
  }'
echo "✅ Done"

# Test 2
echo ""
echo "📍 Test 2: Getting courier1 location..."
curl -s http://localhost:8080/api/locations/courier/courier1 | jq .
echo ""

# Test 3
echo "📍 Test 3: Checking Valkey..."
redis-cli GET courier:location:courier1
echo ""

# Test 4
echo "📍 Test 4: Testing non-existent courier (should be 404)..."
curl -s -i http://localhost:8080/api/locations/courier/courier999 | head -1
echo ""

echo "✅ All tests complete!"
