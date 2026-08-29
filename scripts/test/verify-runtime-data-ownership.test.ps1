$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
& (Join-Path $root 'scripts\ci\verify-data-ownership.ps1') -RuntimeAudit
if (-not $?) { throw 'runtime data ownership audit failed' }
