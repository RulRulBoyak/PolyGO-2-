#Requires -Version 5.1
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [string]$MySqlExe = 'C:\xampp\mysql\bin\mysql.exe'
)

$ErrorActionPreference = 'Stop'
$backup = (Resolve-Path -LiteralPath $BackupDirectory).Path
$manifestPath = Join-Path $backup 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw 'Backup manifest.json is missing.'
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
foreach ($entry in $manifest.files) {
    $path = Join-Path $backup $entry.file
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Backup file is missing: $($entry.file)"
    }
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    if ($actual -ne $entry.sha256) {
        throw "Checksum mismatch: $($entry.file)"
    }
}

$sql = Get-ChildItem -LiteralPath $backup -Filter '*.sql' -File | Select-Object -First 1
if ($null -eq $sql) {
    throw 'No SQL dump was found in the backup.'
}
if (-not (Test-Path -LiteralPath $MySqlExe)) {
    throw "mysql was not found: $MySqlExe"
}

$database = 'polygo_restore_verify_' + (Get-Date -Format 'yyyyMMddHHmmss')
if ($database -notmatch '^polygo_restore_verify_[0-9]{14}$') {
    throw 'Unsafe temporary database name.'
}

try {
    & $MySqlExe -u root -e "CREATE DATABASE ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if ($LASTEXITCODE -ne 0) { throw 'Could not create restore-verification database.' }
    Get-Content -LiteralPath $sql.FullName | & $MySqlExe -u root $database
    if ($LASTEXITCODE -ne 0) { throw 'SQL restore verification failed.' }
    $tableCount = & $MySqlExe -u root -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database';"
    if ($LASTEXITCODE -ne 0 -or [int]$tableCount -lt 20) {
        throw "Restored database has an unexpected table count: $tableCount"
    }
    Write-Host "Backup verified: checksums valid; restored $tableCount tables." -ForegroundColor Green
} finally {
    & $MySqlExe -u root -e "DROP DATABASE IF EXISTS ``$database``;" | Out-Null
}
