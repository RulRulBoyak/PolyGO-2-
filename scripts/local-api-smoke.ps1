#Requires -Version 5.1
param(
    [ValidatePattern('^https?://.+/$')]
    [string]$ApiBaseUrl = 'http://localhost/polygo-api/'
)

$ErrorActionPreference = 'Stop'
$checks = 0

function Invoke-PolyGoJson([string]$Endpoint, [hashtable]$Body) {
    $uri = [Uri]::new([Uri]$ApiBaseUrl, $Endpoint).AbsoluteUri
    if ($null -eq $Body) {
        return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 15
    }
    $json = $Body | ConvertTo-Json -Compress
    return Invoke-RestMethod -Uri $uri -Method Post -ContentType 'application/json' -Body $json -TimeoutSec 15
}

function Assert-Success([string]$Name, $Response, [string]$CollectionProperty) {
    if ($null -eq $Response -or -not $Response.success) {
        throw "$Name did not return success."
    }
    if ($CollectionProperty -and $null -eq $Response.$CollectionProperty) {
        throw "$Name omitted '$CollectionProperty'."
    }
    $script:checks++
    Write-Host "[PASS] $Name" -ForegroundColor Green
}

Write-Host "PolyGo+ read-only API smoke test: $ApiBaseUrl" -ForegroundColor Cyan
Assert-Success 'Status' (Invoke-PolyGoJson 'status.php' $null) ''
Assert-Success 'Categories' (Invoke-PolyGoJson 'categories.php' @{ action = 'list' }) 'categories'
Assert-Success 'Listings' (Invoke-PolyGoJson 'listings.php' @{ action = 'list'; limit = 10; offset = 0 }) 'listings'
Assert-Success 'Campus events' (Invoke-PolyGoJson 'campus.php' @{ action = 'events' }) 'events'
$pulse = Invoke-PolyGoJson 'pulse.php' @{ action = 'list' }
Assert-Success 'Campus Pulse' $pulse 'alerts'
if ($null -eq $pulse.announcements) {
    throw "Campus Pulse omitted 'announcements'."
}

$post = @($pulse.announcements) + @($pulse.alerts) | Select-Object -First 1
if ($null -ne $post) {
    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    try {
        $protectedUri = [Uri]::new([Uri]$ApiBaseUrl, 'pulse.php').AbsoluteUri
        $payload = @{ action = 'comment'; pulse_id = $post.id; comment = 'must not be written' } |
            ConvertTo-Json -Compress
        $content = [System.Net.Http.StringContent]::new(
            $payload, [Text.Encoding]::UTF8, 'application/json')
        $response = $client.PostAsync($protectedUri, $content).GetAwaiter().GetResult()
        if ([int]$response.StatusCode -ne 401) {
            throw "Unauthenticated Pulse write returned HTTP $([int]$response.StatusCode) instead of 401."
        }
        $checks++
        Write-Host '[PASS] Unauthorized Pulse write rejected' -ForegroundColor Green
    } finally {
        $client.Dispose()
    }
}

Write-Host "Smoke test passed: $checks checks" -ForegroundColor Cyan
