# 一键启动：后端 API + 控制台 + 节点客户端（各自新开 PowerShell 窗口）
$root = Split-Path -Parent $PSScriptRoot
Write-Host "正在分别启动后端 / 控制台 / 节点客户端 ..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit","-File","$PSScriptRoot\run-backend.ps1"
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList "-NoExit","-File","$PSScriptRoot\run-dashboard.ps1"
Start-Process powershell -ArgumentList "-NoExit","-File","$PSScriptRoot\run-node.ps1"
Write-Host "已在新窗口启动。后端: http://localhost:8080  控制台: http://localhost:5173" -ForegroundColor Cyan