param([string]$EvidenceDirectory)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$instancePath = Join-Path $root ("tmp\oj309-mysql-" + [Guid]::NewGuid().ToString('N'))
$mysqld = (Get-Command mysqld -ErrorAction Stop).Source
$mysqladmin = (Get-Command mysqladmin -ErrorAction Stop).Source
$baseDirectory = Split-Path (Split-Path $mysqld)
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
$process = $null

New-Item -ItemType Directory -Force -Path $instancePath | Out-Null
try {
    & $mysqld --no-defaults --initialize-insecure "--basedir=$baseDirectory" "--datadir=$instancePath"
    if ($LASTEXITCODE -ne 0) { throw "mysqld initialize failed: $LASTEXITCODE" }
    $stdout = Join-Path $instancePath 'mysqld.stdout.log'
    $stderr = Join-Path $instancePath 'mysqld.stderr.log'
    $arguments = @(
        '--no-defaults', "--basedir=`"$baseDirectory`"", "--datadir=`"$instancePath`"", "--port=$port",
        '--bind-address=127.0.0.1', '--mysqlx=0', '--console'
    )
    $process = Start-Process -FilePath $mysqld -ArgumentList $arguments -WindowStyle Hidden `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$port --user=root ping --silent 2>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "ephemeral mysqld did not become ready: $(Get-Content -Raw $stderr)" }

    $arguments = @{
        HostName = 'localhost'; Port = $port; AdminUser = 'root'; AdminPassword = ''
        AllowEmptyAdminPassword = $true
    }
    if ($EvidenceDirectory) { $arguments.EvidenceDirectory = $EvidenceDirectory }
    & (Join-Path $PSScriptRoot 'verify-data-ownership-migration.ps1') @arguments
} finally {
    if ($process -and -not $process.HasExited) {
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$port --user=root shutdown 2>$null
        $process.WaitForExit(10000) | Out-Null
        if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit(10000) | Out-Null }
    }
    $resolved = [IO.Path]::GetFullPath($instancePath)
    $safePrefix = [IO.Path]::GetFullPath((Join-Path $root 'tmp\oj309-mysql-'))
    if ($resolved.StartsWith($safePrefix, [StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $resolved -PathType Container)) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
