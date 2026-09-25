#Requires -Version 5.1
param(
    [string]$Database = 'polygo',
    [string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) '.local-backups'),
    [string]$MySqlDumpExe = 'C:\xampp\mysql\bin\mysqldump.exe',
    [string]$UploadsDirectory = 'C:\xampp\htdocs\polygo-api\uploads'
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $MySqlDumpExe)) {
    throw "mysqldump was not found: $MySqlDumpExe"
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $OutputDirectory $stamp
New-Item -ItemType Directory -Path $backup -Force | Out-Null
$sql = Join-Path $backup "$Database.sql"

Write-Host "Backing up database '$Database'..." -ForegroundColor Cyan
& $MySqlDumpExe -u root --single-transaction --routines --triggers --events `
    --default-character-set=utf8mb4 $Database | Set-Content -LiteralPath $sql -Encoding utf8
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $sql) -or (Get-Item $sql).Length -eq 0) {
    throw 'Database backup failed or produced an empty file.'
}

$archive = $null
if (Test-Path -LiteralPath $UploadsDirectory) {
    $archive = Join-Path $backup 'uploads.zip'
    Write-Host 'Backing up uploaded files...' -ForegroundColor Cyan
    Compress-Archive -Path (Join-Path $UploadsDirectory '*') -DestinationPath $archive -CompressionLevel Optimal
}

$files = Get-ChildItem -LiteralPath $backup -File | ForEach-Object {
    [ordered]@{
        file = $_.Name
        bytes = $_.Length
        sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }
}
$manifest = [ordered]@{
    created_at = (Get-Date).ToString('o')
    database = $Database
    uploads_source = $UploadsDirectory
    files = @($files)
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $backup 'manifest.json') -Encoding utf8

Write-Host "Backup complete: $backup" -ForegroundColor Green
Get-ChildItem -LiteralPath $backup -File | Select-Object Name, Length
