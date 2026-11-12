param([string]$PgBin = $(if ($env:PG_BIN) { $env:PG_BIN } else { 'D:\Tool\Postgres\bin' }), [int]$Port = 55432)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$localDir = Join-Path $projectRoot '.local'
$dataDir = Join-Path $localDir 'pgdata'
New-Item -ItemType Directory -Force -Path $localDir | Out-Null
if (-not (Test-Path (Join-Path $dataDir 'PG_VERSION'))) {
    & "$PgBin\initdb.exe" -D $dataDir -U postgres --encoding=UTF8 --locale=C --auth=trust
    if ($LASTEXITCODE -ne 0) { throw 'initdb failed' }
}
& "$PgBin\pg_ctl.exe" -D $dataDir status *> $null
if ($LASTEXITCODE -ne 0) {
    $pgArguments = @('-D', ('"' + $dataDir + '"'), '-l', ('"' + (Join-Path $localDir 'postgres.log') + '"'), '-o', ('"-p ' + $Port + ' -h 127.0.0.1"'), '-w', 'start')
    $starter = Start-Process -FilePath "$PgBin\pg_ctl.exe" -ArgumentList $pgArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $localDir 'pg-start.out.log') -RedirectStandardError (Join-Path $localDir 'pg-start.err.log')
    # Wait for pg_ctl only. Start-Process -Wait also waits for its long-running PostgreSQL child.
    $starter.WaitForExit()
    $starter.Dispose()
    & "$PgBin\pg_ctl.exe" -D $dataDir status *> $null
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL start failed; inspect .local/postgres.log' }
}
$roleExists = & "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U postgres -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='caiya'"
if ($roleExists -ne '1') {
    & "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'CREATE ROLE caiya LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE'
    if ($LASTEXITCODE -ne 0) { throw 'Role creation failed' }
}
foreach ($dbName in @('caiya','caiya_test')) {
    $dbExists = & "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$dbName'"
    if ($dbExists -ne '1') {
        & "$PgBin\createdb.exe" -h 127.0.0.1 -p $Port -U postgres -O caiya $dbName
        if ($LASTEXITCODE -ne 0) { throw 'Database creation failed' }
    }
}
foreach ($dbName in @('caiya','caiya_test')) {
    & "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U postgres -d $dbName -v ON_ERROR_STOP=1 -c 'CREATE EXTENSION IF NOT EXISTS pg_trgm' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'pg_trgm extension failed' }
}
Write-Output "Project PostgreSQL ready on 127.0.0.1:$Port; development only, loopback trust authentication."
