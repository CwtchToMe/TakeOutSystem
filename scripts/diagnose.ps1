<#
.SYNOPSIS
  TakeoutSystem One-Click Diagnose -- Check all services
.DESCRIPTION
  Check MySQL / Redis / Backend / Frontend status, locate issues and give fix suggestions.
  No dependencies, pure PowerShell 5.1 compatible.
#>

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path "$scriptRoot\.."

$ErrorActionPreference = "SilentlyContinue"

$GREEN  = "Green"
$RED    = "Red"
$YELLOW = "Yellow"
$CYAN   = "Cyan"
$GRAY   = "Gray"

function Ok   ($s) { Write-Host "  [OK] $s" -ForegroundColor $GREEN }
function Fail ($s) { Write-Host "  [FAIL] $s" -ForegroundColor $RED }
function Warn ($s) { Write-Host "  [WARN] $s" -ForegroundColor $YELLOW }
function Info ($s) { Write-Host "  [INFO] $s" -ForegroundColor $CYAN }

function Check-Port($port) {
    $null -ne (netstat -an 2>$null | Select-String ":$port\s" | Select-Object -First 1)
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor $CYAN
Write-Host "  TakeoutSystem Diagnose" -ForegroundColor $CYAN
Write-Host "==========================================" -ForegroundColor $CYAN
Write-Host ""

$allOk = $true

# ---- 1. MySQL ----
Write-Host "--- MySQL (3306) ---" -ForegroundColor $CYAN
if (Check-Port 3306) {
    Ok "Port 3306 listening"
    $mysql = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    if (Test-Path $mysql) {
        $result = & $mysql -uroot -proot --default-character-set=utf8mb4 db_takeout -e "SELECT 1 AS ok;" 2>$null
        if ($result -match "ok") {
            Ok "Database db_takeout connected"
        } else {
            Warn "MySQL running but login failed, check password/database"
            $allOk = $false
        }
    }
} else {
    Fail "Port 3306 not listening -> MySQL not started"
    Info "Fix: run console.ps1 menu 2 -> 0 (Start Redis + MySQL)"
    $allOk = $false
}

Write-Host ""

# ---- 2. Redis ----
Write-Host "--- Redis (6379) ---" -ForegroundColor $CYAN
if (Check-Port 6379) {
    Ok "Port 6379 listening"
    $redisCli = "$projectRoot\tools\redis\redis-cli.exe"
    if (Test-Path $redisCli) {
        $pong = & $redisCli ping 2>$null
        if ($pong -match "PONG") {
            Ok "Redis ping OK"
        } else {
            Warn "Redis running but ping failed"
            $allOk = $false
        }
    }
} else {
    Fail "Port 6379 not listening -> Redis not started"
    Info "Fix: run console.ps1 menu 2 -> 0"
    $allOk = $false
}

Write-Host ""

# ---- 3. Backend ----
Write-Host "--- Backend API (8080) ---" -ForegroundColor $CYAN
if (Check-Port 8080) {
    Ok "Port 8080 listening"
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get -TimeoutSec 5
        $mysqlStatus = $health.checks.mysql.status
        $redisStatus = $health.checks.redis.status
        if ($mysqlStatus -eq "UP") { Ok "  MySQL reachable (backend)" } else { Fail "  MySQL unreachable (backend)" }
        if ($redisStatus -eq "UP") { Ok "  Redis reachable (backend)" } else { Fail "  Redis unreachable (backend)" }
        if ($mysqlStatus -eq "UP" -and $redisStatus -eq "UP") {
            Ok "Backend health check passed"
        } else {
            Warn "Backend running but dependencies have issues"
            $allOk = $false
        }
    } catch {
        Warn "Backend port open but /api/health no response, may still starting"
        Info "Check logs: Get-Content ""$projectRoot\logs\takeout-out.log"" -Tail 10"
        $allOk = $false
    }

    $jarPath = "$projectRoot\target\takeout-app.jar"
    if (Test-Path $jarPath) {
        Ok "JAR exists ($([math]::Round((Get-Item $jarPath).Length / 1MB, 1)) MB)"
    }
} else {
    Fail "Port 8080 not listening -> Backend not started"
    Info "Fix: run console.ps1 menu 3 -> 1"
    $allOk = $false

    $jarPath = "$projectRoot\target\takeout-app.jar"
    if (-not (Test-Path $jarPath)) {
        Fail "JAR not found ({0})" -f $jarPath
        Info "Fix: run mvn clean package -DskipTests in project directory"
    }
}

Write-Host ""

# ---- 4. Frontend ----
Write-Host "--- Frontend ---" -ForegroundColor $CYAN
$h5Port = Check-Port 3001
$mchPort = Check-Port 3002
$admPort = Check-Port 3003
if ($h5Port) { Ok "H5 (3001) started -> http://localhost:3001" }
else { Warn "H5 (3001) not started -> run: cd h5 ; npm run dev" }
if ($mchPort) { Ok "Merchant (3002) started -> http://localhost:3002" }
else { Warn "Merchant (3002) not started -> run: cd merchant-web ; npm run dev" }
if ($admPort) { Ok "Admin (3003) started -> http://localhost:3003" }
else { Warn "Admin (3003) not started -> run: cd admin-web ; npm run dev" }

Write-Host ""

# ---- 5. Redis Cache Status ----
Write-Host "--- Redis Cache Status ---" -ForegroundColor $CYAN
$redisCli = "$projectRoot\tools\redis\redis-cli.exe"
if (Check-Port 6379 -and (Test-Path $redisCli)) {
    $merchantKeys = & $redisCli keys "merchant:info:*" 2>$null
    if ($merchantKeys) {
        $count = ($merchantKeys | Measure-Object).Count
        Warn "Found $count merchant cache keys (merchant:info:*)"
        Warn "If backend just restarted, suggest clearing cache: .\tools\redis\redis-cli.exe flushdb"
    } else {
        Ok "No stale merchant cache"
    }
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor $CYAN
if ($allOk) {
    Write-Host "  All services OK, system is ready" -ForegroundColor $GREEN
} else {
    Write-Host "  Issues found, fix according to [FAIL]/[WARN] above" -ForegroundColor $YELLOW
    Write-Host "  Quick access: ./scripts/console.ps1" -ForegroundColor $YELLOW
}
Write-Host "==========================================" -ForegroundColor $CYAN