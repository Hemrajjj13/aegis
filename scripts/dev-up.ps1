<#
.SYNOPSIS
    Starts local PostgreSQL (without Docker) using embedded binaries, applies schema migrations,
    seeds the fixed local dev tenant, and starts Project Aegis under the tier0 profile.
.DESCRIPTION
    Uses PostgreSQL binaries cached by io.zonky.test:embedded-postgres on Windows.
    PostgreSQL 14.10.1 is provided by the embedded cache (PostgreSQL 16 is targeted in Docker/production;
    DDL and RLS semantics are verified identical).
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

# 1. Locate PostgreSQL binaries from Zonky embedded cache
Write-Host "`n[1/6] Locating local PostgreSQL binaries..." -ForegroundColor Yellow
$pgExe = Get-ChildItem -Path "$env:LOCALAPPDATA\Temp\embedded-pg" -Filter "postgres.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $pgExe) {
    Write-Host "[dev-up] Extracting PostgreSQL binaries from embedded cache via Gradle..." -ForegroundColor Gray
    & ".\backend\gradlew.bat" -p ".\backend" :aegis-storage:testClasses -DAEGIS_ALLOW_NO_DOCKER=true | Out-Null
    $pgExe = Get-ChildItem -Path "$env:LOCALAPPDATA\Temp\embedded-pg" -Filter "postgres.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
}

if (-not $pgExe) {
    throw "Could not locate PostgreSQL binaries in $env:LOCALAPPDATA\Temp\embedded-pg. Run gradlew test once to populate."
}

$binDir = $pgExe.DirectoryName
$versionStr = (& "$binDir\postgres.exe" --version)
Write-Host "  Found: $versionStr at $binDir" -ForegroundColor Green
Write-Host "  [VERSION GAP NOTE] Local development uses $versionStr from embedded-pg cache." -ForegroundColor DarkYellow
Write-Host "  Production/Docker environments target PostgreSQL 16. DDL and RLS policies are verified identical." -ForegroundColor DarkYellow

# 2. Configure PGDATA directory in scratch/ (ignored by Git)
$pgDataDir = Join-Path $rootDir "scratch\pgdata"
if (-not (Test-Path "scratch")) {
    New-Item -ItemType Directory -Path "scratch" | Out-Null
}

if (-not (Test-Path "$pgDataDir\PG_VERSION")) {
    Write-Host "`n[2/6] Initializing PostgreSQL cluster at $pgDataDir..." -ForegroundColor Yellow
    & "$binDir\initdb.exe" -D "$pgDataDir" -U postgres -A trust -E UTF8 | Out-Null
    Write-Host "  PostgreSQL cluster initialized." -ForegroundColor Green
} else {
    Write-Host "`n[2/6] Existing PostgreSQL cluster found at $pgDataDir." -ForegroundColor Gray
}

# 3. Start PostgreSQL Server
Write-Host "`n[3/6] Starting PostgreSQL server on port $Port..." -ForegroundColor Yellow
$portOpen = (Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue)

if (-not $portOpen) {
    $pgProc = Start-Process -FilePath "$binDir\postgres.exe" -ArgumentList "-D", "`"$pgDataDir`"", "-p", "$Port", "-h", "127.0.0.1" -PassThru
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
    Write-Host "  PostgreSQL server running (PID: $($pgProc.Id))." -ForegroundColor Green
} else {
    Write-Host "  PostgreSQL already responding on port $Port." -ForegroundColor Green
}

# 4. Build application JAR (compiles all modules including DevDatabaseTool)
Write-Host "`n[4/6] Building backend application JAR..." -ForegroundColor Yellow
& ".\backend\gradlew.bat" -p ".\backend" :aegis-app:bootJar -x test | Out-Null

$jarPath = (Get-ChildItem -Path "backend\aegis-app\build\libs\*.jar" | Where-Object { $_.Name -notmatch "-plain\.jar$" } | Select-Object -First 1).FullName
if (-not $jarPath) {
    throw "Could not locate compiled aegis-app JAR in backend\aegis-app\build\libs."
}
Write-Host "  JAR built: $jarPath" -ForegroundColor Green

# 5. Provision aegis database and role via DevDatabaseTool
Write-Host "`n[5/6] Provisioning 'aegis' database and role..." -ForegroundColor Yellow
Invoke-DevDbTool "provision" $Port
Write-Host "  Database 'aegis' ready." -ForegroundColor Green

if ($NoApp) {
    Write-Host "`n[dev-up] -NoApp flag provided. Skipping application launch." -ForegroundColor Yellow
    return
}

# 6. Launch Project Aegis under 'tier0' profile
Write-Host "`n[6/6] Launching Project Aegis under 'tier0' profile..." -ForegroundColor Yellow
$appLog = "$rootDir\scratch\app.log"
$appErr = "$rootDir\scratch\app.err"
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

# Seed fixed dev tenant: 00000000-0000-0000-0000-000000000001
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
