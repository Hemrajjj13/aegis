<#
.SYNOPSIS
    Demonstrates the Project Aegis vertical slice:
    1. Connects to SSE live decision stream.
    2. POSTs a transaction to /v1/decisions and verifies 200 response.
    3. Re-POSTs the duplicate transaction and verifies idempotent 200 response.
    4. Verifies SSE stream received exactly ONE decision broadcast (suppression of duplicate).
    5. Queries and prints PostgreSQL row counts (tenants, processed_events, decisions, outbox).
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$TenantId = "00000000-0000-0000-0000-000000000001",
    [int]$Port = 5432
)

$ErrorActionPreference = "Stop"
$rootDir = (Resolve-Path "$PSScriptRoot\..").Path

function Invoke-DevDbTool([string]$command, [int]$dbPort = 5432) {
    $pgJar = (Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.postgresql\postgresql" -Filter "postgresql-*.jar" -Recurse | Select-Object -First 1).FullName
    $classesDir = "$rootDir\backend\aegis-storage\build\classes\java\main"
    $cp = "$classesDir;$pgJar"
    & java -cp "$cp" com.aegis.storage.dev.DevDatabaseTool $command $dbPort
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  Project Aegis: Live Vertical Slice Demo" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

# 0. Check application availability
Write-Host "`n[0/5] Checking Aegis backend at $BaseUrl..." -ForegroundColor Yellow
$appReady = $false
try {
    $null = Invoke-WebRequest -Uri "$BaseUrl/v1/decisions" -Method Post `
        -Headers @{ "X-Tenant-Id" = $TenantId } `
        -ContentType "application/json" -Body "{}" -ErrorAction Stop
    $appReady = $true
} catch {
    if ($_.Exception.Response) {
        $code = [int]$_.Exception.Response.StatusCode
        if ($code -eq 400 -or $code -eq 200) {
            $appReady = $true
        }
    }
}

if (-not $appReady) {
    Write-Host "  Application is not running on $BaseUrl. Starting via dev-up.ps1..." -ForegroundColor Yellow
    & "$PSScriptRoot\dev-up.ps1"
} else {
    Write-Host "  Application is active and healthy." -ForegroundColor Green
}

# 1. Start SSE stream reader in background
Write-Host "`n[1/5] Subscribing to SSE event stream (GET /v1/events/stream)..." -ForegroundColor Yellow
$sseLog = "$rootDir\scratch\demo-sse.log"
if (Test-Path $sseLog) { Remove-Item $sseLog -Force }

$streamUrl = "$BaseUrl/v1/events/stream"
$sseProc = Start-Process -FilePath "curl.exe" `
    -ArgumentList "-N", "-s", "-H", "`"X-Tenant-Id: $TenantId`"", "$streamUrl" `
    -RedirectStandardOutput $sseLog -RedirectStandardError "$rootDir\scratch\demo-sse.err" -PassThru

# Wait briefly for SSE stream connection handshake
Start-Sleep -Milliseconds 1200
if (Test-Path $sseLog) {
    $initialStream = Get-Content $sseLog -Raw
    Write-Host "  SSE Stream connected successfully." -ForegroundColor Green
} else {
    Write-Host "  Warning: SSE stream log not yet created, continuing..." -ForegroundColor Gray
}

# 2. POST initial transaction event matching OpenAPI TransactionEvent schema
$eventId = [System.Guid]::NewGuid().ToString()
$eventTime = [System.DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
$txPayload = @"
{
  "event_id": "$eventId",
  "event_time": "$eventTime",
  "sequence_number": 0,
  "tenant_id": "$TenantId",
  "card_token": "tok_visa_4242424242424242",
  "amount_minor_units": 7500000,
  "currency": "INR",
  "payment_method": "CARD",
  "merchant_id": "merch_starbucks_mumbai_01",
  "merchant_name": "Starbucks Coffee Mumbai",
  "channel": "POS",
  "custom_fields": {
    "terminal_id": "term_pos_001",
    "location": "Mumbai, IN"
  }
}
"@

Write-Host "`n[2/5] Sending initial POST /v1/decisions (amount: 75,000 INR >= 50,000 threshold)..." -ForegroundColor Yellow
Write-Host "  Event ID: $eventId" -ForegroundColor Gray
$resp1 = Invoke-RestMethod -Uri "$BaseUrl/v1/decisions" -Method Post `
    -Headers @{ "X-Tenant-Id" = $TenantId } `
    -ContentType "application/json" -Body $txPayload

Write-Host "  HTTP 200 Decision Received:" -ForegroundColor Green
$resp1Json = $resp1 | ConvertTo-Json -Depth 5
Write-Host $resp1Json -ForegroundColor White

if ($resp1.outcome -ne "REVIEW") {
    throw "Expected outcome REVIEW for amount 75000 INR, got $($resp1.outcome)"
}

# 3. POST duplicate transaction event (same event_id)
Write-Host "`n[3/5] Sending duplicate POST /v1/decisions with identical event_id (Idempotency Test)..." -ForegroundColor Yellow
$resp2 = Invoke-RestMethod -Uri "$BaseUrl/v1/decisions" -Method Post `
    -Headers @{ "X-Tenant-Id" = $TenantId } `
    -ContentType "application/json" -Body $txPayload

Write-Host "  HTTP 200 Duplicate Replay Response:" -ForegroundColor Green
$resp2Json = $resp2 | ConvertTo-Json -Depth 5
Write-Host $resp2Json -ForegroundColor White

if ($resp1.sequence_number -ne $resp2.sequence_number) {
    throw "Idempotency violated: sequence numbers differ ($($resp1.sequence_number) vs $($resp2.sequence_number))"
}

# 4. Verify SSE stream output
Write-Host "`n[4/5] Reading captured SSE stream log..." -ForegroundColor Yellow
Start-Sleep -Milliseconds 800
# Terminate curl background reader cleanly
if ($sseProc -and -not $sseProc.HasExited) {
    Stop-Process -Id $sseProc.Id -Force -ErrorAction SilentlyContinue
}

$sseRaw = ""
if (Test-Path $sseLog) {
    $sseRaw = Get-Content $sseLog -Raw
}

Write-Host "--- Raw SSE Stream Output ---" -ForegroundColor DarkGray
Write-Host $sseRaw -ForegroundColor White
Write-Host "-----------------------------" -ForegroundColor DarkGray

# Count occurrences of 'event: decision'
$decisionEventMatches = [regex]::Matches($sseRaw, "(?m)^event:\s*decision\s*$")
$decisionCount = $decisionEventMatches.Count

Write-Host "  Decision SSE events detected: $decisionCount" -ForegroundColor Green
if ($decisionCount -eq 1) {
    Write-Host "  [SUCCESS] Duplicate POST did NOT broadcast a second SSE event! (Suppressed as required)" -ForegroundColor Green
} else {
    throw "SSE broadcast failure: Expected exactly 1 decision event, found $decisionCount"
}

# 5. Print SQL row counts
Write-Host "`n[5/5] Querying PostgreSQL database row counts..." -ForegroundColor Yellow
Invoke-DevDbTool "row-counts" $Port

Write-Host "`n================================================================================" -ForegroundColor Green
Write-Host "  Project Aegis Vertical Slice Demo Completed Successfully!" -ForegroundColor Green
Write-Host "================================================================================`n" -ForegroundColor Green
