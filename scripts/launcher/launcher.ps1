# NeuraMesh 图形化启动器（WinForms，Windows 自带运行时，零外部依赖）
# 管理三个服务：后端链节点(8080) / 厂商控制台(5173) / 节点客户端(Electron)
# Java 优先级：发布包内置 jre\ > 系统 PATH 中的 java

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$root = Split-Path -Parent $PSScriptRoot   # release 根目录
$script:backendProc = $null
$script:serveProc = $null

function Find-Java {
    $bundled = Join-Path $root "jre\bin\java.exe"
    if (Test-Path $bundled) { return $bundled }
    $sys = Get-Command java -ErrorAction SilentlyContinue
    if ($sys) { return $sys.Source }
    return $null
}

function Test-Backend {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:8080/chain/stats" -TimeoutSec 2 -UseBasicParsing
        return $r.StatusCode -eq 200
    } catch { return $false }
}

function Test-Dashboard {
    try {
        $c = New-Object Net.Sockets.TcpClient
        $c.Connect("127.0.0.1", 5173); $ok = $c.Connected; $c.Close(); return $ok
    } catch { return $false }
}

function Test-NodeClient {
    return $null -ne (Get-Process -Name "NeuraMesh-Node" -ErrorAction SilentlyContinue)
}

# ---------- 窗体 ----------
$form = New-Object Windows.Forms.Form
$form.Text = "NeuraMesh 启动器"
$form.Size = New-Object Drawing.Size(560, 470)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedSingle"
$form.MaximizeBox = $false
$form.BackColor = [Drawing.Color]::FromArgb(13, 16, 23)

$title = New-Object Windows.Forms.Label
$title.Text = "NeuraMesh 边缘智算网络"
$title.Font = New-Object Drawing.Font("Microsoft YaHei UI", 15, [Drawing.FontStyle]::Bold)
$title.ForeColor = [Drawing.Color]::White
$title.Location = New-Object Drawing.Point(20, 18)
$title.AutoSize = $true
$form.Controls.Add($title)

$sub = New-Object Windows.Forms.Label
$sub.Text = "一键启动全套演示环境（后端区块链 + 厂商控制台 + 节点客户端）"
$sub.Font = New-Object Drawing.Font("Microsoft YaHei UI", 9)
$sub.ForeColor = [Drawing.Color]::FromArgb(140, 150, 165)
$sub.Location = New-Object Drawing.Point(22, 52)
$sub.AutoSize = $true
$form.Controls.Add($sub)

function New-ServiceRow($y, $name, $desc) {
    $lamp = New-Object Windows.Forms.Label
    $lamp.Text = [char]0x25CF
    $lamp.Font = New-Object Drawing.Font("Segoe UI", 12, [Drawing.FontStyle]::Bold)
    $lamp.ForeColor = [Drawing.Color]::FromArgb(90, 95, 105)
    $lamp.Location = New-Object Drawing.Point(24, $y)
    $lamp.Size = New-Object Drawing.Size(22, 24)
    $form.Controls.Add($lamp)

    $lbl = New-Object Windows.Forms.Label
    $lbl.Text = "$name`n$desc"
    $lbl.Font = New-Object Drawing.Font("Microsoft YaHei UI", 9)
    $lbl.ForeColor = [Drawing.Color]::FromArgb(220, 224, 230)
    $lbl.Location = New-Object Drawing.Point(50, $y)
    $lbl.Size = New-Object Drawing.Size(250, 36)
    $form.Controls.Add($lbl)

    $btnStart = New-Object Windows.Forms.Button
    $btnStart.Text = "启动"
    $btnStart.Location = New-Object Drawing.Point(330, $y)
    $btnStart.Size = New-Object Drawing.Size(80, 28)
    $btnStart.FlatStyle = "Flat"
    $btnStart.ForeColor = [Drawing.Color]::White
    $btnStart.BackColor = [Drawing.Color]::FromArgb(99, 91, 255)
    $form.Controls.Add($btnStart)

    $btnOpen = New-Object Windows.Forms.Button
    $btnOpen.Text = "打开"
    $btnOpen.Location = New-Object Drawing.Point(420, $y)
    $btnOpen.Size = New-Object Drawing.Size(80, 28)
    $btnOpen.FlatStyle = "Flat"
    $btnOpen.ForeColor = [Drawing.Color]::FromArgb(200, 205, 215)
    $btnOpen.BackColor = [Drawing.Color]::FromArgb(35, 40, 52)
    $form.Controls.Add($btnOpen)

    return @{ Lamp = $lamp; Start = $btnStart; Open = $btnOpen }
}

$rowBackend = New-ServiceRow 90  "后端链节点" "Spring Boot + BFT 链 · 端口 8080"
$rowDash    = New-ServiceRow 140 "厂商控制台" "Web 控制台 · http://localhost:5173"
$rowNode    = New-ServiceRow 190 "节点客户端" "Electron 桌面端（含设备指纹）"

# 一键全启 / 全部停止
$btnAll = New-Object Windows.Forms.Button
$btnAll.Text = "一键全部启动"
$btnAll.Font = New-Object Drawing.Font("Microsoft YaHei UI", 10, [Drawing.FontStyle]::Bold)
$btnAll.Location = New-Object Drawing.Point(24, 238)
$btnAll.Size = New-Object Drawing.Size(236, 38)
$btnAll.FlatStyle = "Flat"
$btnAll.ForeColor = [Drawing.Color]::White
$btnAll.BackColor = [Drawing.Color]::FromArgb(46, 160, 120)
$form.Controls.Add($btnAll)

