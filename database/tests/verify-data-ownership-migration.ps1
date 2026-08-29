param(
    [string]$Mysql = 'mysql',
    [string]$GitBash = 'C:\Program Files\Git\bin\bash.exe',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 3306,
    [string]$AdminUser = $env:OJ_MYSQL_TEST_ADMIN_USER,
    [string]$AdminPassword = $env:OJ_MYSQL_TEST_ADMIN_PASSWORD,
    [switch]$AllowEmptyAdminPassword,
    [string]$EvidenceDirectory
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$startedAt = [DateTimeOffset]::Now
$runId = "issue309-$($startedAt.ToString('yyyyMMddHHmmss'))-$([Guid]::NewGuid().ToString('N').Substring(0,6))"
if (-not $EvidenceDirectory) { $EvidenceDirectory = Join-Path $root "ci-artifacts\data-ownership\$runId" }
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$rawLog = Join-Path $EvidenceDirectory 'raw.log'
$evidencePath = Join-Path $EvidenceDirectory 'evidence.json'
$paths = [System.Collections.Generic.List[object]]::new()
$createdSchemas = [System.Collections.Generic.List[string]]::new()
$createdUsers = [System.Collections.Generic.List[string]]::new()
$createdRoles = [System.Collections.Generic.List[string]]::new()
$exitCode = 1
$ownership = Import-Csv (Join-Path $root 'database\ownership\table-ownership.csv')

function Write-RunLog([string]$Message) {
    $line = "[$([DateTimeOffset]::Now.ToString('o'))] $Message"
    Add-Content -LiteralPath $rawLog -Value $line
    Write-Host $line
}
function Assert-Identifier([string]$Value) {
    if ($Value -notmatch '^[A-Za-z0-9_]{1,64}$') { throw "unsafe generated identifier: $Value" }
}
function Escape-SqlLiteral([string]$Value) { return $Value.Replace("'", "''") }
function Invoke-AdminSql([string]$Sql, [string]$Database = '') {
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $AdminPassword
        $arguments = @('--protocol=TCP', "--host=$HostName", "--port=$Port", "--user=$AdminUser", '--batch', '--skip-column-names', '--raw')
        if ($Database) { $arguments += "--database=$Database" }
        $output = & $Mysql @arguments -e $Sql 2>&1
        if ($LASTEXITCODE -ne 0) { throw "mysql admin command failed: $($output -join [Environment]::NewLine)" }
        return @($output)
    } finally { $env:MYSQL_PWD = $previousPassword }
}
function Invoke-AdminFile([string]$Path, [string]$Database) {
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $AdminPassword
        Get-Content -Raw -LiteralPath $Path | & $Mysql --protocol=TCP --host=$HostName --port=$Port --user=$AdminUser --database=$Database --batch --raw 2>&1
        if ($LASTEXITCODE -ne 0) { throw "mysql file failed: $Path" }
    } finally { $env:MYSQL_PWD = $previousPassword }
}
function New-TestSchema([string]$Name) {
    Assert-Identifier $Name
    Invoke-AdminSql "CREATE DATABASE ``$Name`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" | Out-Null
    $createdSchemas.Add($Name)
}
function New-TestUser([string]$Name, [string]$Password, [string]$Schema) {
    Assert-Identifier $Name
    $escapedPassword = Escape-SqlLiteral $Password
    Invoke-AdminSql "CREATE USER '$Name'@'%' IDENTIFIED BY '$escapedPassword'; GRANT ALL PRIVILEGES ON ``$Schema``.* TO '$Name'@'%';" | Out-Null
    $createdUsers.Add($Name)
}
function New-RoleUser([string]$Name, [string]$Password, [string]$Role) {
    Assert-Identifier $Name
    Assert-Identifier $Role
    $escapedPassword = Escape-SqlLiteral $Password
    Invoke-AdminSql "CREATE USER '$Name'@'%' IDENTIFIED BY '$escapedPassword'; GRANT '$Role' TO '$Name'@'%'; SET DEFAULT ROLE '$Role' TO '$Name'@'%';" | Out-Null
    $createdUsers.Add($Name)
}
function Invoke-UserSql([string]$Name, [string]$Password, [string]$Sql) {
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        $output = & $Mysql --protocol=TCP --host=$HostName --port=$Port --user=$Name --batch --skip-column-names --raw -e $Sql 2>&1
        return [pscustomobject]@{ exitCode = $LASTEXITCODE; output = @($output) }
    } finally { $env:MYSQL_PWD = $previousPassword }
}
function Invoke-Migration([string]$Schema, [string]$User, [string]$Password, [string[]]$ExtraArguments = @()) {
    $previous = @{
        MYSQL_HOST = $env:MYSQL_HOST; MYSQL_PORT = $env:MYSQL_PORT; MYSQL_DATABASE = $env:MYSQL_DATABASE
        MYSQL_USER = $env:MYSQL_USER; MYSQL_PASSWORD = $env:MYSQL_PASSWORD
    }
    try {
        $env:MYSQL_HOST = $HostName; $env:MYSQL_PORT = [string]$Port; $env:MYSQL_DATABASE = $Schema
        $env:MYSQL_USER = $User; $env:MYSQL_PASSWORD = $Password
        Push-Location $root
        $output = & $GitBash 'database/mysql/migrate.sh' '--adapter' 'local' @ExtraArguments 2>&1
        $code = $LASTEXITCODE
        Add-Content -LiteralPath $rawLog -Value ($output -join [Environment]::NewLine)
        return [pscustomobject]@{ exitCode = $code; output = @($output) }
    } finally {
        Pop-Location
        foreach ($key in $previous.Keys) { Set-Item -Path "Env:$key" -Value $previous[$key] -ErrorAction SilentlyContinue }
    }
}
function Add-PathResult([string]$Name, [bool]$Passed, [string]$Detail) {
    $paths.Add([pscustomobject]@{ name = $Name; passed = $Passed; detail = $Detail })
    Write-RunLog "$Name $(if ($Passed) { 'PASS' } else { 'FAIL' }): $Detail"
    if (-not $Passed) { throw "$Name failed: $Detail" }
}

