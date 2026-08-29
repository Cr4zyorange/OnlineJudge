$ErrorActionPreference = 'Stop'

$rootDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$requiredFiles = @(
    'database/ownership/table-ownership.csv',
    'database/ownership/logical-references.csv',
    'database/mysql/split-schemas.sql',
    'database/mysql/migrate-split-schemas.ps1',
    'database/mysql/verify-split-schemas.ps1',
    'database/mysql/rollback-split-schemas.sql',
    'scripts/ci/verify-data-ownership.ps1',
    'docs/开发/D6-DATA-数据所有权与Schema迁移契约.md'
)

foreach ($relativePath in $requiredFiles) {
    $artifact = Join-Path $rootDirectory $relativePath
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
        throw "missing data ownership contract artifact: $relativePath"
    }
}

& (Join-Path $rootDirectory 'scripts/ci/verify-data-ownership.ps1')
