$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot/dev-db.ps1"
$credentialPath = Join-Path $projectRoot '.local/admin-credentials.json'
if (-not (Test-Path $credentialPath)) {
    $bytes = New-Object byte[] 24
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($bytes)
    $generator.Dispose()
    @{ username = 'admin'; password = [Convert]::ToBase64String($bytes) } | ConvertTo-Json | Set-Content -LiteralPath $credentialPath -Encoding UTF8
}
$credentials = Get-Content -Raw -LiteralPath $credentialPath | ConvertFrom-Json
$env:ADMIN_USERNAME = $credentials.username
$env:ADMIN_PASSWORD = $credentials.password
$env:MEDIA_DIR = Join-Path $projectRoot '.local/uploads'
Push-Location $projectRoot
try {
    $ErrorActionPreference = 'Continue'
    & mvn -f server/pom.xml "-Dmaven.repo.local=$projectRoot/.local/m2" spring-boot:run -B -ntp
    $serverExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($serverExit -ne 0) { throw 'Backend startup failed' }
} finally { Pop-Location }
