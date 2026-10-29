<#
.SYNOPSIS
    Stops the local Project Aegis backend application and the local PostgreSQL server.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "SilentlyContinue"
$rootDir = (Resolve-Path "$PSScriptRoot\..").Path

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  Project Aegis: Local Development Teardown" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

# 1. Stop Aegis Application
$appPidFile = "$rootDir\scratch\app.pid"
if (Test-Path $appPidFile) {
    $appPid = Get-Content $appPidFile -ErrorAction SilentlyContinue
    if ($appPid) {
        Write-Host "`n[1/2] Stopping Aegis application (PID: $appPid)..." -ForegroundColor Yellow
        Stop-Process -Id $appPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $appPidFile -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "`n[1/2] No app.pid found. Ensuring any lingering aegis processes are stopped..." -ForegroundColor Yellow
}

# Also ensure any java process running aegis-app is terminated
Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -match "aegis-app"
} | Stop-Process -Force -ErrorAction SilentlyContinue
Write-Host "  Aegis application stopped." -ForegroundColor Green

# 2. Stop PostgreSQL Server (if managed via pid)
Write-Host "`n[2/2] Stopping local PostgreSQL server..." -ForegroundColor Yellow
$pgPidFile = "$rootDir\scratch\postgres.pid"
if (Test-Path $pgPidFile) {
    $pgPid = Get-Content $pgPidFile -ErrorAction SilentlyContinue
    if ($pgPid) {
        Stop-Process -Id $pgPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $pgPidFile -Force -ErrorAction SilentlyContinue
}

$pgCtl = "$rootDir\scratch\pgsql16\pgsql\bin\pg_ctl.exe"
$pgDataDir = "$rootDir\scratch\pgdata"
if ((Test-Path $pgCtl) -and (Test-Path "$pgDataDir\PG_VERSION")) {
    & $pgCtl -D "$pgDataDir" stop -m fast | Out-Null
}

Write-Host "  Local PostgreSQL stopped." -ForegroundColor Green

Write-Host "`n[dev-down] Local development environment successfully stopped.`n" -ForegroundColor Green
