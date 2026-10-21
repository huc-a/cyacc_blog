$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    & mvn -f server/pom.xml "-Dmaven.repo.local=$projectRoot/.local/m2" test -B -ntp
    if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed' }
} finally { Pop-Location }
