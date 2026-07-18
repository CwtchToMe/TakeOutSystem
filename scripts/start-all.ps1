[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = "TakeoutSystem - Starting..."

$base      = (Resolve-Path "$PSScriptRoot\..").Path
$jar       = "$base\target\takeout-app.jar"
$logBase   = "$base\logs"
# MySQL 不支持中文路径，数据实际存储在 D:\work\takeout-mysql-data
$mysqlData = "D:\work\takeout-mysql-data"

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
function Find-Java {
    # 优先级：JAVA_HOME > 硬编码路径 > PATH
    if ($env:JAVA_HOME) {
        $p = "$env:JAVA_HOME\bin\java.exe"
        if (Test-Path $p) { return $p }
    }
    $candidates = @(
        "D:\tool\Java\bin\java.exe",
        "C:\workD\software\jdk\bin\java.exe",
        "C:\Program Files\Java\jdk-17\bin\java.exe",
        "C:\Program Files\Java\jdk-21\bin\java.exe"
    )
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $fromPath = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($fromPath) { return $fromPath }
    return $null
}
function Find-Npm($dir) {
    # 检查是否有 node_modules，没有就自动 npm install
    if (-not (Test-Path "$dir\node_modules")) {
        Write-Host "        npm install..." -ForegroundColor DarkGray
        Start-Process "cmd" -ArgumentList "/c npm install" -WorkingDirectory $dir -WindowStyle Hidden -Wait | Out-Null
    }
}
function Step($n, $t, $s) { Write-Host ("`n  [$n/7] $t -- $s") -ForegroundColor Cyan }
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
    $redisServer = "$PSScriptRoot\..\tools\redis\redis-server.exe"
    if (-not (Test-Path $redisServer)) { Fail "redis-server.exe not found at $redisServer" }
    else {
        Start-Process $redisServer -ArgumentList "$PSScriptRoot\..\tools\redis\redis.windows.conf" -WindowStyle Hidden | Out-Null
        if (Wait-Port 6379 10 "Redis") { OK "Redis :6379" } else { Fail "Redis timeout" }
    }
}

# 2. MySQL
Step 2 "MySQL" ":3306"
if (Check-Port 3306) { Skip "MySQL" } else {
    $mysqld = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe"
    if (-not (Test-Path $mysqld)) { Fail "mysqld.exe not found at $mysqld" }
    else {
        Start-Process $mysqld -ArgumentList "--datadir=$mysqlData","--port=3306" -WindowStyle Hidden | Out-Null
        if (Wait-Port 3306 20 "MySQL") { OK "MySQL :3306" } else { Fail "MySQL timeout" }
    }
}

# 3. Backend — 自动编译 + 启动
Step 3 "Backend" ":8080"
if (Check-Port 8080) { Skip "App" } else {
    $javaExe = Find-Java
    if (-not $javaExe) { Fail "Java not found -- install JDK 17+ and set JAVA_HOME" }
    else {
        # 自动编译
        $mvn = "$PSScriptRoot\..\mvnw.cmd"
        if (-not (Test-Path $mvn)) { $mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source }
        if ($mvn) {
            Write-Host "        building JAR..." -ForegroundColor DarkGray
            if ($mvn.EndsWith("mvnw.cmd")) { & $mvn package -DskipTests -q }
            else { Start-Process "cmd" -ArgumentList "/c mvn package -DskipTests -q" -WorkingDirectory $base -WindowStyle Hidden -Wait | Out-Null }
        }
        if (-not (Test-Path $jar)) {
            Fail "JAR not found at $jar and auto-build failed -- run: mvn package -DskipTests"
        } else {
            New-Item -ItemType Directory -Path $logBase -Force | Out-Null
            Start-Process $javaExe -ArgumentList @("-Xmx512m","-Xms256m","-jar",$jar) -WindowStyle Hidden `
                -RedirectStandardOutput "$logBase\takeout-out.log" `
                -RedirectStandardError  "$logBase\takeout-err.log" | Out-Null
            if (Wait-Port 8080 60 "App") { OK "App :8080" } else { Fail "App timeout -- check logs\takeout-out.log" }
        }
    }
}

# 4. H5
Step 4 "H5" ":3001"
if (Check-Port 3001) { Skip "H5" } else {
    Find-Npm "$base\h5"
    Start-Process "cmd" -ArgumentList "/c npm run dev" -WorkingDirectory "$base\h5" -WindowStyle Hidden | Out-Null
    if (Wait-Port 3001 45 "H5") { OK "H5 :3001" } else { Fail "H5 timeout" }
}

# 5. merchant-web
Step 5 "merchant-web" ":3002"
if (Check-Port 3002) { Skip "merchant-web" } else {
    Find-Npm "$base\merchant-web"
    Start-Process "cmd" -ArgumentList "/c npm run dev" -WorkingDirectory "$base\merchant-web" -WindowStyle Hidden | Out-Null
    if (Wait-Port 3002 45 "merchant-web") { OK "merchant-web :3002" } else { Fail "merchant-web timeout" }
}

# 6. admin-web
Step 6 "admin-web" ":3003"
if (Check-Port 3003) { Skip "admin-web" } else {
    Find-Npm "$base\admin-web"
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
$allUp = $true
foreach ($s in $svc) {
    $up  = Check-Port $s.p
    $tag = if ($up) { "[UP]  " } else { "[DOWN]" }
    $col = if ($up) { "Green" } else { "Red" }
    Write-Host "     $tag  $($s.n)" -ForegroundColor $col
    if (-not $up) { $allUp = $false }
}
Write-Host "  ============================================" -ForegroundColor Cyan

if ($allUp) {
    Write-Host ""
    Write-Host "  All services are UP!" -ForegroundColor Green
    Write-Host "  H5:         http://localhost:3001" -ForegroundColor White
    Write-Host "  Merchant:   http://localhost:3002" -ForegroundColor White
    Write-Host "  Admin:      http://localhost:3003" -ForegroundColor White
    Write-Host "  API:        http://localhost:8080/doc.html" -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "  Some services failed to start -- check above" -ForegroundColor Red
}

Write-Host ""
Write-Host "  Launching console..." -ForegroundColor DarkGray
Start-Sleep -Seconds 1
$console = "$PSScriptRoot\console.ps1"
if (Test-Path $console) { & $console } else { Write-Host "  [WARN] console.ps1 not found" -ForegroundColor DarkYellow }
