﻿﻿﻿[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = "TakeoutSystem Console"

$projectRoot = Resolve-Path "$PSScriptRoot\.."
$java    = "D:\tool\Java\bin\java.exe"
$jar     = "$projectRoot\target\takeout-app.jar"
$logDir  = "$projectRoot\logs"
$logFile = "$logDir\takeout-out.log"

$redisServer = "$PSScriptRoot\..\tools\redis\redis-server.exe"
$redisConf   = "$PSScriptRoot\..\tools\redis\redis.windows.conf"
$redisCli    = "$PSScriptRoot\..\tools\redis\redis-cli.exe"

$mysqlExe   = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
$mysqldExe  = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe"
$mysqlData  = "D:\TakeoutSystem-data\mysql-data"
$initSql    = "$projectRoot\init\sql\init.sql"

function Check-Port($port) {
    $null -ne (netstat -an 2>$null | Select-String ":$port\s" | Select-Object -First 1)
}

function Wait-Port($port, $sec, $label) {
    $d = (Get-Date).AddSeconds($sec)
    while ((Get-Date) -lt $d) {
        if (Check-Port $port) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

function OK   ($s) { Write-Host "  [OK] $s" -ForegroundColor Green }
function Fail ($s) { Write-Host "  [FAIL] $s" -ForegroundColor Red }
function Info ($s) { Write-Host "  [INFO] $s" -ForegroundColor Cyan }
function Warn ($s) { Write-Host "  [WARN] $s" -ForegroundColor Yellow }

function Header($title) {
    Clear-Host
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host "  TakeoutSystem Console - $title" -ForegroundColor Cyan
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host ""
}

function Show-MainMenu {
    Header "Main Menu"
    $status = @()
    $status += if (Check-Port 3306) { "MySQL UP" } else { "MySQL DOWN" }
    $status += if (Check-Port 6379) { "Redis UP" } else { "Redis DOWN" }
    $status += if (Check-Port 8080) { "App UP" } else { "App DOWN" }
    Write-Host "  Status: [$($status -join '] [')]" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  1. Monitor  - Real-time monitoring (2s refresh)" -ForegroundColor Cyan
    Write-Host "  2. Infra    - Start Redis / MySQL / Init DB" -ForegroundColor Cyan
    Write-Host "  3. App      - Start / Stop backend, View logs" -ForegroundColor Cyan
    Write-Host "  4. Query    - Users / Orders / Merchants / Cart / Custom SQL" -ForegroundColor Cyan
    Write-Host "  Q. Quit" -ForegroundColor Cyan
    Write-Host ""
}

function Show-InfraMenu {
    Header "Infrastructure Management"
    Write-Host "  0. Start Redis + MySQL" -ForegroundColor Cyan
    Write-Host "  1. Start Redis only" -ForegroundColor Cyan
    Write-Host "  2. Start MySQL only" -ForegroundColor Cyan
    Write-Host "  3. Initialize database" -ForegroundColor Cyan
    Write-Host "  B. Back to main menu" -ForegroundColor Cyan
    Write-Host ""
}

function Show-AppMenu {
    Header "Application Management"
    Write-Host "  1. Start backend" -ForegroundColor Cyan
    Write-Host "  2. Stop backend" -ForegroundColor Cyan
    Write-Host "  3. View logs (tail 20)" -ForegroundColor Cyan
    Write-Host "  4. Tail logs (follow)" -ForegroundColor Cyan
    Write-Host "  B. Back to main menu" -ForegroundColor Cyan
    Write-Host ""
}

function Show-QueryMenu {
    Header "Data Query"
    Write-Host "  1. Query users (LIMIT 10)" -ForegroundColor Cyan
    Write-Host "  2. Query orders (latest 10)" -ForegroundColor Cyan
    Write-Host "  3. Query merchants" -ForegroundColor Cyan
    Write-Host "  4. Query shopping cart" -ForegroundColor Cyan
    Write-Host "  5. Custom SQL" -ForegroundColor Cyan
    Write-Host "  B. Back to main menu" -ForegroundColor Cyan
    Write-Host ""
}

function Run-MySQLQuery($sql) {
    $conn = [System.Data.SqlClient.SqlConnection]::new()
    $conn.ConnectionString = "Server=localhost;Port=3306;Database=db_takeout;Uid=root;Pwd=root;CharSet=utf8mb4;"
    try {
        $conn.Open()
        $cmd = $conn.CreateCommand()
        $cmd.CommandText = $sql
        $rdr = $cmd.ExecuteReader()
        $cols = @()
        for ($i = 0; $i -lt $rdr.FieldCount; $i++) { $cols += $rdr.GetName($i) }
        Write-Host ""
        $cols | ForEach-Object { Write-Host ("{0,-25}" -f $_) -NoNewline -ForegroundColor Yellow }
        Write-Host ""
        Write-Host "  " ("-" * (25 * $cols.Count))
        $rowCount = 0
        while ($rdr.Read()) {
            $rowCount++
            Write-Host "  " -NoNewline
            for ($i = 0; $i -lt $rdr.FieldCount; $i++) {
                $val = if ($rdr.IsDBNull($i)) { "NULL" } else { $rdr.GetValue($i).ToString() }
                if ($val.Length -gt 24) { $val = $val.Substring(0, 21) + "..." }
                Write-Host ("{0,-25}" -f $val) -NoNewline
            }
            Write-Host ""
            if ($rowCount -ge 50) { Write-Host "  ... (truncated at 50 rows)"; break }
        }
        $rdr.Close()
        Write-Host ""
        Write-Host "  [$rowCount rows]" -ForegroundColor Green
        Write-Host ""
    } catch {
        Fail "Query failed: $($_.Exception.Message)"
    } finally {
        if ($conn.State -eq 'Open') { $conn.Close() }
    }
}

# -- Menu 1: Monitor --
function Menu-Monitor {
    Header "Real-time Monitor (press Ctrl+C to exit)"
    try {
        while ($true) {
            Clear-Host
            Write-Host "============================================" -ForegroundColor Cyan
            Write-Host "  Real-time Monitor ($(Get-Date -Format 'HH:mm:ss'))" -ForegroundColor Cyan
            Write-Host "============================================" -ForegroundColor Cyan
            Write-Host ""

            Write-Host "--- Port Status ---" -ForegroundColor Yellow
            $ports = @(
                @{n="MySQL   :3306"; p=3306},
                @{n="Redis   :6379"; p=6379},
                @{n="App     :8080"; p=8080},
                @{n="H5      :3001"; p=3001},
                @{n="Merchant:3002"; p=3002},
                @{n="Admin   :3003"; p=3003}
            )
            foreach ($svc in $ports) {
                $up = Check-Port $svc.p
                $tag = if ($up) { "[UP]" } else { "[DOWN]" }
                $col = if ($up) { "Green" } else { "Red" }
                Write-Host "  $tag  $($svc.n)" -ForegroundColor $col
            }
            Write-Host ""

            Write-Host "--- SMS Verification Codes ---" -ForegroundColor Yellow
            if (Check-Port 6379 -and (Test-Path $redisCli)) {
                $keys = & $redisCli keys "sms:code:*" 2>$null
                if ($keys) {
                    foreach ($key in $keys) {
                        $val = & $redisCli get $key 2>$null
                        $ttl = & $redisCli ttl $key 2>$null
                        Write-Host "  $key = $val (TTL: ${ttl}s)" -ForegroundColor Gray
                    }
                } else {
                    Write-Host "  (no SMS cache)" -ForegroundColor Gray
                }
            } else {
                Write-Host "  (Redis not running)" -ForegroundColor Gray
            }
            Write-Host ""

            Write-Host "--- ERROR Logs (last 5) ---" -ForegroundColor Yellow
            if (Test-Path $logFile) {
                $errors = Get-Content $logFile -Tail 100 | Select-String "ERROR" -SimpleMatch | Select-Object -Last 5
                if ($errors) {
                    $errors | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
                } else {
                    Write-Host "  (no ERROR logs)" -ForegroundColor Gray
                }
            } else {
                Write-Host "  (log file not found)" -ForegroundColor Gray
            }
            Write-Host ""
            Write-Host "  (press Ctrl+C to return to main menu)" -ForegroundColor DarkGray
            Start-Sleep -Seconds 2
        }
    } catch {
    }
}

# -- Menu 2: Infra --
function Menu-Infra {
    do {
        Show-InfraMenu
        $c = Read-Host "Select"
        switch ($c) {
            "0" {
                Write-Host ""
                Write-Host "  Starting Redis..." -ForegroundColor Cyan
                if (Check-Port 6379) { Warn "Redis already running" }
                else {
                    Start-Process $redisServer -ArgumentList @($redisConf) -WindowStyle Hidden
                    if (Wait-Port 6379 10 "Redis") { OK "Redis :6379" }
                    else { Fail "Redis startup timeout" }
                }
                Write-Host ""
                Write-Host "  Starting MySQL..." -ForegroundColor Cyan
                if (Check-Port 3306) { Warn "MySQL already running" }
                else {
                    Start-Process $mysqldExe -ArgumentList "--datadir=$mysqlData","--port=3306" -WindowStyle Hidden
                    if (Wait-Port 3306 20 "MySQL") { OK "MySQL :3306" }
                    else { Fail "MySQL startup timeout" }
                }
                Start-Sleep -Seconds 2
            }
            "1" {
                if (Check-Port 6379) { Warn "Redis already running" }
                else {
                    Start-Process $redisServer -ArgumentList @($redisConf) -WindowStyle Hidden
                    if (Wait-Port 6379 10 "Redis") { OK "Redis :6379" }
                    else { Fail "Redis startup timeout" }
                }
                Start-Sleep -Seconds 2
            }
            "2" {
                if (Check-Port 3306) { Warn "MySQL already running" }
                else {
                    Start-Process $mysqldExe -ArgumentList "--datadir=$mysqlData","--port=3306" -WindowStyle Hidden
                    if (Wait-Port 3306 20 "MySQL") { OK "MySQL :3306" }
                    else { Fail "MySQL startup timeout" }
                }
                Start-Sleep -Seconds 2
            }
            "3" {
                Write-Host ""
                Write-Host "  Initializing database..." -ForegroundColor Cyan
                if (-not (Test-Path $mysqlExe)) { Fail "mysql.exe not found" }
                elseif (-not (Test-Path $initSql)) { Fail "init.sql not found: $initSql" }
                else {
                    Get-Content $initSql | & $mysqlExe -uroot -proot --default-character-set=utf8mb4 db_takeout 2>&1
                    if ($LASTEXITCODE -eq 0) { OK "Database initialized" }
                    else { Fail "Initialization failed (see above)" }
                }
                Start-Sleep -Seconds 3
            }
        }
    } while ($c -ne "b" -and $c -ne "B")
}

# -- Menu 3: App --
function Menu-App {
    do {
        Show-AppMenu
        $c = Read-Host "Select"
        switch ($c) {
            "1" {
                if (Check-Port 8080) { Warn "Backend already running" }
                else {
                    if (-not (Test-Path $jar)) { Fail "JAR not found - build first: mvn package -DskipTests" }
                    else {
                        if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
                        Start-Process $java -ArgumentList @("-Xmx512m","-Xms256m","-jar",$jar) -WindowStyle Hidden `
                            -RedirectStandardOutput $logFile `
                            -RedirectStandardError "$logDir\takeout-err.log"
                        if (Wait-Port 8080 40 "Backend") { OK "Backend :8080" }
                        else { Fail "Backend startup timeout - check logs" }
                    }
                }
                Start-Sleep -Seconds 2
            }
            "2" {
                $pid8080 = (netstat -ano | Select-String ":8080\s.*LISTENING" | Select-Object -First 1) -replace '.*\s(\d+)$','$1'
                if ($pid8080) {
                    Stop-Process -Id ([int]$pid8080.Trim()) -Force
                    Start-Sleep -Seconds 1
                    if (Check-Port 8080) { Fail "Cannot stop backend" } else { OK "Backend stopped" }
                } else {
                    Warn "Backend not running"
                }
                Start-Sleep -Seconds 2
            }
            "3" {
                if (Test-Path $logFile) {
                    Write-Host ""
                    Write-Host "  --- Last 20 log lines ---" -ForegroundColor Yellow
                    Get-Content $logFile -Tail 20
                } else {
                    Warn "Log file not found"
                }
                Write-Host ""
                Write-Host "  Press Enter to return..." -NoNewline
                $null = Read-Host
            }
            "4" {
                if (Test-Path $logFile) {
                    Write-Host ""
                    Write-Host "  Tailing logs (Ctrl+C to stop)..." -ForegroundColor Yellow
                    try { Get-Content $logFile -Tail 5 -Wait } catch {}
                } else {
                    Warn "Log file not found"
                    Start-Sleep -Seconds 2
                }
            }
        }
    } while ($c -ne "b" -and $c -ne "B")
}

# -- Menu 4: Query --
function Menu-Query {
    do {
        Show-QueryMenu
        $c = Read-Host "Select"
        switch ($c) {
            "1" { Run-MySQLQuery "SELECT id,phone,nickname,status,role FROM t_user LIMIT 10"; $null = Read-Host "Press Enter to return" }
            "2" { Run-MySQLQuery "SELECT id,order_no,user_id,merchant_id,status,total_price,created_at FROM t_order ORDER BY id DESC LIMIT 10"; $null = Read-Host "Press Enter to return" }
            "3" { Run-MySQLQuery "SELECT id,name,phone,status FROM t_merchant LIMIT 10"; $null = Read-Host "Press Enter to return" }
            "4" { Run-MySQLQuery "SELECT id,user_id,merchant_id,dish_id,dish_name,quantity,unit_price FROM t_cart LIMIT 10"; $null = Read-Host "Press Enter to return" }
            "5" {
                $sql = Read-Host "`n  Enter SQL"
                if ($sql.Trim() -ne "") { Run-MySQLQuery $sql }
                $null = Read-Host "Press Enter to return"
            }
        }
    } while ($c -ne "b" -and $c -ne "B")
}

# -- Main Loop --
do {
    Show-MainMenu
    $choice = Read-Host "Select"
    switch ($choice) {
        "1" { Menu-Monitor }
        "2" { Menu-Infra }
        "3" { Menu-App }
        "4" { Menu-Query }
    }
} while ($choice -ne "q" -and $choice -ne "Q")

Clear-Host
Write-Host "  Goodbye!" -ForegroundColor Cyan
