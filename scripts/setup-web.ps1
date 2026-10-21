param([string]$NodeVersion = 'v24.22.1', [string]$PnpmVersion = '10.32.1')
$ErrorActionPreference = 'Stop'
if ($NodeVersion -notmatch '^v\d+\.\d+\.\d+$' -or $PnpmVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid version' }
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolDir = Join-Path $projectRoot '.local/tooling'
New-Item -ItemType Directory -Force -Path $toolDir | Out-Null
$nodeName = "node-$NodeVersion-win-x64"
$nodeDir = Join-Path $toolDir $nodeName
if (-not (Test-Path (Join-Path $nodeDir 'node.exe'))) {
    $archive = Join-Path $toolDir "$nodeName.zip"
    Invoke-WebRequest "https://nodejs.org/dist/$NodeVersion/$nodeName.zip" -OutFile $archive -UseBasicParsing
    $checks = (Invoke-WebRequest "https://nodejs.org/dist/$NodeVersion/SHASUMS256.txt" -UseBasicParsing).Content
    $expected = ($checks -split "`n" | Where-Object { $_ -match " $([regex]::Escape($nodeName)).zip$" }).Split(' ')[0]
    if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $expected) { throw 'Node checksum verification failed' }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolDir
}
$nodeVersionPath = Join-Path $projectRoot '.local/node-version.txt'
Set-Content -LiteralPath $nodeVersionPath -Value $NodeVersion -Encoding ASCII
$pnpmDir = Join-Path $toolDir 'pnpm'
if (-not (Test-Path (Join-Path $pnpmDir 'package/bin/pnpm.cjs'))) {
    $metadata = Invoke-RestMethod "https://registry.npmjs.org/pnpm/$PnpmVersion"
    $archive = Join-Path $toolDir "pnpm-$PnpmVersion.tgz"
    Invoke-WebRequest $metadata.dist.tarball -OutFile $archive -UseBasicParsing
    $sha = [Security.Cryptography.SHA512]::Create()
    $integrity = 'sha512-' + [Convert]::ToBase64String($sha.ComputeHash([IO.File]::ReadAllBytes($archive)))
    $sha.Dispose()
    if ($integrity -ne $metadata.dist.integrity) { throw 'pnpm checksum verification failed' }
    New-Item -ItemType Directory -Force -Path $pnpmDir | Out-Null
    & tar -xzf $archive -C $pnpmDir
    if ($LASTEXITCODE -ne 0) { throw 'pnpm extraction failed' }
}
Write-Output "Project runtimes ready: Node $NodeVersion, pnpm $PnpmVersion"
& "$PSScriptRoot/web.ps1" install
