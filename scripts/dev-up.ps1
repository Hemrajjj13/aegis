<#
.SYNOPSIS
    Starts local PostgreSQL (without Docker) using an installed/portable PostgreSQL 16 instance,
    applies schema migrations, seeds the fixed local dev tenant, and starts Project Aegis under the tier0 profile.
.DESCRIPTION
    Uses PostgreSQL 16 via environment variables:
      AEGIS_PG_URL      (default: jdbc:postgresql://127.0.0.1:5432/aegis)
      AEGIS_PG_USER     (default: aegis)
      AEGIS_PG_PASSWORD (default: aegis)
#>

[CmdletBinding()]
param(
    [int]$Port = 5432,
    [switch]$NoApp,
    [switch]$Wait
)

$ErrorActionPreference = "Stop"
$rootDir = (Resolve-Path "$PSScriptRoot\..").Path
Set-Location $rootDir

function Invoke-DevDbTool([string]$command, [int]$dbPort = 5432) {
    $pgJar = (Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.postgresql\postgresql" -Filter "postgresql-*.jar" -Recurse | Select-Object -First 1).FullName
    $classesDir = "$rootDir\backend\aegis-storage\build\classes\java\main"
    $cp = "$classesDir;$pgJar"
    & java -cp "$cp" com.aegis.storage.dev.DevDatabaseTool $command $dbPort
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  Project Aegis: Local Development Startup (Tier 0 without Docker)" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

# 1. Verify PostgreSQL 16 Availability
Write-Host "`n[1/6] Checking PostgreSQL on port $Port..." -ForegroundColor Yellow
$portOpen = (Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue)

if (-not $portOpen) {
    # Check for local portable pgsql16 binary
    $localPg = "$rootDir\scratch\pgsql16\pgsql\bin\postgres.exe"
    $pgDataDir = "$rootDir\scratch\pgdata"

    if ((Test-Path $localPg) -and (Test-Path "$pgDataDir\PG_VERSION")) {
        Write-Host "  Starting local portable PostgreSQL from $localPg..." -ForegroundColor Gray
        $pgProc = Start-Process -FilePath $localPg -ArgumentList "-D", "`"$pgDataDir`"", "-p", "$Port", "-h", "127.0.0.1" -PassThru
        $pgProc.Id | Out-File -FilePath "$rootDir\scratch\postgres.pid" -Encoding ascii

        $started = $false
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Milliseconds 500
            if (Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue) {
                $started = $true
                break
            }
        }
        if (-not $started) {
            throw "PostgreSQL failed to bind to port $Port within 15 seconds."
        }
        Write-Host "  PostgreSQL 16 server running (PID: $($pgProc.Id))." -ForegroundColor Green
    } else {
        throw @"
PostgreSQL 16 is not reachable on port $Port and no portable instance was found at $localPg.
Please install and start PostgreSQL 16, or set:
  AEGIS_PG_URL=jdbc:postgresql://127.0.0.1:$Port/aegis
  AEGIS_PG_USER=aegis
  AEGIS_PG_PASSWORD=aegis
See README.md for complete instructions.
"@
    }
} else {
    Write-Host "  PostgreSQL is active and responding on port $Port." -ForegroundColor Green
}

# 2. Build application JAR
Write-Host "`n[2/6] Building backend application JAR..." -ForegroundColor Yellow
& ".\backend\gradlew.bat" -p ".\backend" :aegis-app:bootJar -x test | Out-Null

$jarPath = (Get-ChildItem -Path "backend\aegis-app\build\libs\*.jar" | Where-Object { $_.Name -notmatch "-plain\.jar$" } | Select-Object -First 1).FullName
if (-not $jarPath) {
    throw "Could not locate compiled aegis-app JAR in backend\aegis-app\build\libs."
}
Write-Host "  JAR built: $jarPath" -ForegroundColor Green

# 3. Provision aegis database and role via DevDatabaseTool
Write-Host "`n[3/6] Provisioning 'aegis' database and role..." -ForegroundColor Yellow
Invoke-DevDbTool "provision" $Port
Write-Host "  Database 'aegis' ready." -ForegroundColor Green

if ($NoApp) {
    Write-Host "`n[dev-up] -NoApp flag provided. Skipping application launch." -ForegroundColor Yellow
    return
}

# 4. Launch Project Aegis under 'tier0' profile
Write-Host "`n[4/6] Launching Project Aegis under 'tier0' profile..." -ForegroundColor Yellow
$appLog = "$rootDir\scratch\app.log"
$appErr = "$rootDir\scratch\app.err"

$env:AEGIS_PG_URL = if ($env:AEGIS_PG_URL) { $env:AEGIS_PG_URL } else { "jdbc:postgresql://127.0.0.1:$Port/aegis" }
$env:AEGIS_PG_USER = if ($env:AEGIS_PG_USER) { $env:AEGIS_PG_USER } else { "aegis" }
$env:AEGIS_PG_PASSWORD = if ($env:AEGIS_PG_PASSWORD) { $env:AEGIS_PG_PASSWORD } else { "aegis" }

$appProc = Start-Process -FilePath "java" -ArgumentList "-jar", "`"$jarPath`"", "--spring.profiles.active=tier0" `
    -RedirectStandardOutput $appLog -RedirectStandardError $appErr -PassThru
$appProc.Id | Out-File -FilePath "$rootDir\scratch\app.pid" -Encoding ascii

Write-Host "  Waiting for Aegis API on port 8080..." -ForegroundColor Gray
$appReady = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 1000
    try {
        $resp = Invoke-WebRequest -Uri "http://127.0.0.1:8080/v1/decisions" -Method Post `
            -Headers @{ "X-Tenant-Id" = "00000000-0000-0000-0000-000000000001" } `
            -ContentType "application/json" -Body "{}" -ErrorAction SilentlyContinue
        if ($resp.StatusCode -eq 400 -or $resp.StatusCode -eq 200) {
            $appReady = $true
            break
        }
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 400) {
            $appReady = $true
            break
        }
    }
}

if (-not $appReady) {
    throw "Aegis backend failed to respond on http://localhost:8080 within 40 seconds. Check scratch\app.log."
}

# 5. Seed fixed dev tenant: 00000000-0000-0000-0000-000000000001
Invoke-DevDbTool "seed-tenant" $Port

Write-Host "`n================================================================================" -ForegroundColor Green
Write-Host "  Project Aegis is LIVE!" -ForegroundColor Green
Write-Host "  - Profile:         tier0" -ForegroundColor Green
Write-Host "  - API Base URL:    http://localhost:8080/v1" -ForegroundColor Green
Write-Host "  - Dev Tenant UUID: 00000000-0000-0000-0000-000000000001" -ForegroundColor Green
Write-Host "  - App PID:         $($appProc.Id)" -ForegroundColor Green
Write-Host "  - App Log:         $appLog" -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Green

if ($Wait) {
    Write-Host "`n[dev-up] Running in daemon mode. Waiting on app PID $($appProc.Id)..." -ForegroundColor Cyan
    Wait-Process -Id $appProc.Id
}
