param([string]$Ip = "10.0.0.132", [int]$Porta = 5555)
$ErrorActionPreference = "Stop"
$adb = "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
$alvo = "${Ip}:${Porta}"
$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\release\app-release.apk"

& $adb connect $alvo | Out-Host
& $adb -s $alvo install -r $apk | Out-Host
& $adb -s $alvo shell appops set dev.clawdboard SYSTEM_ALERT_WINDOW allow
& $adb -s $alvo shell dumpsys deviceidle whitelist +dev.clawdboard | Out-Host
& $adb -s $alvo shell cmd appops set dev.clawdboard RUN_ANY_IN_BACKGROUND allow
& $adb -s $alvo shell am start -n dev.clawdboard/.MainActivity | Out-Host
Write-Host "Pronto. Painel: http://${Ip}:8080"
