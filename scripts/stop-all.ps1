[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Host.UI.RawUI.WindowTitle = "TakeoutSystem - Stopping..."

function Stop-Port($port, $label) {
    $proc = netstat -ano 2>$null | Select-String ":$port\s.*LISTENING" | Select-Object -First 1
    if (-not $proc) {
        Write-Host "  [--] $label (:${port}) not running" -ForegroundColor Yellow
        return
    }
    $pid = ($proc -replace '.*\s+(\d+)$','$1').Trim()
    if ($pid -and $pid -ne "0") {
        try {
            Stop-Process -Id ([int]$pid) -Force -ErrorAction Stop
            Write-Host "  [OK] $label (:${port}) stopped (PID $pid)" -ForegroundColor Green
        } catch {
            Write-Host "  [!!] $label (:${port}) failed to stop: $_" -ForegroundColor Red
        }
    }
}

Clear-Host
Write-Host "  ============================================" -ForegroundColor Cyan
Write-Host "     TakeoutSystem -- Stop All Services" -ForegroundColor Cyan
Write-Host "  ============================================" -ForegroundColor Cyan
Write-Host ""

Stop-Port 6379 "Redis"
Stop-Port 3306 "MySQL"
Stop-Port 8080 "Backend"
Stop-Port 3001 "H5"
Stop-Port 3002 "Merchant"
Stop-Port 3003 "Admin"

Write-Host ""
Write-Host "  All services stopped." -ForegroundColor Green
