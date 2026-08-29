$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$harnessPath = Join-Path $root 'database\tests\verify-data-ownership-migration.ps1'
$ephemeralPath = Join-Path $root 'database\tests\run-ephemeral-mysql.ps1'

if (-not (Test-Path -LiteralPath $harnessPath -PathType Leaf)) {
    throw 'missing executable data ownership migration harness'
}
if (-not (Test-Path -LiteralPath $ephemeralPath -PathType Leaf)) {
    throw 'missing isolated ephemeral MySQL runner'
}

$tokens = $null
$parseErrors = $null
$null = [System.Management.Automation.Language.Parser]::ParseFile(
    $harnessPath,
    [ref]$tokens,
    [ref]$parseErrors
)
if ($parseErrors.Count -gt 0) {
    throw "migration harness has PowerShell parse errors: $($parseErrors -join '; ')"
}

$content = Get-Content -Raw $harnessPath
foreach ($requiredPath in @('fresh', 'upgrade', 'repeat', 'failure', 'rollback')) {
    if ($content -notmatch "(?i)\b$requiredPath\b") {
        throw "migration harness does not cover path: $requiredPath"
    }
}
foreach ($requiredEvidence in @('baselineSha', 'testedSha', 'exitCode', 'rawLog', 'startedAt', 'finishedAt')) {
    if ($content -notmatch [regex]::Escape($requiredEvidence)) {
        throw "migration harness does not record evidence field: $requiredEvidence"
    }
}
foreach ($requiredCheck in @('row_count', 'primary_key', 'unique_constraint', 'business_invariant')) {
    if ($content -notmatch [regex]::Escape($requiredCheck)) {
        throw "migration harness does not record integrity check: $requiredCheck"
    }
}

Write-Host 'data ownership migration harness contract passed'