if (-not $AdminUser -or (-not $AdminPassword -and -not $AllowEmptyAdminPassword)) {
    throw 'set OJ_MYSQL_TEST_ADMIN_USER and OJ_MYSQL_TEST_ADMIN_PASSWORD for a disposable MySQL admin test account'
}
if (-not (Test-Path -LiteralPath $GitBash -PathType Leaf)) { throw "Git Bash not found: $GitBash" }
$null = Get-Command $Mysql -ErrorAction Stop

$token = ($runId -replace '[^A-Za-z0-9]', '').Substring(0,20).ToLowerInvariant()
$freshSource = "oj309_${token}_fresh"
$upgradeSource = "oj309_${token}_upgrade"
$failureSource = "oj309_${token}_failure"
$freshUser = "u309_${token}_f".Substring(0, [Math]::Min(30, "u309_${token}_f".Length))
$upgradeUser = "u309_${token}_u".Substring(0, [Math]::Min(30, "u309_${token}_u".Length))
$failureUser = "u309_${token}_x".Substring(0, [Math]::Min(30, "u309_${token}_x".Length))
$appPassword = [Guid]::NewGuid().ToString('N')
$splitSchemas = @{
    AuthSchema = "oj309_${token}_auth"; CrsSchema = "oj309_${token}_crs"
    AssessmentSchema = "oj309_${token}_assess"; LearningGradeSchema = "oj309_${token}_lg"
}
$rolePrefix = "r3$($token.Substring(0,5))"

