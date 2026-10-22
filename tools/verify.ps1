# Lucento local verification (Windows PowerShell). Mirrors the CI pipeline so results can be reported honestly.
# Usage (from the repository root):  powershell -ExecutionPolicy Bypass -File tools\verify.ps1
# Requires JDK 17 on PATH (or JAVA_HOME) and the Android SDK (ANDROID_HOME or local.properties sdk.dir).

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force -Path "build" | Out-Null
$summary = "build\verify-summary.txt"
"Lucento verification $(Get-Date -Format o)" | Out-File $summary -Encoding utf8
"commit: $(git rev-parse --short HEAD) $(if (git status --porcelain) { '(uncommitted changes)' })" | Out-File $summary -Append -Encoding utf8

function Step($name, [scriptblock]$block) {
    Write-Host "`n=== $name ===" -ForegroundColor Cyan
    & $block
    $ok = ($LASTEXITCODE -eq 0)
    $line = "{0,-48} {1}" -f $name, $(if ($ok) { "PASSED" } else { "FAILED (exit $LASTEXITCODE)" })
    $line | Out-File $summary -Append -Encoding utf8
    Write-Host $line -ForegroundColor $(if ($ok) { "Green" } else { "Red" })
}

Step "JVM unit tests" { .\gradlew.bat --stacktrace testDebugUnitTest }
Step "Debug build (Compose + KSP + schema export)" { .\gradlew.bat --stacktrace assembleDebug }
Step "Room schema unchanged vs committed" { git diff --exit-code -- app/schemas }
Step "Offline migration check" { python tools\check_migrations.py }
Step "Lint (errors block)" { .\gradlew.bat --stacktrace lintDebug }
Step "Release build + App Bundle (R8)" { .\gradlew.bat --stacktrace assembleRelease bundleRelease }

$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" } else { "adb" }
$devices = & $adb devices 2>$null | Select-String -Pattern "\tdevice$"
if ($devices) {
    Step "Instrumented migration tests" { .\gradlew.bat --stacktrace connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.lucento.app.data.local.MigrationTest" }
} else {
    "Instrumented migration tests                     NOT RUN (no device/emulator connected)" | Out-File $summary -Append -Encoding utf8
}

Get-ChildItem app\build\outputs -Recurse -Include *.apk, *.aab -ErrorAction SilentlyContinue |
    ForEach-Object { "{0,-60} {1,8:N0} KB" -f $_.Name, ($_.Length / 1KB) } | Out-File $summary -Append -Encoding utf8
Write-Host "`nSummary written to $summary" -ForegroundColor Cyan
Get-Content $summary
