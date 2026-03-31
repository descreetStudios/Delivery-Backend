@echo off
echo Testing WebSocket functionality for Delivery Tracking System
echo.

echo Connecting to WebSocket endpoint...
echo.
echo Please manually send the following messages in the wscat terminal:
echo 1. {"type": "subscribe", "courierId": "courier_123"}
echo 2. {"type": "unsubscribe", "courierId": "courier_123"}
echo 3. {"type": "subscribe", "courierId": "courier_456"}
echo.
echo When you're done testing, close this window to stop the test.

wscat -c ws://localhost:8080/ws/locations

echo.
echo Test completed.
pause
