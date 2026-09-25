$ErrorActionPreference = "Stop"
$env:GRADLE_USER_HOME = "D:\Android\gradle-home"
$env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-17.0.14"
Set-Location (Join-Path $PSScriptRoot "..")
& .\gradlew.bat testReleaseUnitTest assembleRelease
if ($LASTEXITCODE -ne 0) { throw "Build falhou" }
Write-Host "APK: $(Resolve-Path app\build\outputs\apk\release\app-release.apk)"
