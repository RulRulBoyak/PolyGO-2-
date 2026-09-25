param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^https://.+/$')]
    [string]$ApiBaseUrl
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$statusUrl = [Uri]::new([Uri]$ApiBaseUrl, 'status.php').AbsoluteUri

Write-Host "Checking production API: $statusUrl"
$status = Invoke-RestMethod -Uri $statusUrl -Method Get -TimeoutSec 20
if (-not $status.success) {
    throw 'Production API status check did not report success.'
}

Push-Location $projectRoot
try {
    & .\gradlew.bat "-PRELEASE_API_BASE_URL=$ApiBaseUrl" assembleRelease checkstyle testDebugUnitTest lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Release verification failed with exit code $LASTEXITCODE."
    }

    $apk = Get-Item 'app\build\outputs\apk\release\app-release.apk'
    $hash = Get-FileHash $apk.FullName -Algorithm SHA256

    $buildTools = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    $apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
    if (-not (Test-Path -LiteralPath $apksigner)) {
        throw 'Android apksigner was not found.'
    }
    & $apksigner verify --verbose $apk.FullName
    if ($LASTEXITCODE -ne 0) {
        throw 'Release APK signature verification failed.'
    }

    $certLine = & $apksigner verify --print-certs $apk.FullName |
        Select-String 'SHA-1 digest' |
        Select-Object -First 1
    $releaseCert = $certLine.ToString().Split(':', 2)[1].Trim().Replace(':', '').ToLowerInvariant()
    $googleServicesPath = Join-Path $projectRoot 'app\google-services.json'
    if (-not (Test-Path -LiteralPath $googleServicesPath)) {
        throw 'app/google-services.json is missing.'
    }
    $googleServices = Get-Content -LiteralPath $googleServicesPath -Raw | ConvertFrom-Json
    $registeredCerts = @($googleServices.client.oauth_client |
        Where-Object { $_.client_type -eq 1 } |
        ForEach-Object { $_.android_info.certificate_hash.Replace(':', '').ToLowerInvariant() })
    if ($releaseCert -notin $registeredCerts) {
        throw 'The release signing SHA-1 is not registered for Google sign-in. Add it in Firebase/Google Cloud, then download a refreshed google-services.json.'
    }

    Write-Host "Release APK: $($apk.FullName)"
    Write-Host "Size: $($apk.Length) bytes"
    Write-Host "SHA-256: $($hash.Hash)"
} finally {
    Pop-Location
}