$btnStopAll = New-Object Windows.Forms.Button
$btnStopAll.Text = "全部停止"
$btnStopAll.Font = New-Object Drawing.Font("Microsoft YaHei UI", 10)
$btnStopAll.Location = New-Object Drawing.Point(266, 238)
$btnStopAll.Size = New-Object Drawing.Size(234, 38)
$btnStopAll.FlatStyle = "Flat"
$btnStopAll.ForeColor = [Drawing.Color]::FromArgb(220, 224, 230)
$btnStopAll.BackColor = [Drawing.Color]::FromArgb(60, 44, 52)
$form.Controls.Add($btnStopAll)

$log = New-Object Windows.Forms.TextBox
$log.Multiline = $true
$log.ReadOnly = $true
$log.ScrollBars = "Vertical"
$log.Location = New-Object Drawing.Point(24, 290)
$log.Size = New-Object Drawing.Size(476, 120)
$log.BackColor = [Drawing.Color]::FromArgb(20, 24, 33)
$log.ForeColor = [Drawing.Color]::FromArgb(120, 220, 160)
$log.Font = New-Object Drawing.Font("Consolas", 9)
$form.Controls.Add($log)

function Write-Log($msg) {
    $log.AppendText("[$(Get-Date -Format HH:mm:ss)] $msg`r`n")
}

# ---------- 动作 ----------
function Start-Backend {
    if (Test-Backend) { Write-Log "后端已在运行（8080），直接复用"; return }
    $java = Find-Java
    if (-not $java) {
        Write-Log "错误：未找到 Java。发布包应含 jre\ 目录；或安装 JDK 17+"
        return
    }
    $jar = Get-ChildItem (Join-Path $root "neuramesh-api*.jar") | Select-Object -First 1
    if (-not $jar) { Write-Log "错误：未找到 neuramesh-api*.jar"; return }
    Write-Log "启动后端：$([IO.Path]::GetFileName($java)) -jar $($jar.Name)（约需 10~20 秒）"
    $script:backendProc = Start-Process $java -ArgumentList "-jar", "`"$($jar.FullName)`"" -WindowStyle Hidden -PassThru
}

function Start-Dashboard {
    if (Test-Dashboard) { Write-Log "控制台已在运行（5173）"; return }
    Write-Log "启动控制台静态服务（5173）"
    $serve = Join-Path $PSScriptRoot "serve-dashboard.ps1"
    $script:serveProc = Start-Process powershell -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", "`"$serve`"" -WindowStyle Hidden -PassThru
    # 自动打开前端入口（控制台网页），评委零操作可见
    Start-Sleep -Milliseconds 1200
    Start-Process "http://localhost:5173"
    Write-Log "已在浏览器打开前端入口 http://localhost:5173"
}

function Start-NodeClient {
    if (Test-NodeClient) { Write-Log "节点客户端已在运行"; return }
    $exe = Join-Path $root "NeuraMesh-Node-win32-x64\NeuraMesh-Node.exe"
    if (-not (Test-Path $exe)) { Write-Log "错误：未找到节点客户端 exe"; return }
    Write-Log "启动节点客户端"
    Start-Process $exe | Out-Null
}

function Stop-All {
    Write-Log "停止全部服务…"
    Get-Process -Name "NeuraMesh-Node" -ErrorAction SilentlyContinue | Stop-Process -Force
    if ($script:serveProc -and -not $script:serveProc.HasExited) { $script:serveProc | Stop-Process -Force }
    # 关闭所有跑 neuramesh-api 的 java（含客户端内置启动器拉起的）
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like "*neuramesh-api*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    $script:backendProc = $null
    $script:serveProc = $null
    Write-Log "已全部停止"
}

$rowBackend.Start.Add_Click({ Start-Backend })
$rowBackend.Open.Add_Click({ Start-Process "http://localhost:8080/chain/stats" })
$rowDash.Start.Add_Click({ Start-Dashboard })
$rowDash.Open.Add_Click({ Start-Process "http://localhost:5173" })
$rowNode.Start.Add_Click({ Start-NodeClient })
$rowNode.Open.Add_Click({ Start-NodeClient })
$btnAll.Add_Click({ Start-Backend; Start-Dashboard; Start-NodeClient; Write-Log "全套已拉起：后端就绪后绿灯亮起即可演示" })
$btnStopAll.Add_Click({ Stop-All })

# ---------- 状态轮询 ----------
$green = [Drawing.Color]::FromArgb(80, 220, 140)
$gray = [Drawing.Color]::FromArgb(90, 95, 105)
$timer = New-Object Windows.Forms.Timer
$timer.Interval = 3000
$timer.Add_Tick({
    $rowBackend.Lamp.ForeColor = if (Test-Backend) { $green } else { $gray }
    $rowDash.Lamp.ForeColor = if (Test-Dashboard) { $green } else { $gray }
    $rowNode.Lamp.ForeColor = if (Test-NodeClient) { $green } else { $gray }
})
$timer.Start()

$form.Add_FormClosing({
    if ((Test-Backend) -or (Test-NodeClient)) {
        $r = [Windows.Forms.MessageBox]::Show("是否同时停止全部已启动的服务？", "NeuraMesh 启动器",
            [Windows.Forms.MessageBoxButtons]::YesNo, [Windows.Forms.MessageBoxIcon]::Question)
        if ($r -eq [Windows.Forms.DialogResult]::Yes) { Stop-All }
    }
})

Write-Log "就绪。Java：$(if (Find-Java) { Find-Java } else { '未找到（请使用含 jre 的完整发布包）' })"
Write-Log "点击「一键全部启动」开始演示"
[void]$form.ShowDialog()
