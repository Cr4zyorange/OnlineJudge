#!/usr/bin/env pwsh
# #355 disposable acceptance against real MySQL 8.4 + RabbitMQ 4.1:
# - course-migrations (migrate-service.sh) creates the frozen Course schema
# - oj_course_rw cannot read another schema (AC-355-02)
# - Course image boots, /version and readiness pass (AC-355-01)
# - homework published fact applies exactly once, duplicates are idempotent
#   and Rabbit outage never breaks Course writes (AC-355-04/05)
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runId = "$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())-$PID"
$mysqlName = "oj355-course-mysql-$runId"
$rabbitName = "oj355-course-rabbit-$runId"
$courseName = "oj355-course-service-$runId"
$evidenceDir = Join-Path $repoRoot "ci-artifacts/issue355-course-live-$runId"
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$mysqlPassword = "oj355-live-root"
$courseDbPassword = "oj355-live-course-rw"
$rabbitUser = "oj_course_events"
$rabbitPassword = "oj355-live-rabbit"

function Write-Evidence($name, $content) {
    $content | Out-File -FilePath (Join-Path $evidenceDir $name) -Encoding utf8
}

function Wait-MySql {
    for ($i = 0; $i -lt 60; $i++) {
        docker exec $mysqlName mysql "--host=127.0.0.1" "--user=root" "--password=$mysqlPassword" -e "SELECT 1" *> $null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    throw "MySQL 8.4 did not become ready"
}

function Wait-Rabbit {
    for ($i = 0; $i -lt 60; $i++) {
        docker exec $rabbitName rabbitmqctl await_startup *> $null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    throw "RabbitMQ 4.1 did not become ready"
}

try {
    Write-Output "STEP: containers"
    docker rm -f $mysqlName $rabbitName 2>$null
    docker run -d --rm --name $mysqlName -e MYSQL_ROOT_PASSWORD=$mysqlPassword -e MYSQL_DATABASE=oj_course -p 127.0.0.1::3306 mysql:8.4 | Out-Null
    docker run -d --rm --name $rabbitName -e RABBITMQ_DEFAULT_USER=$rabbitUser -e RABBITMQ_DEFAULT_PASS=$rabbitPassword -p 127.0.0.1::5672 -p 127.0.0.1::15672 rabbitmq:4.1-management | Out-Null
    Wait-MySql
    Wait-Rabbit
    Write-Output "STEP: ports"
    $mysqlPort = (docker port $mysqlName 3306/tcp | Select-Object -First 1).Split(":")[-1]
    $rabbitPort = (docker port $rabbitName 5672/tcp | Select-Object -First 1).Split(":")[-1]
    $rabbitMgmtPort = (docker port $rabbitName 15672/tcp | Select-Object -First 1).Split(":")[-1]
    Write-Output "STEP: accounts"

    # DDL principal only: identity baseline table for the cross-schema rejection proof,
    # plus the minimal DML account for the Course runtime.
    docker exec $mysqlName mysql "--host=127.0.0.1" "--user=root" "--password=$mysqlPassword" -e "CREATE DATABASE IF NOT EXISTS oj_identity; CREATE TABLE IF NOT EXISTS oj_identity.t_auth_user (user_id BIGINT PRIMARY KEY); INSERT IGNORE INTO oj_identity.t_auth_user (user_id) VALUES (1); CREATE USER IF NOT EXISTS 'oj_course_rw'@'%' IDENTIFIED BY '$courseDbPassword'; GRANT SELECT, INSERT, UPDATE, DELETE ON oj_course.* TO 'oj_course_rw'@'%'; FLUSH PRIVILEGES;" | Out-Null
    Write-Output "STEP: cross-schema"

    # AC-355-02: the Course runtime account must not read another schema.
    $crossSchema = docker exec $mysqlName mysql "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_identity -e "SELECT COUNT(*) FROM t_auth_user" 2>&1
    $crossSchemaText = ($crossSchema | Out-String).Trim()
    Write-Evidence "cross-schema-denied.txt" $crossSchemaText
    if ($crossSchemaText -notmatch "denied|1044|1142") {
        throw "oj_course_rw unexpectedly read oj_identity"
    }
    Write-Output "STEP: migrations"

    # course-migrations via the tracked runner inside mysql:8.4 (POSIX env).
    $migrationOutput = docker run --rm -v "${repoRoot}:/repo" -w /repo `
        -e MYSQL_HOST=host.docker.internal -e MYSQL_PORT=$mysqlPort `
        -e MIGRATION_DATABASE_NAME=oj_course -e MIGRATION_DATABASE_USER=root -e MIGRATION_DATABASE_PASSWORD=$mysqlPassword `
        mysql:8.4 sh /repo/database/mysql/migrate-service.sh --schema course 2>&1
    $migrationText = ($migrationOutput | Out-String).Trim()
    Write-Evidence "course-migrations.log" $migrationText
    if ($migrationText -notmatch "PASS schema=course" -or $migrationText -notmatch "V20260901_07") {
        throw "course migrations did not apply the frozen LRN tables"
    }
    Write-Output "STEP: image"
    $tableCount = docker exec $mysqlName mysql -N "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_course -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='oj_course' AND (table_name LIKE 'lrn_%' OR table_name LIKE 'learning_%')"
    Write-Evidence "course-lrn-table-count.txt" ("course-owned LRN tables=" + ($tableCount | Out-String).Trim())

    # Image: build from the local jar with the cached immutable runtime base.
    $gitSha = git rev-parse HEAD
    Push-Location $repoRoot
    mvn -f services/course/pom.xml -DskipTests package | Out-Null
    $jar = Get-Item services/course/target/onlinejudge-course-service-0.1.0-SNAPSHOT.jar
    if ($jar.Length -lt 1000000) { throw "course jar is not a Spring Boot executable archive (size=$($jar.Length))" }
    Pop-Location
    docker build --build-arg "GIT_SHA=$gitSha" -f services/course/Dockerfile.cached-runtime -t "onlinejudge/course-service:$gitSha" . *> "$evidenceDir\course-image-build.log"
    docker image inspect "onlinejudge/course-service:$gitSha" --format "{{.Id}} {{.Config.Labels}}" | Out-File "$evidenceDir\course-image-id.txt" -Encoding utf8
    Write-Output "STEP: tokens"

    # JWKS + short-lived user JWTs for the real HTTP path.
    $auth = node -e @"
const crypto = require('crypto');
const pair = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const kid = 'course-355-live';
const jwk = pair.publicKey.export({ format: 'jwk' });
const now = Math.floor(Date.now() / 1000);
const token = (userId, roles) => {
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ iss: 'onlinejudge.identity.v2', aud: 'onlinejudge.api', iat: now, exp: now + 600, userId: String(userId), sessionId: '355-' + userId, securityVersion: 1, roles, permissions: [] })).toString('base64url');
  return header + '.' + payload + '.' + crypto.sign('RSA-SHA256', Buffer.from(header + '.' + payload), pair.privateKey).toString('base64url');
};
console.log(JSON.stringify({
  jwks: JSON.stringify({ keys: [{ kty: 'RSA', use: 'sig', alg: 'RS256', kid, n: jwk.n, e: jwk.e }] }),
  teacher: token(7411, ['TEACHER']), student: token(7412, ['STUDENT'])
}));
"@
    $authJson = $auth | ConvertFrom-Json
    Write-Output "STEP: course container"

    docker run -d --rm --name $courseName -p 127.0.0.1::8082 `
        -e COURSE_DATABASE_HOST=host.docker.internal -e COURSE_DATABASE_PORT=$mysqlPort `
        -e COURSE_DATABASE_NAME=oj_course -e COURSE_DATABASE_USER=oj_course_rw -e COURSE_DATABASE_PASSWORD=$courseDbPassword `
        -e COURSE_RABBIT_ENABLED=true -e RABBITMQ_HOST=host.docker.internal -e RABBITMQ_PORT=$rabbitPort `
        -e RABBITMQ_USER=$rabbitUser -e RABBITMQ_PASSWORD=$rabbitPassword `
        -e IDENTITY_JWKS_TRUST_BUNDLE=$($authJson.jwks) -e IDENTITY_JWKS_URI=http://127.0.0.1:9/.well-known/jwks.json `
        -e IDENTITY_JWKS_REFRESH_ENABLED=false `
        "onlinejudge/course-service:$gitSha" | Out-Null
    $coursePort = (docker port $courseName 8082/tcp | Select-Object -First 1).Split(":")[-1]
    $base = "http://127.0.0.1:$coursePort"

    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $health = Invoke-RestMethod -Uri "$base/actuator/health" -TimeoutSec 3
            if ($health.status -eq "UP") { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw "course service did not become ready" }
    Write-Output "STEP: health"
    $version = Invoke-RestMethod -Uri "$base/version" -TimeoutSec 5
    $readiness = Invoke-RestMethod -Uri "$base/actuator/health/readiness" -TimeoutSec 5
    Write-Evidence "course-service-health.json" (($version | ConvertTo-Json -Compress) + "`n" + ($readiness | ConvertTo-Json -Compress))
    Write-Output "STEP: course api"

    $created = Invoke-RestMethod -Uri "$base/api/v1/courses" -Method Post -Headers @{
        "Authorization" = "Bearer $($authJson.teacher)"; "X-Request-Id" = "355-live-course-create"; "Content-Type" = "application/json"
    } -Body '{"name":"Course 355 live","enrollmentMode":"PUBLIC"}'
    $courseId = $created.data.id
    Invoke-RestMethod -Uri "$base/api/v1/courses/$courseId/join" -Method Post -Headers @{
        "Authorization" = "Bearer $($authJson.student)"; "X-Request-Id" = "355-live-course-join"; "Content-Type" = "application/json"
    } -Body '{}' | Out-Null
    Write-Evidence "course-created.txt" ("courseId=$courseId teacher=7411 student=7412")
    Write-Output "STEP: publish facts"

    function Publish-RabbitFact($routingKey, $payloadJson) {
        $body = @{ properties = @{}; routing_key = $routingKey; payload = $payloadJson; payload_encoding = "string" }
            | ConvertTo-Json -Depth 4 -Compress
        $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${rabbitUser}:${rabbitPassword}"))
        $apiBase = "http://127.0.0.1:$rabbitMgmtPort"
        Invoke-RestMethod -Uri "$apiBase/api/exchanges/%2f/onlinejudge.events.v2" -Method Put `
            -Headers @{ Authorization = "Basic $encoded" } -ContentType "application/json" `
            -Body '{"type":"topic","durable":true,"auto_delete":false,"internal":false}' -TimeoutSec 10 | Out-Null
        $result = Invoke-RestMethod -Uri "$apiBase/api/exchanges/%2f/onlinejudge.events.v2/publish" -Method Post `
            -Headers @{ Authorization = "Basic $encoded" } -ContentType "application/json" -Body $body -TimeoutSec 10
        if (-not $result.routed) { throw "RabbitMQ fact $routingKey was not routed" }
    }

    # Wait for Course's own membership snapshots (real source facts) to advance
    # the complete-roster watermark and project both active members.  Receiver
    # resolution must never run against an incomplete roster.
    $projected = 0
    for ($i = 0; $i -lt 30; $i++) {
        $projected = docker exec $mysqlName mysql -N "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_course -e "SELECT COUNT(*) FROM learning_course_member_projection WHERE course_id=$courseId AND membership_status='ACTIVE'" 2>$null
        $wmReady = docker exec $mysqlName mysql -N "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_course -e "SELECT COUNT(*) FROM learning_course_membership_watermark WHERE course_id=$courseId" 2>$null
        if (([string]$projected).Trim() -eq "2" -and ([string]$wmReady).Trim() -eq "1") { break }
        Start-Sleep -Seconds 1
    }
    if (([string]$projected).Trim() -ne "2") {
        throw "Course roster projection did not reach two active members before fact publication"
    }
    Write-Evidence "roster-projection.txt" "projectedMembers=$([string]$projected) watermarkPresent=$([string]$wmReady)"

    $homeworkEnvelope = @{
        eventId = "355-homework-77"; eventType = "assessment.homework.published.v2"; payloadVersion = 2
        aggregateType = "assessment-homework"; aggregateId = "homework-77"; aggregateVersion = 4
        occurredAt = "2026-09-01T01:00:00Z"; correlationId = "355-homework-correlation"
        payload = @{ courseId = "course-$courseId"; homeworkId = "homework-77"; title = "Live homework"; deadline = "2026-09-06T16:00:00Z"; receiverScope = "COURSE_ACTIVE_STUDENTS"; publishedAt = "2026-09-01T01:00:00Z" }
    } | ConvertTo-Json -Depth 6 -Compress
    Publish-RabbitFact "onlinejudge.assessment.homework.published.v2" $homeworkEnvelope
    # duplicate delivery of the same fact
    Publish-RabbitFact "onlinejudge.assessment.homework.published.v2" $homeworkEnvelope

    $taskCount = 0
    $notifCount = 0
    for ($i = 0; $i -lt 30; $i++) {
        $taskCount = docker exec $mysqlName mysql -N "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_course -e "SELECT COUNT(*) FROM lrn_learning_task WHERE user_id=7412 AND course_id=$courseId" 2>$null
        $notifCount = docker exec $mysqlName mysql -N "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_course -e "SELECT COUNT(*) FROM lrn_notification WHERE user_id=7412" 2>$null
        if (([string]$taskCount).Trim() -eq "1" -and ([string]$notifCount).Trim() -eq "1") { break }
        Start-Sleep -Seconds 1
    }
    $inboxCount = docker exec $mysqlName mysql -N "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_course -e "SELECT COUNT(*) FROM learning_event_inbox WHERE consumer_name='course-lrn' AND event_id='355-homework-77'" 2>$null
    $watermark = docker exec $mysqlName mysql -N "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_course -e "SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id=$courseId" 2>$null
    Write-Evidence "fact-idempotency.txt" ("tasks=$([string]$taskCount) notifications=$([string]$notifCount) inbox=$([string]$inboxCount) watermark=$([string]$watermark)")
    $wm = ([string]$watermark).Trim()
    if (([string]$taskCount).Trim() -ne "1" -or ([string]$notifCount).Trim() -ne "1" -or ([string]$inboxCount).Trim() -ne "1" -or $wm -eq "" -or [long]$wm -lt 1) {
        throw "homework fact was not applied exactly once with a complete roster watermark"
    }
    Write-Output "STEP: rabbit outage"

    # AC-355-04: RabbitMQ outage never breaks Course core writes; facts stay durable PENDING.
    docker stop $rabbitName | Out-Null
    $createdDuringOutage = Invoke-RestMethod -Uri "$base/api/v1/courses" -Method Post -Headers @{
        "Authorization" = "Bearer $($authJson.teacher)"; "X-Request-Id" = "355-live-outage-create"; "Content-Type" = "application/json"
    } -Body '{"name":"Course writes during rabbit outage","enrollmentMode":"PUBLIC"}'
    $outageCourseId = $createdDuringOutage.data.id
    $retained = docker exec $mysqlName mysql -N "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_course -e "SELECT COUNT(*) FROM course_event_outbox WHERE delivery_status IN ('PENDING','RETRY','FAILED') AND (aggregate_id='$outageCourseId' OR aggregate_id LIKE '${outageCourseId}:%')"
    $courseRows = docker exec $mysqlName mysql -N "--host=127.0.0.1" "--user=oj_course_rw" "--password=$courseDbPassword" oj_course -e "SELECT COUNT(*) FROM crs_course WHERE id=$outageCourseId"
    Write-Evidence "rabbit-outage.txt" ("outageCourseId=$outageCourseId courseRows=$([string]$courseRows) retainedFacts=$([string]$retained)")
    if ([int][string]$courseRows -ne 1 -or [int][string]$retained -lt 2) {
        throw "Course writes or durable outbox retention failed during Rabbit outage"
    }

    Write-Output "verify-course-355-live: PASS mysql=8.4 rabbit=4.1 migrations=ok cross-schema=denied image=ok health=ok fact-idempotency=ok outage-write=ok evidence=$evidenceDir"
} catch {
    Write-Evidence "failure.txt" ($_.Exception.ToString() + "`n--- stack ---`n" + $_.ScriptStackTrace)
    Write-Output "verify-course-355-live: FAILED at $($_.InvocationInfo.ScriptLineNumber): $($_.Exception.Message)"
    throw
} finally {
    try { docker logs $courseName *> "$evidenceDir\course-service.log" 2>&1 } catch { }
    try { docker logs $mysqlName *> "$evidenceDir\mysql.log" 2>&1 } catch { }
    try { docker logs $rabbitName *> "$evidenceDir\rabbit.log" 2>&1 } catch { }
    docker rm -f $courseName 2>$null
    docker rm -f $mysqlName $rabbitName 2>$null
}
