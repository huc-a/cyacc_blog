param([ValidateSet('install','dev','preview','build','typecheck','test','test:e2e')][string]$Action = 'dev')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$versionFile = Join-Path $projectRoot '.local/node-version.txt'
if (Test-Path $versionFile) {
    $taskNodeVersion = (Get-Content $versionFile -Raw).Trim()
    $taskNodeBin = Join-Path $projectRoot ".local/tooling/node-$taskNodeVersion-win-x64"
    $env:PATH = "$taskNodeBin;$env:PATH"
}
Push-Location (Join-Path $projectRoot 'web')
try {
    $ErrorActionPreference = 'Continue'
    if ($Action -eq 'install') { & node (Join-Path $projectRoot '.local/tooling/pnpm/package/bin/pnpm.cjs') install --store-dir (Join-Path $projectRoot '.local/pnpm-store') }
    else { & npm.cmd run $Action }
    $webExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($webExit -ne 0) { throw "Web $Action failed" }
} finally { Pop-Location }
