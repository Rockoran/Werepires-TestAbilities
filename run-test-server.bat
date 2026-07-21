@echo off
REM Double-click to build the plugin and launch the local test server.
REM Pass -NoBuild to skip the build:  run-test-server.bat -NoBuild
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-test-server.ps1" %*
pause
