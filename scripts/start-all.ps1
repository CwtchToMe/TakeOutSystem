[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = "TakeoutSystem - Starting..."

$base    = (Resolve-Path "$PSScriptRoot\..").Path
$java    = "C:\workD\software\jdk\bin\java.exe"
$jar     = "$base\target\takeout-app.jar"
$logBase = "$base\logs"

function Check-Port($port) {
    $null -ne (netstat -an 2>$null | Select-String ":$port\s" | Select-Object -First 1)
}
function Wait-Port($port, $sec, $label) {
    $d = (Get-Date).AddSeconds($sec)
    while ((Get-Date) -lt $d) {
        if (Check-Port $port) { return $true }
        Write-Host "    waiting $label..." -ForegroundColor DarkGray
        Start-Sleep -Seconds 2
    }
    return $false
}
function Step($n, $t, $s) { Write-Host ("`n  [$n/6] $t -- $s") -ForegroundColor Cyan }
function OK($s)   { Write-Host "        OK   $s" -ForegroundColor Green }
function Skip($s) { Write-Host "        --   $s already running" -ForegroundColor Yellow }
function Fail($s) { Write-Host "        !!   $s" -ForegroundColor Red }

Clear-Host
Write-Host "  ============================================" -ForegroundColor Cyan
Write-Host "     TakeoutSystem -- Start All" -ForegroundColor Cyan
Write-Host "  ============================================" -ForegroundColor Cyan

# 1. Redis
Step 1 "Redis" ":6379"
if (Check-Port 6379) { Skip "Redis" } else {
    Start-Process "C:\workD\software\redis\redis-server.exe" `
        -ArgumentList "C:\workD\software\redis\redis.windows.conf" -WindowStyle Hidden | Out-Null
    if (Wait-Port 6379 10 "Redis") { OK "Redis :6379" } else { Fail "Redis timeout" }
}

# 2. MySQL
Step 2 "MySQL" ":3306"
if (Check-Port 3306) { Skip "MySQL" } else {
    Start-Process "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" `
        -ArgumentList "--datadir=C:\workD\software\mysql-data","--port=3306" -WindowStyle Hidden | Out-Null
    if (Wait-Port 3306 20 "MySQL") { OK "MySQL :3306" } else { Fail "MySQL timeout" }
}

# 3. Spring Boot
Step 3 "Backend" ":8080"
if (Check-Port 8080) { Skip "App" } else {
    if (-not (Test-Path $jar)) { Fail "JAR not found -- run: mvn package -DskipTests" }
    else {
        New-Item -ItemType Directory -Path $logBase -Force | Out-Null
        Start-Process $java -ArgumentList @("-Xmx512m","-Xms256m","-jar",$jar) -WindowStyle Hidden `
            -RedirectStandardOutput "$logBase\takeout-out.log" `
            -RedirectStandardError  "$logBase\takeout-err.log" | Out-Null
        if (Wait-Port 8080 40 "App") { OK "App :8080" } else { Fail "App timeout -- check logs\takeout-out.log" }
    }
}

# 4. H5
Step 4 "H5" ":3001"
if (Check-Port 3001) { Skip "H5" } else {
    Start-Process "cmd" -ArgumentList "/c npm run dev" -WorkingDirectory "$base\h5" -WindowStyle Hidden | Out-Null
    if (Wait-Port 3001 45 "H5") { OK "H5 :3001" } else { Fail "H5 timeout" }
}

# 5. merchant-web
Step 5 "merchant-web" ":3002"
if (Check-Port 3002) { Skip "merchant-web" } else {
    Start-Process "cmd" -ArgumentList "/c npm run dev" -WorkingDirectory "$base\merchant-web" -WindowStyle Hidden | Out-Null
    if (Wait-Port 3002 45 "merchant-web") { OK "merchant-web :3002" } else { Fail "merchant-web timeout" }
}

# 6. admin-web
Step 6 "admin-web" ":3003"
if (Check-Port 3003) { Skip "admin-web" } else {
    Start-Process "cmd" -ArgumentList "/c npm run dev" -WorkingDirectory "$base\admin-web" -WindowStyle Hidden | Out-Null
    if (Wait-Port 3003 45 "admin-web") { OK "admin-web :3003" } else { Fail "admin-web timeout" }
}

# Summary
Write-Host ""
Write-Host "  ============================================" -ForegroundColor Cyan
$svc = @(
    @{n="Redis      :6379"; p=6379}, @{n="MySQL      :3306"; p=3306},
    @{n="App        :8080"; p=8080}, @{n="H5         :3001"; p=3001},
    @{n="Merchant   :3002"; p=3002}, @{n="Admin      :3003"; p=3003}
)
foreach ($s in $svc) {
    $up  = Check-Port $s.p
    $tag = if ($up) { "[UP]  " } else { "[DOWN]" }
    $col = if ($up) { "Green" } else { "Red" }
    Write-Host "     $tag  $($s.n)" -ForegroundColor $col
}
Write-Host "  ============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Launching console..." -ForegroundColor DarkGray
Start-Sleep -Seconds 1
& "C:\workD\software\console.ps1"
