[CmdletBinding()]
param(
    [switch]$SkipCompose,
    [switch]$SkipE2E,
    [switch]$KeepEnvironment
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$frontendRoot = Join-Path $repoRoot 'frontend'
$composeRoot = Join-Path $repoRoot 'deploy/docker'
$composeFiles = @(
    (Join-Path $composeRoot 'compose.yml'),
    (Join-Path $composeRoot 'compose.eval.yml'),
    (Join-Path $composeRoot 'compose.eval.local.example.yml')
)
$results = [System.Collections.Generic.List[object]]::new()
$composeStarted = $false

function Add-CheckResult {
    param(
        [ValidateSet('PASS', 'FAIL', 'BLOCKED')][string]$Status,
        [string]$Name,
        [string]$Detail
    )

    $results.Add([PSCustomObject]@{
        Status = $Status
        Name = $Name
        Detail = $Detail
    })
    Write-Host ("[{0}] {1}: {2}" -f $Status, $Name, $Detail)
}

function Invoke-Check {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    try {
        & $Action
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {
            throw "command exited with code $LASTEXITCODE"
        }
        Add-CheckResult -Status PASS -Name $Name -Detail 'completed'
    } catch {
        Add-CheckResult -Status FAIL -Name $Name -Detail $_.Exception.Message
    }
}

function Invoke-Compose {
    param([string[]]$Arguments)

    Push-Location $composeRoot
    try {
        & docker compose @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose exited with code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Test-DockerContainerCleanup {
    $leftovers = @(& docker ps -a --format '{{.Names}}' --filter 'name=oj-lab-' 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw 'could not inspect Docker sandbox containers'
    }
    if ($leftovers.Count -gt 0) {
        throw ("sandbox containers remain: {0}" -f ($leftovers -join ', '))
    }
}

function Invoke-RealDockerSmoke {
    $previousValue = [Environment]::GetEnvironmentVariable('OJ_DOCKER_SANDBOX_TEST', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('OJ_DOCKER_SANDBOX_TEST', 'true', 'Process')
        Push-Location $backendRoot
        try {
            & mvn test '-Dtest=DockerSandboxExecutorTest'
            if ($LASTEXITCODE -ne 0) {
                throw "real Docker sandbox smoke exited with code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
    } finally {
        [Environment]::SetEnvironmentVariable('OJ_DOCKER_SANDBOX_TEST', $previousValue, 'Process')
    }
}

function Write-Summary {
    Write-Host ''
    Write-Host 'Issue #265 verification summary'
    $results | Format-Table -AutoSize | Out-String | Write-Host
}

try {
    $head = (& git -C $repoRoot rev-parse HEAD).Trim()
    Add-CheckResult -Status PASS -Name 'baseline' -Detail $head

    Invoke-Check -Name 'Docker daemon' -Action {
        & docker info --format '{{.ServerVersion}} {{.OSType}} {{.Architecture}}'
        if ($LASTEXITCODE -ne 0) {
            throw 'Docker daemon is unavailable'
        }
    }

    Invoke-Check -Name 'Docker evaluator image preflight' -Action {
        $image = if ([string]::IsNullOrWhiteSpace($env:ONLINEJUDGE_EVALUATION_DOCKER_PYTHON_IMAGE)) {
            'python:3.12-alpine'
        } else {
            $env:ONLINEJUDGE_EVALUATION_DOCKER_PYTHON_IMAGE
        }
        & docker pull $image
        if ($LASTEXITCODE -ne 0) {
            throw "could not prepare evaluator image $image"
        }
    }

    Invoke-Check -Name 'Compose evaluation configuration' -Action {
        $arguments = @()
        foreach ($composeFile in $composeFiles) {
            $arguments += @('-f', $composeFile)
        }
        $arguments += 'config'
        Invoke-Compose -Arguments $arguments
    }

    Invoke-Check -Name 'LAB backend behavior and transaction tests' -Action {
        Push-Location $backendRoot
        try {
            & mvn test '-Dtest=LabExperimentControllerTest,LabSubmissionControllerTest,LabExperimentMigrationTest,LabExperimentTransactionTest,LabEvaluationServiceTest,DockerSandboxExecutorTest'
        } finally {
            Pop-Location
        }
    }

    Invoke-Check -Name 'real Docker sandbox smoke' -Action { Invoke-RealDockerSmoke }

    Invoke-Check -Name 'Docker sandbox cleanup' -Action { Test-DockerContainerCleanup }
    Add-CheckResult -Status BLOCKED -Name 'Docker daemon disconnect during evaluation' -Detail 'requires an unsafe mid-run daemon interruption; verify on a disposable FAT host'

    if ($SkipCompose) {
        Add-CheckResult -Status BLOCKED -Name 'Compose application environment' -Detail 'skipped by -SkipCompose'
    } else {
        Invoke-Check -Name 'Compose application environment' -Action {
            Invoke-Compose -Arguments @('-f', $composeFiles[0], '-f', $composeFiles[1], '-f', $composeFiles[2], 'up', '--build', '--wait', '-d')
            $script:composeStarted = $true
        }
    }

    Invoke-Check -Name 'LAB frontend unit tests' -Action {
        Push-Location $frontendRoot
        try {
            & npm run test:unit -- tests/unit/api/labs.spec.ts tests/unit/lab/LabTeacherView.spec.ts tests/unit/lab/LabStudentView.spec.ts tests/unit/lab/LabSubmissionHistoryView.spec.ts tests/unit/lab/LabSubmissionReviewView.spec.ts
        } finally {
            Pop-Location
        }
    }

    Invoke-Check -Name 'frontend typecheck' -Action {
        Push-Location $frontendRoot
        try { & npm run typecheck } finally { Pop-Location }
    }

    Invoke-Check -Name 'frontend build' -Action {
        Push-Location $frontendRoot
        try { & npm run build } finally { Pop-Location }
    }

    if ($SkipE2E) {
        Add-CheckResult -Status BLOCKED -Name 'LAB Playwright E2E' -Detail 'skipped by -SkipE2E'
    } elseif ($SkipCompose) {
        Add-CheckResult -Status BLOCKED -Name 'LAB Playwright E2E' -Detail 'requires the Compose application environment'
    } else {
        Invoke-Check -Name 'LAB Playwright E2E' -Action {
            Push-Location $frontendRoot
            try {
                if ([string]::IsNullOrWhiteSpace($env:E2E_BASE_URL)) {
                    $env:E2E_BASE_URL = 'http://127.0.0.1:8088'
                }
                & npm run test:e2e -- tests/e2e/lab/issue-265-lab-lifecycle.spec.ts
            } finally {
                Pop-Location
            }
        }
    }
} finally {
    if ($composeStarted -and -not $KeepEnvironment) {
        Invoke-Check -Name 'Compose container cleanup' -Action {
            Invoke-Compose -Arguments @('-f', $composeFiles[0], '-f', $composeFiles[1], '-f', $composeFiles[2], 'down', '--volumes', '--remove-orphans')
        }
    }

    Write-Summary
}

if ($results.Status -contains 'FAIL') {
    exit 1
}
