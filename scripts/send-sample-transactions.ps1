<#
.SYNOPSIS
    Sends sample transaction events from scripts/sample-transactions.json to a running
    Project Aegis backend API and displays the resulting decisions.

.DESCRIPTION
    IMPORTANT: All card numbers and tokens used in this script and sample-transactions.json
    are simulated test identifiers (e.g. tok_visa_4242424242424242, tok_mastercard_5555555555554444).
    Project Aegis strictly adheres to the Zero PAN Invariant and never accepts or stores raw
    Primary Account Numbers (PANs).

.PARAMETER BaseUrl
    Base URL of the Aegis API Gateway. Default: "http://localhost:8080".

.PARAMETER TenantId
    Target Tenant UUID. Default: "00000000-0000-0000-0000-000000000001" (Acme Payments India).

.PARAMETER Token
    Optional Bearer access token. If omitted, the script checks scratch\token.txt or attempts
    unauthenticated dev-header submission under the tier0 profile.

.EXAMPLE
    .\scripts\send-sample-transactions.ps1

.EXAMPLE
    .\scripts\send-sample-transactions.ps1 -BaseUrl "http://localhost:8080" -TenantId "00000000-0000-0000-0000-000000000001" -Token "eyJhbGciOi..."
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TenantId = "00000000-0000-0000-0000-000000000001",
    [string]$Token = ""
)

$ErrorActionPreference = "Continue"
$rootDir = (Resolve-Path "$PSScriptRoot\..").Path
$sampleFile = Join-Path $rootDir "scripts\sample-transactions.json"

if (-not (Test-Path $sampleFile)) {
    Write-Error "Sample transactions file not found at: $sampleFile"
    exit 1
}

# Resolve Bearer Token
if ([string]::IsNullOrWhiteSpace($Token)) {
    $tokenFile = Join-Path $rootDir "scratch\token.txt"
    if (Test-Path $tokenFile) {
        $Token = (Get-Content $tokenFile -Raw).Trim()
        Write-Host "Loaded Bearer token from scratch\token.txt" -ForegroundColor DarkGray
    }
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  Project Aegis: Sample Transaction Dispatcher" -ForegroundColor Cyan
Write-Host "  API Endpoint: $BaseUrl/v1/decisions" -ForegroundColor Cyan
Write-Host "  Tenant ID:    $TenantId" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "NOTE: All tokens in sample-transactions.json are simulated synthetic test data.`n" -ForegroundColor DarkYellow

$samples = Get-Content $sampleFile -Raw | ConvertFrom-Json
$results = @()
$counter = 0

foreach ($item in $samples) {
    $counter++
    $desc = $item.description
    $expected = $item.expected_outcome
    $payload = $item.payload

    # Ensure tenant_id matches target tenant parameter
    if ($payload.tenant_id) {
        $payload.tenant_id = $TenantId
    }

    $jsonBody = $payload | ConvertTo-Json -Depth 5 -Compress

    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantId
    }
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers["Authorization"] = "Bearer $Token"
    }

    Write-Host "[$counter/$($samples.Count)] Sending: $($payload.event_id) ($desc)..." -ForegroundColor Yellow

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $actualOutcome = ""
    $score = $null
    $rulesFired = ""
    $latencyMs = $null
    $decisionId = ""

    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/v1/decisions" `
            -Method Post `
            -Headers $headers `
            -Body $jsonBody `
            -ContentType "application/json" `
            -ErrorAction Stop

        $stopwatch.Stop()
        $actualOutcome = $response.outcome
        $score = $response.score
        $decisionId = $response.decision_id
        $latencyMs = [math]::Round($response.latency_ms, 3)

        if ($response.rules_fired -and $response.rules_fired.Count -gt 0) {
            $rulesFired = ($response.rules_fired | ForEach-Object { $_.rule_id }) -join ", "
        }
        else {
            $rulesFired = "(none)"
        }

        Write-Host "  -> Decision: $actualOutcome (Score: $score, Latency: $($latencyMs)ms, Decision ID: $decisionId)" -ForegroundColor Green
    }
    catch {
        $stopwatch.Stop()
        $statusCode = $null
        $errorDetail = ""

        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $errBody = $reader.ReadToEnd() | ConvertFrom-Json
                $errorDetail = "$($errBody.type) - $($errBody.title)"
            }
            catch {
                $errorDetail = $_.Exception.Message
            }
        }
        else {
            $errorDetail = $_.Exception.Message
        }

        $actualOutcome = "HTTP $statusCode"
        $rulesFired = $errorDetail
        $latencyMs = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 3)

        Write-Host "  -> Response: $actualOutcome ($errorDetail)" -ForegroundColor Red
    }

    $amountInr = [math]::Round(($payload.amount_minor_units / 100.0), 2)

    $results += [PSCustomObject]@{
        Case       = $counter
        EventId    = $payload.event_id
        Merchant   = $payload.merchant_name
        AmountINR  = $amountInr
        Expected   = $expected
        Actual     = $actualOutcome
        Score      = if ($null -ne $score) { $score } else { "-" }
        LatencyMs  = if ($null -ne $latencyMs) { $latencyMs } else { "-" }
        RulesFired = $rulesFired
    }

    Start-Sleep -Milliseconds 100
}

Write-Host "`n================================================================================" -ForegroundColor Cyan
Write-Host "  Batch Dispatch Summary" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
$results | Format-Table -Property Case, EventId, AmountINR, Expected, Actual, Score, LatencyMs, RulesFired -AutoSize

