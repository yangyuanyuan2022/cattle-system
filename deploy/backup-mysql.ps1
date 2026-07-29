param(
  [string]$BackupRoot = "$PSScriptRoot\backups",
  [int]$RetentionDays = 30
)
$ErrorActionPreference = 'Stop'
$required = 'DB_HOST','DB_PORT','DB_USERNAME','DB_PASSWORD','DB_NAME'
foreach ($name in $required) { if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Missing environment variable: $name" } }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$day = Get-Date -Format 'yyyy-MM-dd'
$targetDir = Join-Path $BackupRoot $day
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
$sqlPath = Join-Path $targetDir "$($env:DB_NAME)-$stamp.sql"
$zipPath = "$sqlPath.zip"
$env:MYSQL_PWD = $env:DB_PASSWORD
try {
  & mysqldump --host=$env:DB_HOST --port=$env:DB_PORT --user=$env:DB_USERNAME --single-transaction --routines --events --triggers --set-gtid-purged=OFF --default-character-set=utf8mb4 $env:DB_NAME --result-file=$sqlPath
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path $sqlPath)) { throw 'mysqldump failed' }
  Compress-Archive -LiteralPath $sqlPath -DestinationPath $zipPath -CompressionLevel Optimal
  Remove-Item -LiteralPath $sqlPath -Force
  Get-ChildItem -LiteralPath $BackupRoot -Recurse -File -Filter '*.zip' | Where-Object LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) | Remove-Item -Force
  Write-Output $zipPath
} finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
