@echo off
cls
:: Check for admin
net session >nul 2>&1
if %errorLevel% neq 0 (
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

:: Check for Java
where java >nul 2>nul
if %errorlevel%==1 (
    @echo Java not found in path.
    pause
    exit /b
)

choco install maven

pause
