#!/usr/bin/env pwsh
# crs-e2e-http.ps1 失败路径回归测试。
# 验证：服务不可达（error 分支）或断言收集到 FAIL（failed>0 分支）时，
# 证据 JSON 仍会写出，且脚本必须显式以非 0 退出，不能“吞掉失败返回成功”。

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scriptUnderTest = Join-Path $repoRoot 'scripts/test/crs-e2e-http.ps1'

function Assert-Condition {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if (-not $Condition) {
        Write-Error "$Name 失败：$Detail"
    }
    Write-Host "PASS: $Name"
}

function Invoke-E2eFailureScenario {
    param(
        [string]$Name,
        [string]$BaseUrl,
        [scriptblock]$Server = $null,
        [string]$StopFile = $null
    )
    $outDir = Join-Path ([System.IO.Path]::GetTempPath()) ('crs-e2e-http-test-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    $logFile = Join-Path $outDir 'runner.log'
    $job = $null
    try {
        if ($null -ne $Server) {
            $probe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
            $probe.Start()
            $port = ([System.Net.IPEndPoint]$probe.LocalEndpoint).Port
            $probe.Stop()
            $stubUrl = "http://127.0.0.1:$port/"
            $job = Start-Job -ScriptBlock $Server -ArgumentList $port, $StopFile
            Start-Sleep -Milliseconds 600
            & $scriptUnderTest -BaseUrl $stubUrl.TrimEnd('/') -OutDir $outDir *> $logFile
        } else {
            & $scriptUnderTest -BaseUrl $BaseUrl -OutDir $outDir *> $logFile
        }
        $exitCode = $LASTEXITCODE
        return [pscustomobject]@{
            Name = $Name
            ExitCode = $exitCode
            OutDir = $outDir
        }
    } finally {
        if ($null -ne $job) {
            if ($null -ne $StopFile) {
                New-Item -ItemType File -Path $StopFile -Force | Out-Null
            }
            Wait-Job -Job $job -Timeout 10 -ErrorAction SilentlyContinue | Out-Null
            Stop-Job -Job $job -ErrorAction SilentlyContinue
            Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
        }
    }
}

function Read-Evidence {
    param([string]$OutDir)
    $jsonFile = Join-Path $OutDir 'crs-closure-http.json'
    Assert-Condition '写出证据 JSON' (Test-Path -LiteralPath $jsonFile) $jsonFile
    return Get-Content -Raw -LiteralPath $jsonFile | ConvertFrom-Json
}

# 场景 1：服务不可达（error 分支）——脚本必须写证据并以非 0 退出。
$scenario1 = Invoke-E2eFailureScenario -Name 'unreachable-server' -BaseUrl 'http://127.0.0.1:9'
Assert-Condition '服务不可达时退出码非 0' ($scenario1.ExitCode -ne 0) "exit=$($scenario1.ExitCode)"
$evidence1 = Read-Evidence -OutDir $scenario1.OutDir
Assert-Condition '服务不可达时记录 error' (-not [string]::IsNullOrWhiteSpace($evidence1.error)) "error=$($evidence1.error)"
Assert-Condition '证据包含 total 字段' ($null -ne $evidence1.total) "total=$($evidence1.total)"
Remove-Item -LiteralPath $scenario1.OutDir -Recurse -Force

# 场景 2：服务可达但返回非法数据（failed>0 分支）——脚本必须写证据并以非 0 退出。
$stubServer = {
    param($Port, $StopFile)
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    $listener.Start()
    $count = 0
    try {
        while ($count -lt 200 -and -not (Test-Path -LiteralPath $StopFile)) {
            if (-not $listener.Pending()) {
                Start-Sleep -Milliseconds 50
                continue
            }
            $client = $listener.AcceptTcpClient()
            $stream = $client.GetStream()
            $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
            $line = $reader.ReadLine()
            while ($line) {
                $line = $reader.ReadLine()
            }
            $bytes = [System.Text.Encoding]::UTF8.GetBytes('{"data":{}}')
            $header = "HTTP/1.1 200 OK`r`nContent-Type: application/json`r`nContent-Length: $($bytes.Length)`r`nConnection: close`r`n`r`n"
            $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($header)
            $stream.Write($headerBytes, 0, $headerBytes.Length)
            $stream.Write($bytes, 0, $bytes.Length)
            $client.Close()
            $count++
        }
    } finally {
        $listener.Stop()
    }
}
$scenario2Stop = Join-Path ([System.IO.Path]::GetTempPath()) ('crs-e2e-http-test-stop-' + [guid]::NewGuid().ToString('N'))
$scenario2 = Invoke-E2eFailureScenario -Name 'bad-data' -Server $stubServer -StopFile $scenario2Stop
Assert-Condition '断言 FAIL 时退出码非 0' ($scenario2.ExitCode -ne 0) "exit=$($scenario2.ExitCode)"
$evidence2 = Read-Evidence -OutDir $scenario2.OutDir
Assert-Condition '断言 FAIL 时记录 failed>0' ($evidence2.failed -gt 0) "failed=$($evidence2.failed)"
Assert-Condition '断言 FAIL 时 total 与 checks 对齐' ($evidence2.total -eq @($evidence2.checks).Count) "total=$($evidence2.total) checks=$(@($evidence2.checks).Count)"
Remove-Item -LiteralPath $scenario2.OutDir -Recurse -Force

Write-Host 'crs-e2e-http.test: PASS'
