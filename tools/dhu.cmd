@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dhu.ps1" %*
if errorlevel 1 pause
