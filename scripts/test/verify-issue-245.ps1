[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Windows PowerShell 5.1 does not load this standard assembly until it is requested.
Add-Type -AssemblyName System.Net.Http
$httpClient = [System.Net.Http.HttpClient]::new()
$httpClient.Timeout = [TimeSpan]::FromSeconds(15)
$verificationFailures = [System.Collections.Generic.List[string]]::new()

function Invoke-Api {
    param(
        [Parameter(Mandatory)] [string]$Method,
        [Parameter(Mandatory)] [string]$Path,
        [string]$Token,
        [object]$Body,
        [System.Net.Http.HttpContent]$Content
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        [Uri]::new("$BaseUrl$Path")
    )
    try {
        if ($Token) {
            $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
        }
        if ($PSBoundParameters.ContainsKey("Content")) {
            $request.Content = $Content
        } elseif ($PSBoundParameters.ContainsKey("Body")) {
            $json = $Body | ConvertTo-Json -Depth 8 -Compress
            $request.Content = [System.Net.Http.StringContent]::new(
                $json,
                [System.Text.Encoding]::UTF8,
                "application/json"
            )
        }

        $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
        try {
            $rawBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $payload = if ([string]::IsNullOrWhiteSpace($rawBody)) { $null } else { $rawBody | ConvertFrom-Json }
            return [pscustomobject]@{
                StatusCode = [int]$response.StatusCode
                Payload = $payload
                RawBody = $rawBody
            }
        } finally {
            $response.Dispose()
        }
    } finally {
        $request.Dispose()
    }
}

function Assert-Response {
    param(
        [Parameter(Mandatory)] $Response,
        [Parameter(Mandatory)] [int]$ExpectedStatus,
        [string]$ExpectedCode,
        [Parameter(Mandatory)] [string]$Step
    )

    if ($Response.StatusCode -ne $ExpectedStatus) {
        throw "${Step}: expected HTTP $ExpectedStatus, got $($Response.StatusCode); body=$($Response.RawBody)"
    }
    if ($ExpectedCode -and $Response.Payload.code -ne $ExpectedCode) {
        throw "${Step}: expected code $ExpectedCode, got $($Response.Payload.code); body=$($Response.RawBody)"
    }
    Write-Host "PASS $Step (HTTP $ExpectedStatus)"
}

function Assert-Equal {
    param(
        [Parameter(Mandatory)] $Actual,
        [Parameter(Mandatory)] $Expected,
        [Parameter(Mandatory)] [string]$Step
    )

    if ($Actual -ne $Expected) {
        throw "${Step}: expected '$Expected', got '$Actual'"
    }
    Write-Host "PASS $Step"
}

function Login {
    param([string]$Account, [string]$Password)

    $response = Invoke-Api -Method "POST" -Path "/api/v1/auth/login" -Body @{
        account = $Account
        password = $Password
    }
    Assert-Response -Response $response -ExpectedStatus 200 -ExpectedCode "0" -Step "login $Account"
    if ([string]::IsNullOrWhiteSpace($response.Payload.data.token)) {
        throw "login ${Account}: response did not contain a session token"
    }
    return [string]$response.Payload.data.token
}

function New-ReportContent {
    param([long]$SubmissionId, [string]$FileName, [string]$ContentType, [string]$Text)

    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $form.Add([System.Net.Http.StringContent]::new([string]$SubmissionId), "submissionId")
    $fileContent = [System.Net.Http.ByteArrayContent]::new([System.Text.Encoding]::UTF8.GetBytes($Text))
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
    $form.Add($fileContent, "reportFile", $FileName)
    # MultipartFormDataContent implements IEnumerable; prevent PowerShell from unrolling it into its parts.
    return ,$form
}

try {
    $teacherToken = Login -Account "teacher001" -Password "Teacher001@pass"
    $studentToken = Login -Account "student001" -Password "Student001@pass"

    $deadline = (Get-Date).AddDays(1).ToString("yyyy-MM-ddTHH:mm:ss")
    $labPayload = @{
        chapterId = 950101
        title = "Issue 245 API verification $(Get-Date -Format 'HHmmss')"
        description = "Local acceptance verification only."
        deadline = $deadline
        maxScore = 100
        attachmentIds = @()
        allowedLanguages = "python"
        evaluationMode = "MANUAL"
        autoEvaluate = $false
        reportRequired = $true
        timeLimitMs = 1000
        memoryLimitKb = 65536
        testcases = @(@{
            input = "1`n"
            expectedOutput = "1`n"
            scoreWeight = 100
            "public" = $true
            timeLimitMs = 1000
            memoryLimitKb = 65536
            orderNum = 1
        })
    }

    $studentCreate = Invoke-Api -Method "POST" -Path "/api/v1/courses/9501/labs" -Token $studentToken -Body $labPayload
    Assert-Response -Response $studentCreate -ExpectedStatus 403 -ExpectedCode "ERR-AUTH-05" -Step "UC-LAB-01 student cannot create a lab"

    $invalidTimePayload = $labPayload.Clone()
    $invalidTimePayload.deadline = (Get-Date).AddMinutes(-1).ToString("yyyy-MM-ddTHH:mm:ss")
    $invalidTime = Invoke-Api -Method "POST" -Path "/api/v1/courses/9501/labs" -Token $teacherToken -Body $invalidTimePayload
    Assert-Response -Response $invalidTime -ExpectedStatus 400 -ExpectedCode "LAB-400-01" -Step "UC-LAB-01 rejects a past deadline"

    $createdLab = Invoke-Api -Method "POST" -Path "/api/v1/courses/9501/labs" -Token $teacherToken -Body $labPayload
    Assert-Response -Response $createdLab -ExpectedStatus 201 -ExpectedCode "0" -Step "UC-LAB-01 teacher creates draft lab"
    Assert-Equal -Actual $createdLab.Payload.data.status -Expected "DRAFT" -Step "UC-LAB-01 new lab remains draft"
    $labId = [long]$createdLab.Payload.data.id

    $studentReadsDraft = Invoke-Api -Method "GET" -Path "/api/v1/labs/$labId" -Token $studentToken
    Assert-Response -Response $studentReadsDraft -ExpectedStatus 403 -ExpectedCode "LAB-403-01" -Step "UC-LAB-01 draft is hidden from student"

    $publishedLab = Invoke-Api -Method "POST" -Path "/api/v1/labs/$labId/publish" -Token $teacherToken
    Assert-Response -Response $publishedLab -ExpectedStatus 200 -ExpectedCode "0" -Step "UC-LAB-01 teacher publishes lab"
    Assert-Equal -Actual $publishedLab.Payload.data.status -Expected "PUBLISHED" -Step "UC-LAB-01 publish state transition"

    $submissionContent = [System.Net.Http.MultipartFormDataContent]::new()
    $submissionContent.Add([System.Net.Http.StringContent]::new("print('issue 245 v1')"), "code")
    $submissionContent.Add([System.Net.Http.StringContent]::new("python"), "language")
    $firstSubmission = Invoke-Api -Method "POST" -Path "/api/v1/labs/$labId/submissions" -Token $studentToken -Content $submissionContent
    Assert-Response -Response $firstSubmission -ExpectedStatus 201 -ExpectedCode "0" -Step "UC-LAB-02 first code submission"
    Assert-Equal -Actual $firstSubmission.Payload.data.version -Expected 1 -Step "UC-LAB-02 first submission version"
    $submissionId = [long]$firstSubmission.Payload.data.submissionId

    $secondSubmissionContent = [System.Net.Http.MultipartFormDataContent]::new()
    $secondSubmissionContent.Add([System.Net.Http.StringContent]::new("print('issue 245 v2')"), "code")
    $secondSubmissionContent.Add([System.Net.Http.StringContent]::new("python"), "language")
    $secondSubmission = Invoke-Api -Method "POST" -Path "/api/v1/labs/$labId/submissions" -Token $studentToken -Content $secondSubmissionContent
    Assert-Response -Response $secondSubmission -ExpectedStatus 201 -ExpectedCode "0" -Step "UC-LAB-02 resubmits code"
    Assert-Equal -Actual $secondSubmission.Payload.data.version -Expected 2 -Step "UC-LAB-02 submission version increments"

    $invalidReport = Invoke-Api -Method "POST" -Path "/api/v1/labs/$labId/reports" -Token $studentToken -Content (New-ReportContent -SubmissionId $submissionId -FileName "invalid.txt" -ContentType "text/plain" -Text "not a report")
    Assert-Response -Response $invalidReport -ExpectedStatus 400 -Step "UC-LAB-02 rejects an unsupported report attachment"

    $firstReport = Invoke-Api -Method "POST" -Path "/api/v1/labs/$labId/reports" -Token $studentToken -Content (New-ReportContent -SubmissionId $submissionId -FileName "issue-245-v1.pdf" -ContentType "application/pdf" -Text "Issue 245 report v1")
    Assert-Response -Response $firstReport -ExpectedStatus 201 -ExpectedCode "0" -Step "UC-LAB-02 uploads report"
    Assert-Equal -Actual $firstReport.Payload.data.version -Expected 1 -Step "UC-LAB-02 first report version"

    $secondReport = Invoke-Api -Method "POST" -Path "/api/v1/labs/$labId/reports" -Token $studentToken -Content (New-ReportContent -SubmissionId $submissionId -FileName "issue-245-v2.pdf" -ContentType "application/pdf" -Text "Issue 245 report v2")
    Assert-Response -Response $secondReport -ExpectedStatus 201 -ExpectedCode "0" -Step "UC-LAB-02 resubmits report"
    Assert-Equal -Actual $secondReport.Payload.data.version -Expected 2 -Step "UC-LAB-02 report version increments"

    $history = Invoke-Api -Method "GET" -Path "/api/v1/labs/$labId/submissions" -Token $studentToken
    Assert-Response -Response $history -ExpectedStatus 200 -ExpectedCode "0" -Step "UC-LAB-02 reads own submission history"
    Assert-Equal -Actual $history.Payload.data[0].version -Expected 2 -Step "UC-LAB-02 history is newest first"

    # Docker is intentionally unavailable in this local environment; the result must remain queryable as SYSTEM_ERROR.
    $evaluationContent = [System.Net.Http.MultipartFormDataContent]::new()
    $evaluationContent.Add([System.Net.Http.StringContent]::new("print('EMPTY')"), "code")
    $evaluationContent.Add([System.Net.Http.StringContent]::new("python"), "language")
    $evaluationSubmission = Invoke-Api -Method "POST" -Path "/api/v1/labs/950211/submissions" -Token $studentToken -Content $evaluationContent
    Assert-Response -Response $evaluationSubmission -ExpectedStatus 201 -ExpectedCode "0" -Step "UC-LAB-02 submits to auto-evaluation lab"
    $evaluationSubmissionId = [long]$evaluationSubmission.Payload.data.submissionId
    $evaluationResult = $null
    for ($attempt = 1; $attempt -le 20; $attempt++) {
        Start-Sleep -Milliseconds 500
        $evaluationResult = Invoke-Api -Method "GET" -Path "/api/v1/labs/950211/submissions/$evaluationSubmissionId/result" -Token $studentToken
        if ($evaluationResult.StatusCode -eq 200 -and $evaluationResult.Payload.data.evaluationStatus -notin @("PENDING", "RUNNING")) {
            break
        }
    }
    Assert-Response -Response $evaluationResult -ExpectedStatus 200 -ExpectedCode "0" -Step "UC-LAB-02 reads evaluation result"
    if ($evaluationResult.Payload.data.evaluationStatus -eq "SYSTEM_ERROR") {
        Write-Host "PASS UC-LAB-02 persists evaluator exception"
    } else {
        $verificationFailures.Add(
            "UC-LAB-02 expected SYSTEM_ERROR for unavailable Docker sandbox, got $($evaluationResult.Payload.data.evaluationStatus)"
        )
        Write-Host "FAIL UC-LAB-02 persists evaluator exception (got $($evaluationResult.Payload.data.evaluationStatus))"
    }

    $reviewPayload = @{ gradeItemId = 950401; targetType = "ITEM_SCORE"; reason = "Issue 245 rejection path" }
    $firstReview = Invoke-Api -Method "POST" -Path "/api/v1/courses/9501/grade-review-requests" -Token $studentToken -Body $reviewPayload
    Assert-Response -Response $firstReview -ExpectedStatus 200 -ExpectedCode "0" -Step "UC-GR-04 student submits review request"
    Assert-Equal -Actual $firstReview.Payload.data.status -Expected "PENDING" -Step "UC-GR-04 review begins pending"
    $firstReviewId = [long]$firstReview.Payload.data.requestId

    $duplicateReview = Invoke-Api -Method "POST" -Path "/api/v1/courses/9501/grade-review-requests" -Token $studentToken -Body $reviewPayload
    Assert-Response -Response $duplicateReview -ExpectedStatus 400 -ExpectedCode "ERR-GRD-08" -Step "UC-GR-04 rejects duplicate pending review"

    $studentProcess = Invoke-Api -Method "PUT" -Path "/api/v1/grade-review-requests/$firstReviewId/process" -Token $studentToken -Body @{ action = "REJECT"; responseComment = "student must not process" }
    Assert-Response -Response $studentProcess -ExpectedStatus 403 -ExpectedCode "ERR-AUTH-05" -Step "UC-GR-04 student cannot process review"

    $rejectedReview = Invoke-Api -Method "PUT" -Path "/api/v1/grade-review-requests/$firstReviewId/process" -Token $teacherToken -Body @{ action = "REJECT"; responseComment = "Evidence is insufficient." }
    Assert-Response -Response $rejectedReview -ExpectedStatus 200 -ExpectedCode "0" -Step "UC-GR-04 teacher rejects review"
    Assert-Equal -Actual $rejectedReview.Payload.data.status -Expected "REJECTED" -Step "UC-GR-04 rejection state transition"

    $approvedPayload = @{ gradeItemId = 950401; targetType = "ITEM_SCORE"; reason = "Issue 245 recalculation path" }
    $approvedReview = Invoke-Api -Method "POST" -Path "/api/v1/courses/9501/grade-review-requests" -Token $studentToken -Body $approvedPayload
    Assert-Response -Response $approvedReview -ExpectedStatus 200 -ExpectedCode "0" -Step "UC-GR-04 permits a new review after rejection"
    $approvedReviewId = [long]$approvedReview.Payload.data.requestId

    $processedReview = Invoke-Api -Method "PUT" -Path "/api/v1/grade-review-requests/$approvedReviewId/process" -Token $teacherToken -Body @{ action = "APPROVE"; adjustedScore = 95; responseComment = "Verified source score and recalculated total." }
    Assert-Response -Response $processedReview -ExpectedStatus 200 -ExpectedCode "0" -Step "UC-GR-04 teacher approves review"
    Assert-Equal -Actual $processedReview.Payload.data.status -Expected "APPROVED" -Step "UC-GR-04 approval state transition"

    $gradesAfterReview = Invoke-Api -Method "GET" -Path "/api/v1/courses/9501/my-grades" -Token $studentToken
    Assert-Response -Response $gradesAfterReview -ExpectedStatus 200 -ExpectedCode "0" -Step "UC-GR-04 student reads recalculated grade"
    Assert-Equal -Actual ([decimal]$gradesAfterReview.Payload.data.summary.finalScore) -Expected ([decimal]"90.80") -Step "UC-GR-04 approved item review recalculates final score"

    if ($verificationFailures.Count -gt 0) {
        throw "ISSUE-245 API ACCEPTANCE FAILED: $($verificationFailures -join '; ')"
    }
    Write-Output "ISSUE-245 API ACCEPTANCE PASSED"
} finally {
    $httpClient.Dispose()
}
