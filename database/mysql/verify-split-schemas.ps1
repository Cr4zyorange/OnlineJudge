param(
    [string]$Mysql = 'mysql',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 3306,
    [Parameter(Mandatory = $true)][string]$User,
    [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Password,
    [string]$SourceSchema = 'onlinejudge',
    [string]$AuthSchema = 'oj_auth',
    [string]$CrsSchema = 'oj_crs',
    [string]$AssessmentSchema = 'oj_assessment',
    [string]$LearningGradeSchema = 'oj_learning_grade'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ownership = Import-Csv (Join-Path $root 'database\ownership\table-ownership.csv')
$logicalReferences = Import-Csv (Join-Path $root 'database\ownership\logical-references.csv')
$schemaByOwner = @{ AUTH=$AuthSchema; CRS=$CrsSchema; ASSESSMENT=$AssessmentSchema; LEARNING_GRADE=$LearningGradeSchema }
$ownerByTable = @{}
foreach ($entry in $ownership) { $ownerByTable[$entry.table] = $entry.owner }
$failures = [System.Collections.Generic.List[string]]::new()
$checkCounts = [ordered]@{ row_count=0; row_digest=0; primary_key=0; unique_constraint=0; foreign_key=0; business_invariant=0 }

function Invoke-Mysql([string]$Sql) {
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        $output = & $Mysql --protocol=TCP --host=$HostName --port=$Port --user=$User --batch --skip-column-names --raw -e $Sql 2>&1
        if ($LASTEXITCODE -ne 0) { throw "mysql failed: $($output -join [Environment]::NewLine)" }
        return @($output)
    } finally { $env:MYSQL_PWD = $previousPassword }
}

$countQueries = foreach ($entry in $ownership) {
    $targetSchema = $schemaByOwner[$entry.owner]
    "SELECT '$($entry.table)',(SELECT COUNT(*) FROM ``$SourceSchema``.``$($entry.table)``),(SELECT COUNT(*) FROM ``$targetSchema``.``$($entry.table)``)"
}
foreach ($row in (Invoke-Mysql ($countQueries -join "`nUNION ALL`n"))) {
    $table, $sourceCount, $targetCount = $row -split "`t"
    $checkCounts.row_count++
    if ($sourceCount -ne $targetCount) { $failures.Add("row_count mismatch: $table source=$sourceCount target=$targetCount") }
}

$checksumTargets = [System.Collections.Generic.List[string]]::new()
foreach ($entry in $ownership) {
    $checksumTargets.Add("``$SourceSchema``.``$($entry.table)``")
    $checksumTargets.Add("``$($schemaByOwner[$entry.owner])``.``$($entry.table)``")
}
$checksums = @{}
foreach ($row in (Invoke-Mysql ("CHECKSUM TABLE " + ($checksumTargets -join ',') + ' EXTENDED;'))) {
    $qualifiedTable, $checksum = $row -split "`t"
    $checksums[$qualifiedTable] = $checksum
}
foreach ($entry in $ownership) {
    $sourceName = "$SourceSchema.$($entry.table)"
    $targetName = "$($schemaByOwner[$entry.owner]).$($entry.table)"
    $checkCounts.row_digest++
    if ($checksums[$sourceName] -ne $checksums[$targetName]) { $failures.Add("row_digest mismatch: $($entry.table)") }
}

$targetSchemasSql = ($schemaByOwner.Values | ForEach-Object { "'$_'" }) -join ','
$pkSql = "SELECT TABLE_SCHEMA,TABLE_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA IN ('{0}',{1}) AND CONSTRAINT_NAME='PRIMARY' GROUP BY TABLE_SCHEMA,TABLE_NAME;" -f $SourceSchema,$targetSchemasSql
$pkMetadata = @{}
foreach ($row in (Invoke-Mysql $pkSql)) { $schema, $table, $columns = $row -split "`t"; $pkMetadata["$schema.$table"] = $columns }
foreach ($entry in $ownership) {
    $checkCounts.primary_key++
    if ($pkMetadata["$SourceSchema.$($entry.table)"] -ne $pkMetadata["$($schemaByOwner[$entry.owner]).$($entry.table)"]) {
        $failures.Add("primary_key mismatch: $($entry.table)")
        continue
    }
    $keyColumns = @($entry.primary_key -split '\+')
    $quotedKeyColumns = ($keyColumns | ForEach-Object { "``$_``" }) -join ','
    $sourceKeys = @(Invoke-Mysql "SELECT $quotedKeyColumns FROM ``$SourceSchema``.``$($entry.table)`` ORDER BY $quotedKeyColumns;")
    $targetKeys = @(Invoke-Mysql "SELECT $quotedKeyColumns FROM ``$($schemaByOwner[$entry.owner])``.``$($entry.table)`` ORDER BY $quotedKeyColumns;")
    if (($sourceKeys -join "`n") -cne ($targetKeys -join "`n")) {
        $failures.Add("primary_key set mismatch: $($entry.table)")
    }
}

$uniqueSql = "SELECT TABLE_SCHEMA,TABLE_NAME,INDEX_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA IN ('{0}',{1}) AND NON_UNIQUE=0 GROUP BY TABLE_SCHEMA,TABLE_NAME,INDEX_NAME ORDER BY TABLE_NAME,INDEX_NAME;" -f $SourceSchema,$targetSchemasSql
$uniqueMetadata = @{}
foreach ($row in (Invoke-Mysql $uniqueSql)) {
    $schema, $table, $index, $columns = $row -split "`t"
    $key = "$schema.$table"; if (-not $uniqueMetadata[$key]) { $uniqueMetadata[$key] = [System.Collections.Generic.List[string]]::new() }
    $uniqueMetadata[$key].Add("$index=$columns")
}
foreach ($entry in $ownership) {
    $checkCounts.unique_constraint++
    $source = @($uniqueMetadata["$SourceSchema.$($entry.table)"]) -join ';'
    $target = @($uniqueMetadata["$($schemaByOwner[$entry.owner]).$($entry.table)"]) -join ';'
    if ($source -ne $target) { $failures.Add("unique_constraint mismatch: $($entry.table)") }
}

$foreignKeySql = "SELECT k.CONSTRAINT_SCHEMA,k.TABLE_NAME,k.CONSTRAINT_NAME,k.REFERENCED_TABLE_NAME,GROUP_CONCAT(CONCAT(k.COLUMN_NAME,'=',k.REFERENCED_COLUMN_NAME) ORDER BY k.ORDINAL_POSITION),r.UPDATE_RULE,r.DELETE_RULE FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA IN ('{0}',{1}) AND k.REFERENCED_TABLE_NAME IS NOT NULL GROUP BY k.CONSTRAINT_SCHEMA,k.TABLE_NAME,k.CONSTRAINT_NAME,k.REFERENCED_TABLE_NAME,r.UPDATE_RULE,r.DELETE_RULE;" -f $SourceSchema,$targetSchemasSql
$foreignKeyMetadata = @{}
foreach ($row in (Invoke-Mysql $foreignKeySql)) {
    $schema, $table, $name, $targetTable, $columns, $updateRule, $deleteRule = $row -split "`t"
    $foreignKeyMetadata["$schema.$table.$name"] = "$targetTable|$columns|$updateRule|$deleteRule"
}
foreach ($key in @($foreignKeyMetadata.Keys | Where-Object { $_.StartsWith("$SourceSchema.") })) {
    $parts = $key.Split('.',3); $table = $parts[1]; $name = $parts[2]
    $value = $foreignKeyMetadata[$key]; $referencedTable = $value.Split('|')[0]
    if ($ownerByTable[$table] -ne $ownerByTable[$referencedTable]) { $failures.Add("cross-owner foreign_key remains in source: $table -> $referencedTable"); continue }
    $targetKey = "$($schemaByOwner[$ownerByTable[$table]]).$table.$name"
    $checkCounts.foreign_key++
    if ($foreignKeyMetadata[$targetKey] -ne $value) { $failures.Add("foreign_key mismatch: $table.$name") }
}

$invariantQueries = [System.Collections.Generic.List[string]]::new()
foreach ($reference in $logicalReferences) {
    if (-not $schemaByOwner.ContainsKey($reference.owner) -or -not $schemaByOwner.ContainsKey($reference.target_owner) -or $reference.target_table -eq 'logical source') { continue }
    $sourceSchema = $schemaByOwner[$reference.owner]; $targetSchema = $schemaByOwner[$reference.target_owner]
    $label = "$($reference.table).$($reference.column)"
    $invariantQueries.Add("SELECT '$label',COUNT(*) FROM ``$sourceSchema``.``$($reference.table)`` s LEFT JOIN ``$targetSchema``.``$($reference.target_table)`` t ON t.``$($reference.target_column)``=s.``$($reference.column)`` WHERE s.``$($reference.column)`` IS NOT NULL AND t.``$($reference.target_column)`` IS NULL")
}
foreach ($row in (Invoke-Mysql ($invariantQueries -join "`nUNION ALL`n"))) {
    $label, $orphanCount = $row -split "`t"; $checkCounts.business_invariant++
    if ([long]$orphanCount -ne 0) { $failures.Add("business_invariant orphan: $label count=$orphanCount") }
}

if ($failures.Count -gt 0) { $failures | ForEach-Object { Write-Error $_ -ErrorAction Continue }; exit 1 }
[pscustomobject]@{ result='split_schema_contract_ok'; tables=$ownership.Count; checks=$checkCounts } | ConvertTo-Json -Compress
