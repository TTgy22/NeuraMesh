# 启动 NeuraMesh API 网关（Spring Boot, :8080）
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
Write-Host "启动 API 网关 http://localhost:8080 ..." -ForegroundColor Cyan
& "$root\gradlew.bat" ":neuramesh-api:bootRun"