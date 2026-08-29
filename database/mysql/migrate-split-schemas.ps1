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
    [string]$LearningGradeSchema = 'oj_learning_grade',
    [switch]$ProvisionRoles,
    [string]$RolePrefix = 'oj'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ownership = Import-Csv (Join-Path $root 'database\ownership\table-ownership.csv')
$schemaByOwner = @{
    AUTH = $AuthSchema
    CRS = $CrsSchema
    ASSESSMENT = $AssessmentSchema
    LEARNING_GRADE = $LearningGradeSchema
}

function Assert-Identifier([string]$Value, [string]$Label) {
    if ($Value -notmatch '^[A-Za-z0-9_]{1,64}$') { throw "unsafe $Label identifier: $Value" }
}
foreach ($identifier in @($SourceSchema, $AuthSchema, $CrsSchema, $AssessmentSchema, $LearningGradeSchema, $RolePrefix)) {
    Assert-Identifier $identifier 'database or role'
}

function Invoke-Mysql([string]$Sql) {
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        $output = & $Mysql --protocol=TCP --host=$HostName --port=$Port --user=$User --batch --skip-column-names --raw -e $Sql 2>&1
        if ($LASTEXITCODE -ne 0) { throw "mysql failed: $($output -join [Environment]::NewLine)" }
        return @($output)
    } finally {
        $env:MYSQL_PWD = $previousPassword
    }
}

foreach ($schemaName in $schemaByOwner.Values) {
    Invoke-Mysql "CREATE DATABASE IF NOT EXISTS ``$schemaName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" | Out-Null
}

if ($ProvisionRoles) {
    foreach ($owner in $schemaByOwner.Keys) {
        $roleName = "${RolePrefix}_$($owner.ToLowerInvariant())_role"
        Assert-Identifier $roleName 'role'
        if ($roleName.Length -gt 32) { throw "role name exceeds MySQL 32-character limit: $roleName" }
        $schemaName = $schemaByOwner[$owner]
        Invoke-Mysql "CREATE ROLE IF NOT EXISTS '$roleName'; GRANT SELECT, INSERT, UPDATE, DELETE ON ``$schemaName``.* TO '$roleName';" | Out-Null
    }
}

$copyStatements = [System.Collections.Generic.List[string]]::new()
$countQueries = [System.Collections.Generic.List[string]]::new()
foreach ($entry in $ownership) {
    $targetSchema = $schemaByOwner[$entry.owner]
    $copyStatements.Add("CREATE TABLE IF NOT EXISTS ``$targetSchema``.``$($entry.table)`` LIKE ``$SourceSchema``.``$($entry.table)``")
    $copyStatements.Add("INSERT INTO ``$targetSchema``.``$($entry.table)`` SELECT s.* FROM ``$SourceSchema``.``$($entry.table)`` s WHERE NOT EXISTS (SELECT 1 FROM ``$targetSchema``.``$($entry.table)`` LIMIT 1)")
    $countQueries.Add("SELECT '$($entry.table)', (SELECT COUNT(*) FROM ``$SourceSchema``.``$($entry.table)``), (SELECT COUNT(*) FROM ``$targetSchema``.``$($entry.table)``)")
}
Invoke-Mysql (($copyStatements -join ";`n") + ';') | Out-Null
$countRows = Invoke-Mysql ($countQueries -join "`nUNION ALL`n")
foreach ($countRow in $countRows) {
    $table, $sourceCount, $targetCount = $countRow -split "`t"
    if ($sourceCount -ne $targetCount) {
        throw "partial or drifted target table ${table}: source=$sourceCount target=$targetCount"
    }
}

# CREATE TABLE LIKE omits foreign keys. Restore only owner-internal references.
$ownerByTable = @{}
foreach ($entry in $ownership) { $ownerByTable[$entry.table] = $entry.owner }
$foreignKeys = Invoke-Mysql @"
SELECT kcu.TABLE_NAME, kcu.CONSTRAINT_NAME,
       GROUP_CONCAT(kcu.COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION),
       kcu.REFERENCED_TABLE_NAME,
       GROUP_CONCAT(kcu.REFERENCED_COLUMN_NAME ORDER BY kcu.ORDINAL_POSITION),
       rc.UPDATE_RULE, rc.DELETE_RULE
  FROM information_schema.KEY_COLUMN_USAGE kcu
  JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
    ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
   AND rc.TABLE_NAME = kcu.TABLE_NAME
   AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
 WHERE kcu.CONSTRAINT_SCHEMA = '$SourceSchema'
   AND kcu.REFERENCED_TABLE_NAME IS NOT NULL
 GROUP BY kcu.TABLE_NAME, kcu.CONSTRAINT_NAME, kcu.REFERENCED_TABLE_NAME,
          rc.UPDATE_RULE, rc.DELETE_RULE;
"@
$foreignKeyStatements = [System.Collections.Generic.List[string]]::new()
foreach ($foreignKey in $foreignKeys) {
    $parts = $foreignKey -split "`t"
    if ($parts.Count -ne 7) { throw "unexpected foreign key metadata: $foreignKey" }
    $table, $constraint, $columns, $referencedTable, $referencedColumns, $updateRule, $deleteRule = $parts
    if ($ownerByTable[$table] -ne $ownerByTable[$referencedTable]) { continue }
    $targetSchema = $schemaByOwner[$ownerByTable[$table]]
    $quotedColumns = (($columns -split ',') | ForEach-Object { "``$_``" }) -join ','
    $quotedReferencedColumns = (($referencedColumns -split ',') | ForEach-Object { "``$_``" }) -join ','
    $foreignKeyStatements.Add("SET @fk_exists=(SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='$targetSchema' AND TABLE_NAME='$table' AND CONSTRAINT_NAME='$constraint')")
    $alter = "ALTER TABLE ``$targetSchema``.``$table`` ADD CONSTRAINT ``$constraint`` FOREIGN KEY ($quotedColumns) REFERENCES ``$targetSchema``.``$referencedTable`` ($quotedReferencedColumns) ON UPDATE $updateRule ON DELETE $deleteRule"
    $foreignKeyStatements.Add("SET @fk_sql=IF(@fk_exists=0,'$($alter.Replace("'", "''"))','SELECT 1')")
    $foreignKeyStatements.Add('PREPARE fk_stmt FROM @fk_sql')
    $foreignKeyStatements.Add('EXECUTE fk_stmt')
    $foreignKeyStatements.Add('DEALLOCATE PREPARE fk_stmt')
}
if ($foreignKeyStatements.Count -gt 0) { Invoke-Mysql (($foreignKeyStatements -join ";`n") + ';') | Out-Null }

$historyStatements = [System.Collections.Generic.List[string]]::new()
foreach ($owner in $schemaByOwner.Keys) {
    $targetSchema = $schemaByOwner[$owner]
    $historyStatements.Add("CREATE TABLE IF NOT EXISTS ``$targetSchema``.schema_migrations LIKE ``$SourceSchema``.schema_migrations")
    $versions = @($ownership | Where-Object owner -eq $owner | Select-Object -ExpandProperty migration -Unique)
    $quotedVersions = ($versions | ForEach-Object { "'$($_.Replace("'", "''"))'" }) -join ','
    $historyStatements.Add("INSERT IGNORE INTO ``$targetSchema``.schema_migrations SELECT * FROM ``$SourceSchema``.schema_migrations WHERE version IN ($quotedVersions)")
}
Invoke-Mysql (($historyStatements -join ";`n") + ';') | Out-Null

Write-Host "split migration complete: source=$SourceSchema tables=$($ownership.Count)"
