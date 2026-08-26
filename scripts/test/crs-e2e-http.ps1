#!/usr/bin/env pwsh
# CRS 接口级端到端闭环（真实 MySQL + 真实 HTTP）。
# 前置：后端已以 compose 配置启动（8080），且已导入 database/mysql/compose-schema.sql 与种子账号。
# 输出：output/playwright/e2e-crs/crs-closure-http.json（PASS/FAIL 断言与原始响应摘要）。
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$OutDir = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path 'output\playwright\e2e-crs')
)

$ErrorActionPreference = 'Stop'
$results = [System.Collections.Generic.List[object]]::new()
$summary = [pscustomobject]@{
    environment = "real MySQL 9.6 + Spring Boot HTTP ($BaseUrl)"
    total = 0
    passed = 0
    failed = 0
    error = $null
    checks = [System.Collections.Generic.List[object]]::new()
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Body = $null,
        [string]$Token = $null
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        ContentType = 'application/json'
    }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 8) }
    $response = Invoke-RestMethod @params
    return $response
}

function Add-Check {
    param([string]$Name, [bool]$Passed, [string]$Detail = '')
    $results.Add([pscustomobject]@{ check = $Name; status = if ($Passed) { 'PASS' } else { 'FAIL' }; detail = $Detail })
    if (-not $Passed) { Write-Host "FAIL: $Name - $Detail" }
}

