# 启动 Electron 节点客户端（渲染层 Vite :5174 + Electron 桌面窗口）
$ErrorActionPreference = "Stop"
$dir = Join-Path (Split-Path -Parent $PSScriptRoot) "neuramesh-node"
Set-Location $dir
if (-not (Test-Path "node_modules")) {
  Write-Host "首次运行，安装依赖..." -ForegroundColor Yellow
  $env:ELECTRON_SKIP_BINARY_DOWNLOAD = "1"
  npm install --registry=https://registry.npmmirror.com --no-audit --no-fund
}
# 确保 Electron 二进制就绪（首次安装跳过了下载）
if (-not (Test-Path "node_modules\electron\dist")) {
  Write-Host "下载 Electron 二进制（npmmirror 镜像）..." -ForegroundColor Yellow
  $env:ELECTRON_MIRROR = "https://npmmirror.com/mirrors/electron/"
  node "node_modules\electron\install.js"
}
Write-Host "启动渲染层 http://localhost:5174 ..." -ForegroundColor Cyan
$vite = Start-Process npm -ArgumentList "run","dev" -PassThru -NoNewWindow
Start-Sleep -Seconds 4
$env:VITE_DEV_SERVER_URL = "http://localhost:5174"
Write-Host "启动 Electron 桌面窗口 ..." -ForegroundColor Cyan
npm run electron
if ($vite -and -not $vite.HasExited) { Stop-Process -Id $vite.Id -Force }