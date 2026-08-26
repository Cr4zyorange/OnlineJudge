[CmdletBinding()]
param(
    [switch]$SkipCompose,
    [switch]$SkipE2E,
    [switch]$KeepEnvironment,
    [switch]$Diagnostic
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
$composeProject = "oj265-$(([Guid]::NewGuid().ToString('N')).Substring(0, 12))"
$composeOverrideFile = Join-Path ([IO.Path]::GetTempPath()) "$composeProject.override.yml"
$composeCleanupRegistered = $false
$composeReady = $false
$previousE2EBaseUrl = [Environment]::GetEnvironmentVariable('E2E_BASE_URL', 'Process')
$previousOjHttpPort = [Environment]::GetEnvironmentVariable('OJ_HTTP_PORT', 'Process')
$isolatedPort = $null

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

function Get-DisposablePort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function New-ComposeOverride {
    $override = @'
services:
  mysql:
    container_name: !reset null
  backend:
    container_name: !reset null
  frontend:
    container_name: !reset null
'@
    [IO.File]::WriteAllText($composeOverrideFile, $override, [Text.UTF8Encoding]::new($false))
}

function Get-ComposeArguments {
    param([string[]]$CommandArguments)

    $arguments = @(
        '-p', $composeProject,
        '-f', $composeFiles[0],
        '-f', $composeFiles[1],
        '-f', $composeFiles[2],
        '-f', $composeOverrideFile
    )
    return $arguments + $CommandArguments
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
    New-ComposeOverride
    $isolatedPort = Get-DisposablePort
    [Environment]::SetEnvironmentVariable('OJ_HTTP_PORT', [string]$isolatedPort, 'Process')
    [Environment]::SetEnvironmentVariable('E2E_BASE_URL', "http://127.0.0.1:$isolatedPort", 'Process')

    $head = (& git -C $repoRoot rev-parse HEAD).Trim()
    Add-CheckResult -Status PASS -Name 'baseline' -Detail $head
    Add-CheckResult -Status PASS -Name 'disposable Compose environment' -Detail "$composeProject on http://127.0.0.1:$isolatedPort"

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
        Invoke-Compose -Arguments (Get-ComposeArguments @('config'))
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
        # Register cleanup before startup so a partial `up --wait` still has a scoped teardown.
        $script:composeCleanupRegistered = $true
        Invoke-Check -Name 'Compose application environment' -Action {
            Invoke-Compose -Arguments (Get-ComposeArguments @('up', '--build', '--wait', '-d'))
            $script:composeReady = $true
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
    } elseif (-not $composeReady) {
        Add-CheckResult -Status BLOCKED -Name 'LAB Playwright E2E' -Detail 'requires a healthy disposable Compose application environment'
    } else {
        Invoke-Check -Name 'LAB Playwright E2E' -Action {
            Push-Location $frontendRoot
            try {
                & npm run test:e2e -- tests/e2e/lab/issue-265-lab-lifecycle.spec.ts
            } finally {
                Pop-Location
            }
        }
    }
} finally {
    try {
        if ($composeCleanupRegistered -and -not $KeepEnvironment) {
            Invoke-Check -Name 'Compose container cleanup' -Action {
                Invoke-Compose -Arguments (Get-ComposeArguments @('down', '--volumes', '--remove-orphans'))
            }
        }
    } finally {
        if (Test-Path -LiteralPath $composeOverrideFile) {
            Remove-Item -LiteralPath $composeOverrideFile -Force -ErrorAction SilentlyContinue
        }
        [Environment]::SetEnvironmentVariable('E2E_BASE_URL', $previousE2EBaseUrl, 'Process')
        [Environment]::SetEnvironmentVariable('OJ_HTTP_PORT', $previousOjHttpPort, 'Process')
        Write-Summary
    }
}

$hasFail = $results.Status -contains 'FAIL'
$hasBlocked = $results.Status -contains 'BLOCKED'
if ($hasFail -or ($hasBlocked -and -not $Diagnostic)) {
    exit 1
}