try {
    $login = Invoke-Api 'POST' '/api/v1/auth/login' @{ account = 'teacher001'; password = 'Teacher001@pass' }
    $teacherToken = $login.data.token
    Add-Check 'teacher001 登录' ($null -ne $teacherToken) '登录成功'

    $studentLogin = Invoke-Api 'POST' '/api/v1/auth/login' @{ account = 'student001'; password = 'Student001@pass' }
    $studentToken = $studentLogin.data.token
    Add-Check 'student001 登录' ($null -ne $studentToken) '登录成功'

    $stamp = Get-Date -Format 'yyyyMMddHHmmss'

    # 1) 教师建课（公开课）
    $course = Invoke-Api 'POST' '/api/v1/courses' @{
        name = "E2E公开课-$stamp"; description = '真实 MySQL + HTTP 闭环验证'; enrollmentMode = 'PUBLIC'; status = 'ACTIVE'
    } $teacherToken
    $publicCourseId = $course.data.id
    Add-Check '教师创建公开课' ($publicCourseId -gt 0) "courseId=$publicCourseId"

    # 2) 章节与资源
    $chapter = Invoke-Api 'POST' "/api/v1/courses/$publicCourseId/chapters" @{ chapterName = '第1章 E2E'; parentId = $null } $teacherToken
    Add-Check '教师创建章节' ($chapter.data.id -gt 0) "chapterId=$($chapter.data.id)"

    $client = [System.Net.Http.HttpClient]::new()
    $multipart = New-Object System.Net.Http.MultipartFormDataContent
    $bytes = [System.Text.Encoding]::UTF8.GetBytes('%PDF-1.4 E2E')
    $fileContent = New-Object System.Net.Http.ByteArrayContent(, $bytes)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/pdf')
    $multipart.Add($fileContent, 'file', '讲义.pdf')
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$BaseUrl/api/v1/courses/$publicCourseId/resources")
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $teacherToken)
    $request.Content = $multipart
    $uploadResponse = $client.SendAsync($request).Result
    $uploadBody = $uploadResponse.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    Add-Check '教师上传资源' ($uploadResponse.StatusCode -eq 200 -and $uploadBody.data.id -gt 0) "status=$($uploadResponse.StatusCode)"
    $resourceId = $uploadBody.data.id

    # 3) 学生公开加入 -> 可见章节与可下载资源
    $join = Invoke-Api 'POST' "/api/v1/courses/$publicCourseId/join" @{} $studentToken
    Add-Check '学生公开加入 ACTIVE' ($join.data.status -eq 'ACTIVE') "status=$($join.data.status)"
    $dup = $null
    try { Invoke-Api 'POST' "/api/v1/courses/$publicCourseId/join" @{} $studentToken | Out-Null } catch { $dup = $_.Exception.Response.StatusCode.value__ }
    Add-Check '重复加入返回 409' ($dup -eq 409) "http=$dup"
    $chapters = Invoke-Api 'GET' "/api/v1/courses/$publicCourseId/chapters" $null $studentToken
    Add-Check '学生读取章节树' ($chapters.data.Count -ge 1) "count=$($chapters.data.Count)"
    $download = Invoke-WebRequest -Uri "$BaseUrl/api/v1/courses/$publicCourseId/resources/$resourceId/download" -Headers @{ Authorization = "Bearer $studentToken" } -UseBasicParsing
    Add-Check '学生下载资源' ($download.StatusCode -eq 200) "status=$($download.StatusCode)"

    # 4) 邀请码加入：错误码与正确码
    $invite = Invoke-Api 'POST' '/api/v1/courses' @{
        name = "E2E邀请码课-$stamp"; enrollmentMode = 'INVITE'; inviteCode = 'E2EINV'; status = 'ACTIVE'
    } $teacherToken
    $wrongStatus = $null
    try { Invoke-Api 'POST' "/api/v1/courses/$($invite.data.id)/join" @{ inviteCode = 'WRONG' } $studentToken | Out-Null } catch { $wrongStatus = $_.Exception.Response.StatusCode.value__ }
    Add-Check '非法邀请码返回 400' ($wrongStatus -eq 400) "http=$wrongStatus"
    $inviteJoin = Invoke-Api 'POST' "/api/v1/courses/$($invite.data.id)/join" @{ inviteCode = 'E2EINV' } $studentToken
    Add-Check '正确邀请码加入 ACTIVE' ($inviteJoin.data.status -eq 'ACTIVE') "status=$($inviteJoin.data.status)"

    # 5) 审批加入：PENDING -> 审批前无权限 -> 审批后权限开放
    $review = Invoke-Api 'POST' '/api/v1/courses' @{
        name = "E2E审批课-$stamp"; enrollmentMode = 'REVIEW'; status = 'ACTIVE'
    } $teacherToken
    $reviewCourseId = $review.data.id
    $apply = Invoke-Api 'POST' "/api/v1/courses/$reviewCourseId/join" @{ applyReason = 'E2E 审批验证' } $studentToken
    Add-Check '审批课加入 PENDING' ($apply.data.status -eq 'PENDING') "status=$($apply.data.status)"
    $beforeStatus = $null
    try { Invoke-Api 'GET' "/api/v1/courses/$reviewCourseId" $null $studentToken | Out-Null; $beforeStatus = 200 } catch { $beforeStatus = $_.Exception.Response.StatusCode.value__ }
    Add-Check '审批前访问课程被拒' ($beforeStatus -eq 403) "http=$beforeStatus"
    $studentId = $studentLogin.data.user.id
    $approve = Invoke-Api 'PUT' "/api/v1/courses/$reviewCourseId/members/$studentId" @{ role = 'STUDENT'; status = 'ACTIVE' } $teacherToken
    Add-Check '教师审批通过' ($approve.data.status -eq 'ACTIVE') "status=$($approve.data.status)"
    $permission = Invoke-Api 'GET' "/api/v1/courses/$reviewCourseId/permissions/$studentId" $null $teacherToken
    Add-Check '审批后成员权限开放' ($permission.data.member -eq $true) "member=$($permission.data.member)"

    # 6) 满员与资源失败
    $full = Invoke-Api 'POST' '/api/v1/courses' @{
        name = "E2E满员课-$stamp"; enrollmentMode = 'PUBLIC'; maxStudents = 1; status = 'ACTIVE'
    } $teacherToken
    Invoke-Api 'POST' "/api/v1/courses/$($full.data.id)/join" @{} $studentToken | Out-Null
    $admin = Invoke-Api 'POST' '/api/v1/auth/login' @{ account = 'admin001'; password = 'Admin001@pass' }
    $fullStatus = $null
    try { Invoke-Api 'POST' "/api/v1/courses/$($full.data.id)/join" @{} $admin.data.token | Out-Null } catch { $fullStatus = $_.Exception.Response.StatusCode.value__ }
    Add-Check '满员课程拒绝加入' ($fullStatus -eq 409) "http=$fullStatus"

    $badUploadRequest = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$BaseUrl/api/v1/courses/$publicCourseId/resources")
    $badUploadRequest.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $teacherToken)
    $badMultipart = New-Object System.Net.Http.MultipartFormDataContent
    $badFile = New-Object System.Net.Http.ByteArrayContent(, [byte[]](1, 2, 3))
    $badMultipart.Add($badFile, 'file', '恶意.exe')
    $badUploadRequest.Content = $badMultipart
    $badUploadResponse = $client.SendAsync($badUploadRequest).Result
    Add-Check '不支持类型上传被拒' ($badUploadResponse.StatusCode -eq 400) "status=$($badUploadResponse.StatusCode)"

    # 7) 公告与首页摘要
    $announcement = Invoke-Api 'POST' "/api/v1/courses/$publicCourseId/announcements" @{ title = 'E2E置顶公告'; content = '闭环验证'; isTop = $true } $teacherToken
    Add-Check '教师发布置顶公告' ($announcement.data.top -eq $true) "top=$($announcement.data.top)"
    $homeSummary = Invoke-Api 'GET' "/api/v1/courses/$publicCourseId/home-summary" $null $studentToken
    Add-Check '课程首页摘要聚合' ($null -ne $homeSummary.data.course -and $homeSummary.data.announcements.Count -ge 1) "course=$($homeSummary.data.course.id)"
} catch {
    $summary.error = $_.Exception.Message
}

$results | ForEach-Object { $_.detail = [regex]::Replace([string]$_.detail, 'token=[A-Za-z0-9_\-\.]+', 'token=[REDACTED]') }
$summary.total = $results.Count
$summary.passed = @($results | Where-Object status -eq 'PASS').Count
$summary.failed = @($results | Where-Object status -eq 'FAIL').Count
$summary.checks = $results

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$outFile = Join-Path $OutDir 'crs-closure-http.json'
$summary | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 -LiteralPath $outFile
Write-Host "PASS=$($summary.passed) FAIL=$($summary.failed) TOTAL=$($summary.total)"
Write-Host "证据输出：$outFile"
if ($summary.error -or $summary.failed -gt 0) {
    Write-Host "CRS E2E 闭环失败：error='$($summary.error)' failed=$($summary.failed)，退出码 1"
    exit 1
}
exit 0
