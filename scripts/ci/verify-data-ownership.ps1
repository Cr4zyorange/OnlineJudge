param([switch]$RuntimeAudit)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ownershipPath = Join-Path $root 'database\ownership\table-ownership.csv'
$manifestPath = Join-Path $root 'database\migrations\manifest.txt'
$schemaPath = Join-Path $root 'database\mysql\compose-schema.sql'
$ownership = Import-Csv $ownershipPath
$errors = [System.Collections.Generic.List[string]]::new()

$duplicates = $ownership | Group-Object table | Where-Object Count -ne 1
foreach ($duplicate in $duplicates) { $errors.Add("table has non-unique owner: $($duplicate.Name)") }

$schema = Get-Content -Raw $schemaPath
$schemaTables = [regex]::Matches($schema, '(?im)^CREATE TABLE(?: IF NOT EXISTS)?\s+`?([a-zA-Z0-9_]+)`?\s*\(') |
    ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne 'schema_migrations' }
$ownedTables = @($ownership.table)
foreach ($table in $schemaTables) {
    if ($table -notin $ownedTables) { $errors.Add("schema table has no owner: $table") }
}
foreach ($table in $ownedTables) {
    if ($table -notin $schemaTables) { $errors.Add("ownership table absent from schema: $table") }
}

foreach ($entry in $ownership) {
    $tablePattern = "(?is)CREATE TABLE(?: IF NOT EXISTS)?\s+``?$([regex]::Escape($entry.table))``?\s*\((?<body>.*?)\)\s*;"
    $tableMatch = [regex]::Match($schema, $tablePattern)
    if (-not $tableMatch.Success) { continue }
    $body = $tableMatch.Groups['body'].Value
    $explicitPrimaryKey = [regex]::Match($body, '(?is)PRIMARY\s+KEY\s*\((?<columns>[^)]+)\)')
    if ($explicitPrimaryKey.Success) {
        $actualPrimaryKey = (($explicitPrimaryKey.Groups['columns'].Value -split ',') |
            ForEach-Object { ($_ -replace '[`\s]', '') }) -join '+'
    } else {
        $inlinePrimaryKey = [regex]::Match($body, '(?im)^\s*`?(?<column>[A-Za-z0-9_]+)`?\s+[^\r\n,]*\bPRIMARY\s+KEY\b')
        $actualPrimaryKey = if ($inlinePrimaryKey.Success) { $inlinePrimaryKey.Groups['column'].Value } else { '' }
    }
    if (-not $actualPrimaryKey) {
        $errors.Add("schema table has no detectable primary key: $($entry.table)")
    } elseif ($actualPrimaryKey -ne $entry.primary_key) {
        $errors.Add("primary key mismatch: $($entry.table) manifest=$($entry.primary_key) schema=$actualPrimaryKey")
    }
}

$manifest = Get-Content $manifestPath | Where-Object { $_ -and -not $_.StartsWith('#') }
foreach ($entry in $ownership) {
    if ($entry.migration -notin $manifest) { $errors.Add("migration not in manifest: $($entry.table) -> $($entry.migration)") }
}

$ownerByTable = @{}
foreach ($entry in $ownership) { $ownerByTable[$entry.table] = $entry.owner }
$fkPattern = '(?is)CREATE TABLE(?: IF NOT EXISTS)?\s+`?(?<table>[a-zA-Z0-9_]+)`?.*?(?=CREATE TABLE|\z)'
foreach ($tableBlock in [regex]::Matches($schema, $fkPattern)) {
    $from = $tableBlock.Groups['table'].Value
    foreach ($reference in [regex]::Matches($tableBlock.Value, '(?i)REFERENCES\s+`?(?<target>[a-zA-Z0-9_]+)`?')) {
        $target = $reference.Groups['target'].Value
        if ($ownerByTable.ContainsKey($from) -and $ownerByTable.ContainsKey($target) -and $ownerByTable[$from] -ne $ownerByTable[$target]) {
            $errors.Add("cross-owner foreign key: $from -> $target")
        }
    }
}

if ($RuntimeAudit) {
    $moduleOwners = @{ auth = 'AUTH'; crs = 'CRS'; lab = 'ASSESSMENT'; hwk = 'ASSESSMENT'; lrn = 'LEARNING_GRADE'; grd = 'LEARNING_GRADE' }
    $javaRoot = Join-Path $root 'backend\src\main\java\com\onlinejudge'
    Get-ChildItem -LiteralPath $javaRoot -Filter '*.java' -Recurse | ForEach-Object {
        $relative = $_.FullName.Substring($javaRoot.Length + 1)
        $module = $relative.Split([IO.Path]::DirectorySeparatorChar)[0]
        $moduleOwner = $moduleOwners[$module]
        $content = Get-Content -Raw $_.FullName
        foreach ($table in $ownedTables) {
            $sqlPattern = "(?i)\b(FROM|JOIN|INTO|UPDATE|DELETE\s+FROM)\s+``?$([regex]::Escape($table))\b"
            if ($content -match $sqlPattern -and $moduleOwner -ne $ownerByTable[$table]) {
                $errors.Add("cross-owner SQL: $relative accesses $table owned by $($ownerByTable[$table])")
            }
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | Sort-Object -Unique | ForEach-Object { Write-Error $_ -ErrorAction Continue }
    exit 1
}
Write-Host "data ownership contract passed: $($ownership.Count) tables, one owner each, no cross-owner foreign keys"
