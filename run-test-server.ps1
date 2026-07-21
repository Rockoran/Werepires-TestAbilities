<#
    VampireSMP local test loop.

    Default:        build the plugin, copy it into the test server, then start the server.
    -NoBuild:       skip the Maven build and just (re)deploy the existing jar + start.
    -BuildOnly:     build + deploy but don't start the server.

    Everything lives inside this project (test-server\). Stop the server by typing `stop`
    in its console. Run again after each change to test.

    Usage:
        .\run-test-server.ps1
        .\run-test-server.ps1 -NoBuild
        .\run-test-server.ps1 -BuildOnly
#>

param(
    [switch]$NoBuild,
    [switch]$BuildOnly
)

$ErrorActionPreference = "Stop"
$root   = $PSScriptRoot
$server = Join-Path $root "test-server"
$jarOut = Join-Path $root "testbuilds\Werepires1.3.jar"
$plugins = Join-Path $server "plugins"

# Maven bundled with IntelliJ (no standalone Maven needed on this machine).
$mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"

# Java 21 (required by Paper 1.21.10). Prefer the Adoptium JDK, fall back to PATH.
$java = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\java.exe"
if (-not (Test-Path $java)) { $java = "java" }

# ── 1. Build ────────────────────────────────────────────────────────────────
if (-not $NoBuild) {
    Write-Host "[test] Building plugin..." -ForegroundColor Cyan
    if (-not (Test-Path $mvn)) {
        Write-Host "[test] Maven not found at: $mvn" -ForegroundColor Red
        Write-Host "[test] Edit the `$mvn path in this script, or run with -NoBuild." -ForegroundColor Yellow
        exit 1
    }
    & $mvn -q -DskipTests -f (Join-Path $root "pom.xml") clean package
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[test] Build FAILED — server not started." -ForegroundColor Red
        exit 1
    }
    Write-Host "[test] Build OK." -ForegroundColor Green
}

# ── 2. Deploy ─────────────────────────────────────────────────────────────────
if (-not (Test-Path $jarOut)) {
    Write-Host "[test] Plugin jar not found: $jarOut" -ForegroundColor Red
    Write-Host "[test] Run without -NoBuild to build it first." -ForegroundColor Yellow
    exit 1
}
New-Item -ItemType Directory -Path $plugins -Force | Out-Null
Copy-Item -Path $jarOut -Destination (Join-Path $plugins "Werepires1.3.jar") -Force
Write-Host "[test] Deployed plugin to test-server\plugins\." -ForegroundColor Green

if ($BuildOnly) {
    Write-Host "[test] -BuildOnly set; not starting the server." -ForegroundColor Yellow
    exit 0
}

# ── 3. Run ────────────────────────────────────────────────────────────────────
Write-Host "[test] Starting Paper 1.21.10 (type 'stop' in the console to quit)..." -ForegroundColor Cyan
Push-Location $server
try {
    & $java -Xms2G -Xmx4G -jar "paper.jar" nogui
} finally {
    Pop-Location
}
