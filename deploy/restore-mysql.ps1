param([Parameter(Mandatory=$true)][string]$BackupZip,[switch]$ConfirmRestore)
$ErrorActionPreference = 'Stop'
if (-not $ConfirmRestore) { throw 'Restore is destructive. Re-run with -ConfirmRestore after verifying the target database.' }
$required = 'DB_HOST','DB_PORT','DB_USERNAME','DB_PASSWORD','DB_NAME'
foreach ($name in $required) { if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Missing environment variable: $name" } }
$temp = Join-Path ([IO.Path]::GetTempPath()) ("cattle-restore-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $temp | Out-Null
try {
  Expand-Archive -LiteralPath $BackupZip -DestinationPath $temp
  $sql = Get-ChildItem -LiteralPath $temp -File -Filter '*.sql' | Select-Object -First 1
  if (-not $sql) { throw 'The archive does not contain a SQL dump.' }
  $env:MYSQL_PWD = $env:DB_PASSWORD
  Get-Content -LiteralPath $sql.FullName -Raw | mysql --host=$env:DB_HOST --port=$env:DB_PORT --user=$env:DB_USERNAME --default-character-set=utf8mb4 $env:DB_NAME
  if ($LASTEXITCODE -ne 0) { throw 'mysql restore failed' }
} finally {
  Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
