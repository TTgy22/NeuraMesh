@echo off
rem NeuraMesh GUI Launcher entry - runs on any Windows PC (built-in PowerShell/WinForms, bundled JRE)
start "" powershell -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "%~dp0launcher\launcher.ps1"
exit /b 0
