@echo off
setlocal

:: Check for admin
net session >nul 2>&1
if %errorLevel% neq 0 (
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

:: Batch directory (no trailing backslash issues)
set "SCRIPT_DIR=%~dp0"

:: Verify redis-cli.exe exists next to the batch
if not exist "%SCRIPT_DIR%redis-cli.exe" (
    echo redis-cli.exe not found in %SCRIPT_DIR%
    pause
    exit /b
)

:: Check if already in PATH
echo %PATH% | find /i "%SCRIPT_DIR%" >nul
if not errorlevel 1 (
    echo %SCRIPT_DIR% is already in PATH.
    pause
    goto :eof
)

:: Add to system PATH
powershell -Command ^
    "$p=[Environment]::GetEnvironmentVariable('Path','Machine');" ^
    "[Environment]::SetEnvironmentVariable('Path', $p + ';%SCRIPT_DIR%', 'Machine')"

echo Added %SCRIPT_DIR% to system PATH.
echo Restart your terminal to apply changes.
pause
