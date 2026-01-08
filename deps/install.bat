@echo off
cls

set "SCRIPT_DIR=%~dp0"

:: Check for admin
net session >nul 2>&1
if %errorLevel% neq 0 (
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

cd %SCRIPT_DIR%

cd Maven-Windows/
call install.bat
cd..

cd Redis-Windows/
install.bat
install_redis_service
call set_path.bat
cd..