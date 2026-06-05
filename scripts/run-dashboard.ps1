# 启动厂商控制台 / 区块链浏览器（Vite, :5173）
$ErrorActionPreference = "Stop"
$dir = Join-Path (Split-Path -Parent $PSScriptRoot) "neuramesh-dashboard"
Set-Location $dir
if (-not (Test-Path "node_modules")) {
  Write-Host "首次运行，安装依赖..." -ForegroundColor Yellow
  npm install --registry=https://registry.npmmirror.com --no-audit --no-fund
}
Write-Host "启动控制台 http://localhost:5173 ..." -ForegroundColor Cyan
npm run dev