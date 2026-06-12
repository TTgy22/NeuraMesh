# NeuraMesh 控制台静态服务器：HttpListener 托管 release\dashboard 于 http://localhost:5173/
# 由 launcher.ps1 以隐藏窗口拉起；独立运行也可：powershell -ExecutionPolicy Bypass -File serve-dashboard.ps1

$root = Join-Path (Split-Path -Parent $PSScriptRoot) "dashboard"
if (-not (Test-Path (Join-Path $root "index.html"))) {
    Write-Error "未找到 dashboard\index.html"
    exit 1
}

$mime = @{
    ".html" = "text/html; charset=utf-8"; ".js" = "application/javascript"; ".css" = "text/css"
    ".json" = "application/json"; ".svg" = "image/svg+xml"; ".png" = "image/png"; ".ico" = "image/x-icon"
    ".woff" = "font/woff"; ".woff2" = "font/woff2"; ".map" = "application/json"; ".txt" = "text/plain"
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://localhost:5173/")
$listener.Start()
Write-Host "dashboard serving at http://localhost:5173/"

while ($listener.IsListening) {
    try {
        $ctx = $listener.GetContext()
        $reqPath = [Uri]::UnescapeDataString($ctx.Request.Url.AbsolutePath).TrimStart("/")
        if ([string]::IsNullOrWhiteSpace($reqPath)) { $reqPath = "index.html" }
        $file = Join-Path $root $reqPath
        # 防目录穿越 + SPA 兜底回 index.html
        $full = [IO.Path]::GetFullPath($file)
        if (-not $full.StartsWith([IO.Path]::GetFullPath($root)) -or -not (Test-Path $full -PathType Leaf)) {
            $full = Join-Path $root "index.html"
        }
        $bytes = [IO.File]::ReadAllBytes($full)
        $ext = [IO.Path]::GetExtension($full).ToLower()
        $ctx.Response.ContentType = if ($mime.ContainsKey($ext)) { $mime[$ext] } else { "application/octet-stream" }
        $ctx.Response.ContentLength64 = $bytes.Length
        $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        $ctx.Response.OutputStream.Close()
    } catch {
        # 单个请求失败不中断服务
    }
}
