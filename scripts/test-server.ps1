$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not $env:TEST_DB_URL) { & "$PSScriptRoot/dev-db.ps1" }
    $ErrorActionPreference = 'Continue'
    & mvn -f server/pom.xml "-Dmaven.repo.local=$projectRoot/.local/m2" test -B -ntp
    $testExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($testExit -ne 0) { throw 'Backend verification failed' }
} finally { Pop-Location }
