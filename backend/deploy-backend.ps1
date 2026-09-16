#Requires -Version 5.1
<#
.SYNOPSIS
    Deploy PolyGo backend PHP files from this repo to the XAMPP htdocs folder.
.DESCRIPTION
    1. PARITY CHECK  - compare each repo file against the live copy (SHA256)
    2. PHP LINT       - php -l every repo file BEFORE anything is copied
    3. BACKUP         - live file is saved as .bak-<timestamp> only when it differs
    4. COPY           - repo file replaces the live one
    Secrets (secrets.php, service-account.json, secrets/, vendor/) are NEVER touched.
.EXAMPLE
    .\deploy-backend.ps1 -DryRun            # parity report only, nothing copied
    .\deploy-backend.ps1                     # deploy the default change set
    .\deploy-backend.ps1 -All                # sync every backend .php in the repo
    .\deploy-backend.ps1 -Files config.php   # deploy a single named file
#>
param(
    [string[]]$Files = @(
        'config.php', 'google_login.php', 'otp.php', 'forgot_password.php',
        'NotificationManager.php', 'transactions.php', 'review.php', 'verify.php',
        'messages.php', 'listings.php', 'clean_seed.php'
    ),
    [string]$RepoDir = (Join-Path $PSScriptRoot 'polygo-api'),
    [string]$LiveDir = 'C:\xampp\htdocs\polygo-api',
    [string]$PhpExe = 'C:\xampp\php\php.exe',
    [switch]$All,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Get-Sha256([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    }
    return $null
}

$neverDeploy = @('secrets.php', 'service-account.json')

if (-not (Test-Path -LiteralPath $RepoDir)) {
    Write-Host "Repo dir not found: $RepoDir" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path -LiteralPath $LiveDir)) {
    Write-Host "Live dir not found: $LiveDir" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path -LiteralPath $PhpExe)) {
    Write-Host "PHP binary not found: $PhpExe" -ForegroundColor Red
    exit 1
}

if ($All) {
    $Files = Get-ChildItem -LiteralPath $RepoDir -Filter '*.php' -File |
        Select-Object -ExpandProperty Name
}

$Files = $Files |
    Where-Object { $_ -notin $neverDeploy -and (Test-Path -LiteralPath (Join-Path $RepoDir $_)) } |
    Sort-Object -Unique

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$deployed = @()
$skipped = @()
$failed = @()

Write-Host "== PolyGo backend deploy ==" -ForegroundColor Cyan
Write-Host "  repo: $RepoDir"
Write-Host "  live: $LiveDir"
Write-Host "  mode: $(if ($DryRun) { 'DRY RUN (no changes)' } else { 'live' })"
Write-Host ""

foreach ($f in $Files) {
    $repo = Join-Path $RepoDir $f
    $live = Join-Path $LiveDir $f
    $repoHash = Get-Sha256 $repo
    $liveHash = Get-Sha256 $live
    $isNew = ($null -eq $liveHash)
    $drift = $isNew -or ($repoHash -ne $liveHash)
    $state = if ($isNew) { 'NEW' } elseif ($repoHash -eq $liveHash) { 'MATCH' } else { 'DIFF' }

    $livePrefix = if ($liveHash) { $liveHash.Substring(0, 8) } else { 'absent' }
    Write-Host ("  [{0}] {1,-32} repo:{2}  live:{3}" -f $state, $f, $repoHash.Substring(0, 8), $livePrefix)

    if (-not $drift) { $skipped += $f; continue }

    $lint = & $PhpExe -l $repo 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host ("      LINT FAIL: " + ($lint -join ' ')) -ForegroundColor Red
        $failed += $f
        continue
    }

    if ($DryRun) { continue }

    if (-not $isNew) {
        Copy-Item -LiteralPath $live -Destination "$live.bak-$stamp" -Force
        Write-Host ("      backup -> {0}.bak-{1}" -f $f, $stamp) -ForegroundColor DarkGray
    }
    Copy-Item -LiteralPath $repo -Destination $live -Force
    $deployed += $f
}

Write-Host ""
Write-Host ("Deployed: {0}" -f $(if ($deployed.Count) { "[$($deployed -join ', ')]" } else { 'none' }))
Write-Host ("Skipped  (already in sync): {0} file(s)" -f $skipped.Count)
if ($failed.Count) {
    Write-Host ("FAILED   (lint): [$($failed -join ', ')]") -ForegroundColor Red
    exit 1
}

if ('clean_seed.php' -in $Files -and -not $DryRun) {
    Write-Host "REMINDER: run http://localhost/polygo-api/clean_seed.php once to refresh test data." -ForegroundColor Yellow
}