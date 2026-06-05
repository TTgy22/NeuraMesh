# 安装两个前端工程的依赖（npmmirror 加速；Electron 二进制延后到 run-node 时下载）
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
foreach ($proj in @("neuramesh-dashboard", "neuramesh-node")) {
  $dir = Join-Path $root $proj
  Write-Host "安装 $proj 依赖..." -ForegroundColor Cyan
  Push-Location $dir
  $env:ELECTRON_SKIP_BINARY_DOWNLOAD = "1"
  npm install --registry=https://registry.npmmirror.com --no-audit --no-fund
  Pop-Location
}
Write-Host "前端依赖安装完成。" -ForegroundColor Green