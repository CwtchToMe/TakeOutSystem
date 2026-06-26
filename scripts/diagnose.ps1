&lt;#
.SYNOPSIS
  TakeoutSystem 一键诊断脚本 — 检查所有服务是否正常
.DESCRIPTION
  检查 MySQL / Redis / 后端 / 前端 的状态，定位问题并给出修复建议。
  无需任何依赖，纯 PowerShell 5.1 兼容。
#>

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
Write-Host "  TakeoutSystem 一键诊断" -ForegroundColor $CYAN
Write-Host "==========================================" -ForegroundColor $CYAN
Write-Host ""

$allOk = $true

# ── 1. MySQL ──
Write-Host "--- MySQL (3306) ---" -ForegroundColor $CYAN
if (Check-Port 3306) {
    Ok "端口 3306 监听中"
    $mysql = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    if (Test-Path $mysql) {
        $result = & $mysql -uroot -proot --default-character-set=utf8mb4 db_takeout -e "SELECT 1 AS ok;" 2>$null
        if ($result -match "ok") {
            Ok "数据库 db_takeout 连接成功"
        } else {
            Warn "MySQL 进程存在但无法登录，请检查密码/数据库"
            $allOk = $false
        }
    }
} else {
    Fail "端口 3306 未监听 → MySQL 未启动"
    Info "修复: 运行 console.ps1 菜单 2 → 0（启动 Redis + MySQL）"
    $allOk = $false
}

Write-Host ""

# ── 2. Redis ──
Write-Host "--- Redis (6379) ---" -ForegroundColor $CYAN
if (Check-Port 6379) {
    Ok "端口 6379 监听中"
    $redisCli = "C:\workD\software\redis\redis-cli.exe"
    if (Test-Path $redisCli) {
        $pong = & $redisCli ping 2>$null
        if ($pong -match "PONG") {
            Ok "Redis ping 成功"
        } else {
            Warn "Redis 进程存在但 ping 失败"
            $allOk = $false
        }
    }
} else {
    Fail "端口 6379 未监听 → Redis 未启动"
    Info "修复: 运行 console.ps1 菜单 2 → 0"
    $allOk = $false
}

Write-Host ""

# ── 3. 后端 ──
Write-Host "--- 后端 API (8080) ---" -ForegroundColor $CYAN
if (Check-Port 8080) {
    Ok "端口 8080 监听中"
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get -TimeoutSec 5
        $mysqlStatus = $health.checks.mysql.status
        $redisStatus = $health.checks.redis.status
        if ($mysqlStatus -eq "UP") { Ok "  MySQL 可达 (后端视角)" } else { Fail "  MySQL 不可达 (后端视角)" }
        if ($redisStatus -eq "UP") { Ok "  Redis 可达 (后端视角)" } else { Fail "  Redis 不可达 (后端视角)" }
        if ($mysqlStatus -eq "UP" -and $redisStatus -eq "UP") {
            Ok "后端健康检查通过"
        } else {
            Warn "后端运行中但依赖服务有异常"
            $allOk = $false
        }
    } catch {
        Warn "后端端口已开但 /api/health 无响应，可能仍在启动中"
        Info "查看日志: Get-Content C:\workD\code\TakeoutSystem\logs\takeout-out.log -Tail 10"
        $allOk = $false
    }

    $jarPath = "C:\workD\code\TakeoutSystem\target\takeout-app.jar"
    if (Test-Path $jarPath) {
        Ok "JAR 包存在 ($([math]::Round((Get-Item $jarPath).Length / 1MB, 1)) MB)"
    }
} else {
    Fail "端口 8080 未监听 → 后端未启动"
    Info "修复: 运行 console.ps1 菜单 3 → 1"
    $allOk = $false

    $jarPath = "C:\workD\code\TakeoutSystem\target\takeout-app.jar"
    if (-not (Test-Path $jarPath)) {
        Fail "JAR 包不存在 ({0})" -f $jarPath
        Info "修复: 在项目目录运行 mvn clean package -DskipTests"
    }
}

Write-Host ""

# ── 4. 前端 ──
Write-Host "--- 前端 ---" -ForegroundColor $CYAN
$h5Port = Check-Port 3001
$mchPort = Check-Port 3002
if ($h5Port) { Ok "消费者 H5 (3001) 已启动 → http://localhost:3001" }
else { Warn "消费者 H5 (3001) 未启动 → 需另开终端运行: cd h5 && npm run dev" }
if ($mchPort) { Ok "商家端 (3002) 已启动 → http://localhost:3002" }
else { Warn "商家端 (3002) 未启动 → 需另开终端运行: cd merchant-web && npm run dev" }

Write-Host ""

# ── 5. Redis 缓存状态 ──
Write-Host "--- Redis 缓存状态 ---" -ForegroundColor $CYAN
$redisCli = "C:\workD\software\redis\redis-cli.exe"
if (Check-Port 6379 -and (Test-Path $redisCli)) {
    $merchantKeys = & $redisCli keys "merchant:info:*" 2>$null
    if ($merchantKeys) {
        $count = ($merchantKeys | Measure-Object).Count
        Warn "存在 $count 个商家缓存键 (merchant:info:*)"
        Warn "如果后端刚重启过，建议清理缓存: & `"$redisCli`" flushdb"
    } else {
        Ok "无残留商家缓存"
    }
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor $CYAN
if ($allOk) {
    Write-Host "  所有服务正常，系统可正常使用" -ForegroundColor $GREEN
} else {
    Write-Host "  存在异常，请根据上方 [FAIL]/[WARN] 修复" -ForegroundColor $YELLOW
    Write-Host "  快捷入口: console.ps1 (C:\workD\software\console.ps1)" -ForegroundColor $YELLOW
}
Write-Host "==========================================" -ForegroundColor $CYAN