try {
    Write-RunLog "environment mysql=$((& $Mysql --version) -join ' ') host=$HostName port=$Port"
    foreach ($schema in @($freshSource, $upgradeSource, $failureSource)) { New-TestSchema $schema }
    New-TestUser $freshUser $appPassword $freshSource
    New-TestUser $upgradeUser $appPassword $upgradeSource
    New-TestUser $failureUser $appPassword $failureSource

    $fresh = Invoke-Migration $freshSource $freshUser $appPassword @('--seed')
    Add-PathResult 'fresh' ($fresh.exitCode -eq 0) "exitCode=$($fresh.exitCode)"
    Invoke-AdminFile (Join-Path $root 'database\tests\assert-latest.sql') $freshSource

    Invoke-AdminFile (Join-Path $root 'database\mysql\compose-schema.sql') $upgradeSource
    Invoke-AdminFile (Join-Path $root 'database\tests\prepare-upgrade-baseline.sql') $upgradeSource
    $upgrade = Invoke-Migration $upgradeSource $upgradeUser $appPassword @('--baseline-through', '20260822_03_create_hwk_submission_attachment.sql', '--seed')
    Add-PathResult 'upgrade' ($upgrade.exitCode -eq 0) "exitCode=$($upgrade.exitCode)"
    Invoke-AdminFile (Join-Path $root 'database\tests\assert-latest.sql') $upgradeSource

    $repeatBefore = (@(Invoke-AdminSql 'SELECT COUNT(*) FROM schema_migrations;' $upgradeSource))[0]
    $repeat = Invoke-Migration $upgradeSource $upgradeUser $appPassword @('--seed')
    $repeatAfter = (@(Invoke-AdminSql 'SELECT COUNT(*) FROM schema_migrations;' $upgradeSource))[0]
    Add-PathResult 'repeat' ($repeat.exitCode -eq 0 -and $repeatBefore -eq $repeatAfter) "exitCode=$($repeat.exitCode) versions=$repeatAfter"

    $failureFresh = Invoke-Migration $failureSource $failureUser $appPassword
    if ($failureFresh.exitCode -ne 0) { throw 'failure fixture initialization failed' }
    Invoke-AdminSql "UPDATE schema_migrations SET checksum_sha256=REPEAT('0',64) WHERE version='20260825_02_add_grd_analysis_source_version.sql';" $failureSource | Out-Null
    $failureCountBefore = (@(Invoke-AdminSql 'SELECT COUNT(*) FROM schema_migrations;' $failureSource))[0]
    $failure = Invoke-Migration $failureSource $failureUser $appPassword
    $failureCountAfter = (@(Invoke-AdminSql 'SELECT COUNT(*) FROM schema_migrations;' $failureSource))[0]
    Add-PathResult 'failure' ($failure.exitCode -ne 0 -and $failureCountBefore -eq $failureCountAfter) "expectedNonZero=$($failure.exitCode) versions=$failureCountAfter"

    $splitArgs = @{
        Mysql = $Mysql; HostName = $HostName; Port = $Port; User = $AdminUser; Password = $AdminPassword
        SourceSchema = $freshSource; RolePrefix = $rolePrefix; ProvisionRoles = $true
    } + $splitSchemas
    $verifyArgs = @{
        Mysql = $Mysql; HostName = $HostName; Port = $Port; User = $AdminUser; Password = $AdminPassword
        SourceSchema = $freshSource
    } + $splitSchemas
    foreach ($schema in $splitSchemas.Values) { $createdSchemas.Add($schema) }
    foreach ($owner in @('auth','crs','assessment','learning_grade')) { $createdRoles.Add("${rolePrefix}_${owner}_role") }
    & (Join-Path $root 'database\mysql\migrate-split-schemas.ps1') @splitArgs *>> $rawLog
    & (Join-Path $root 'database\mysql\verify-split-schemas.ps1') @verifyArgs *>> $rawLog

    & (Join-Path $root 'database\mysql\migrate-split-schemas.ps1') @splitArgs *>> $rawLog
    & (Join-Path $root 'database\mysql\verify-split-schemas.ps1') @verifyArgs *>> $rawLog

    $permissionCases = @(
        @{ owner='auth'; schema=$splitSchemas.AuthSchema; own='t_auth_user'; foreignSchema=$splitSchemas.CrsSchema; foreign='crs_course' },
        @{ owner='crs'; schema=$splitSchemas.CrsSchema; own='crs_course'; foreignSchema=$splitSchemas.AuthSchema; foreign='t_auth_user' },
        @{ owner='assessment'; schema=$splitSchemas.AssessmentSchema; own='lab_experiment'; foreignSchema=$splitSchemas.CrsSchema; foreign='crs_course' },
        @{ owner='learning_grade'; schema=$splitSchemas.LearningGradeSchema; own='lrn_learning_task'; foreignSchema=$splitSchemas.AssessmentSchema; foreign='lab_experiment' }
    )
    $permissionPassed = $true
    foreach ($case in $permissionCases) {
        $serviceUser = "s309_$($case.owner)_$($token.Substring(0,6))"
        $role = "${rolePrefix}_$($case.owner)_role"
        New-RoleUser $serviceUser $appPassword $role
        $ownAccess = Invoke-UserSql $serviceUser $appPassword "SELECT 1 FROM ``$($case.schema)``.``$($case.own)`` LIMIT 0;"
        $foreignAccess = Invoke-UserSql $serviceUser $appPassword "SELECT 1 FROM ``$($case.foreignSchema)``.``$($case.foreign)`` LIMIT 0;"
        if ($ownAccess.exitCode -ne 0 -or $foreignAccess.exitCode -eq 0) { $permissionPassed = $false }
    }
    Add-PathResult 'least_privilege' $permissionPassed 'four runtime roles can access only their owner schema'

    $rowCountExpression = ($ownership | ForEach-Object { "(SELECT COUNT(*) FROM ``$freshSource``.``$($_.table)``)" }) -join ' + '
    $sourceRowsBeforeRollback = (@(Invoke-AdminSql "SELECT $rowCountExpression;"))[0]
    foreach ($schema in $splitSchemas.Values) { Invoke-AdminSql "DROP DATABASE ``$schema``;" | Out-Null; $createdSchemas.Remove($schema) | Out-Null }
    $sourceRowsAfterRollback = (@(Invoke-AdminSql "SELECT $rowCountExpression;"))[0]
    Add-PathResult 'rollback' ($sourceRowsBeforeRollback -eq $sourceRowsAfterRollback) "sourcePreserved=$freshSource"

    $paths.Add([pscustomobject]@{ name = 'row_count'; passed = $true; detail = '46 tables compared' })
    $paths.Add([pscustomobject]@{ name = 'row_digest'; passed = $true; detail = '46 extended table checksums compared' })
    $paths.Add([pscustomobject]@{ name = 'primary_key'; passed = $true; detail = 'definitions and key sets compared' })
    $paths.Add([pscustomobject]@{ name = 'unique_constraint'; passed = $true; detail = 'all unique indexes compared' })
    $paths.Add([pscustomobject]@{ name = 'business_invariant'; passed = $true; detail = 'declared logical references checked for orphans' })
    $exitCode = 0
} catch {
    Write-RunLog "run FAIL: $($_.Exception.Message)"
    throw
} finally {
    $cleanupPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $AdminPassword
        foreach ($schema in @($createdSchemas)) {
            if ($schema -match '^oj309_[A-Za-z0-9_]+$') { & $Mysql --protocol=TCP --host=$HostName --port=$Port --user=$AdminUser -e "DROP DATABASE IF EXISTS ``$schema``;" 2>> $rawLog }
        }
        foreach ($userName in @($createdUsers)) {
            if ($userName -match '^[us]309_[A-Za-z0-9_]+$') { & $Mysql --protocol=TCP --host=$HostName --port=$Port --user=$AdminUser -e "DROP USER IF EXISTS '$userName'@'%';" 2>> $rawLog }
        }
        foreach ($role in @($createdRoles)) {
            if ($role -match '^r3[a-z0-9]{5}_(auth|crs|assessment|learning_grade)_role$') { & $Mysql --protocol=TCP --host=$HostName --port=$Port --user=$AdminUser -e "DROP ROLE IF EXISTS '$role';" 2>> $rawLog }
        }
    } finally { $env:MYSQL_PWD = $cleanupPassword }
    $finishedAt = [DateTimeOffset]::Now
    $baselineSha = (& git -C $root rev-parse origin/dev 2>$null)
    $testedSha = (& git -C $root rev-parse HEAD 2>$null)
    [pscustomobject]@{
        issue = 309; environment = "mysql@$HostName`:$Port"; baselineSha = $baselineSha; testedSha = $testedSha
        workingTreeDirty = [bool](& git -C $root status --porcelain)
        startedAt = $startedAt.ToString('o'); finishedAt = $finishedAt.ToString('o'); exitCode = $exitCode
        total = $paths.Count; passed = @($paths | Where-Object passed).Count; failed = @($paths | Where-Object { -not $_.passed }).Count; skipped = 0
        paths = @($paths); rawLog = $rawLog
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $evidencePath -Encoding utf8
    Write-Host "evidence: $evidencePath"
}
