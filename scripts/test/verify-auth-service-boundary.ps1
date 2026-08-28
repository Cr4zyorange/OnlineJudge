param(
    [string]$ServiceRoot = "services/auth-service"
)

$ErrorActionPreference = "Stop"

function Fail-Boundary([string]$Message) {
    throw "AUTH boundary verification failed: $Message"
}

try {
    $repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../.."))
    $resolvedServiceRoot = if ([System.IO.Path]::IsPathRooted($ServiceRoot)) {
        [System.IO.Path]::GetFullPath($ServiceRoot)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $ServiceRoot))
    }

    if (-not $resolvedServiceRoot.StartsWith($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        Fail-Boundary "service root must stay inside the repository"
    }
    if (-not (Test-Path -LiteralPath $resolvedServiceRoot -PathType Container)) {
        Fail-Boundary "service root does not exist: $resolvedServiceRoot"
    }

    $forbiddenModules = "crs|lab|hwk|grd|lrn|integration|evaluation|storage|event"
    $forbiddenJava = Get-ChildItem -LiteralPath $resolvedServiceRoot -Recurse -File -Filter "*.java" |
        Where-Object {
            $_.FullName -match "[\\/]com[\\/]onlinejudge[\\/]($forbiddenModules)([\\/]|$)" -or
            (Select-String -LiteralPath $_.FullName -Pattern "com\.onlinejudge\.($forbiddenModules)(\.|;)" -Quiet)
        }
    if ($forbiddenJava.Count -gt 0) {
        $paths = ($forbiddenJava.FullName | Select-Object -First 10 | ForEach-Object {
            [System.IO.Path]::GetRelativePath($repositoryRoot, $_)
        }) -join ", "
        Fail-Boundary "foreign Java modules detected ($($forbiddenJava.Count)); first matches: $paths"
    }

    $forbiddenSqlPattern = "(?i)\b(crs_|lab_|t_hwk_|lrn_|t_grade_|t_course_grade_summary)"
    $forbiddenSql = Get-ChildItem -LiteralPath $resolvedServiceRoot -Recurse -File -Filter "*.sql" |
        Where-Object { Select-String -LiteralPath $_.FullName -Pattern $forbiddenSqlPattern -Quiet }
    if ($forbiddenSql.Count -gt 0) {
        $paths = ($forbiddenSql.FullName | Select-Object -First 10 | ForEach-Object {
            [System.IO.Path]::GetRelativePath($repositoryRoot, $_)
        }) -join ", "
        Fail-Boundary "foreign SQL ownership detected ($($forbiddenSql.Count)); first matches: $paths"
    }

    $pom = Join-Path $resolvedServiceRoot "pom.xml"
    if (-not (Test-Path -LiteralPath $pom -PathType Leaf)) {
        Fail-Boundary "standalone pom.xml is missing"
    }

    $mavenCommand = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "mvn" }
    & $mavenCommand -f $pom test
    if ($LASTEXITCODE -ne 0) {
        Fail-Boundary "standalone tests exited with $LASTEXITCODE"
    }

    & $mavenCommand -f $pom -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        Fail-Boundary "standalone package exited with $LASTEXITCODE"
    }

    $artifact = Join-Path $resolvedServiceRoot "target/onlinejudge-auth-service-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
        Fail-Boundary "standalone package was not produced"
    }

    $testedSha = (& git -C $repositoryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        Fail-Boundary "unable to resolve tested Git SHA"
    }

    Write-Output "AUTH boundary verification PASS"
    Write-Output "service=$([System.IO.Path]::GetRelativePath($repositoryRoot, $resolvedServiceRoot))"
    Write-Output "forbidden-java=0"
    Write-Output "forbidden-sql=0"
    Write-Output "artifact=$([System.IO.Path]::GetRelativePath($repositoryRoot, $artifact))"
    Write-Output "tested-sha=$testedSha"
    exit 0
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
