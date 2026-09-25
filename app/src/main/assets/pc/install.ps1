$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding $false
$dir = Join-Path $HOME '.claude'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$script = Join-Path $dir 'clawdboard-usage.ps1'
$oldScript = Join-Path $dir 'clawdboard-statusline.ps1'
$settingsPath = Join-Path $dir 'settings.json'
$backup = "$settingsPath.antes-do-clawdboard"

$settings = New-Object PSObject
if (Test-Path $settingsPath) {
    $text = [System.IO.File]::ReadAllText($settingsPath)
    if ($text.Trim()) { $settings = $text | ConvertFrom-Json }
    if (-not (Test-Path $backup)) { Copy-Item $settingsPath $backup }
}

$line = $settings.PSObject.Properties['statusLine']
if ($line -and $line.Value.command -like '*clawdboard-statusline*') {
    $previous = ''
    if (Test-Path $oldScript) {
        $m = [regex]::Match([System.IO.File]::ReadAllText($oldScript), '(?m)^\$previous = ''(.*)''\s*$')
        if ($m.Success) { $previous = $m.Groups[1].Value.Replace("''", "'") }
    }
    if ($previous) {
        $line.Value | Add-Member -NotePropertyName command -NotePropertyValue $previous -Force
    } else {
        $settings.PSObject.Properties.Remove('statusLine')
    }
}
if (Test-Path $oldScript) { Remove-Item $oldScript -Force }

$usage = @'
__USAGE__
'@
[System.IO.File]::WriteAllText($script, $usage, $utf8)

$command = 'powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + ($script -replace '\\', '/') + '"'
if (-not $settings.PSObject.Properties['hooks']) {
    $settings | Add-Member -NotePropertyName hooks -NotePropertyValue (New-Object PSObject)
}
foreach ($event in 'Stop', 'SessionStart') {
    $groups = @()
    if ($settings.hooks.PSObject.Properties[$event]) { $groups = @($settings.hooks.$event) }
    $groups = @($groups | Where-Object { -not ((@($_.hooks) | ForEach-Object { $_.command }) -join ' ' -like '*clawdboard-usage*') })
    $hook = New-Object PSObject -Property @{ type = 'command'; command = $command; timeout = 30 }
    $groups += New-Object PSObject -Property @{ hooks = @($hook) }
    $settings.hooks | Add-Member -NotePropertyName $event -NotePropertyValue $groups -Force
}
[System.IO.File]::WriteAllText($settingsPath, ($settings | ConvertTo-Json -Depth 64), $utf8)

Write-Host '__T_CONNECTED__' -ForegroundColor Green
if (Test-Path $backup) { Write-Host "__T_BACKUP__$backup" }
Write-Host '__T_EVERY__'
