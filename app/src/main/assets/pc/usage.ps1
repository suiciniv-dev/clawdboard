param([switch]$Work)
$ErrorActionPreference = 'SilentlyContinue'
$url = '__URL__'
$key = '__KEY__'
$tmp = [System.IO.Path]::GetTempPath()
$mark = [System.IO.Path]::Combine($tmp, 'clawdboard-uso.txt')

if (-not $Work) {
    [void][Console]::In.ReadToEnd()
    if ([System.IO.File]::Exists($mark) -and [System.IO.File]::GetLastWriteTime($mark) -gt [DateTime]::Now.AddSeconds(-120)) { exit 0 }
    [System.IO.File]::WriteAllText($mark, [DateTime]::Now.ToString('o'))
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = 'powershell.exe'
    $psi.Arguments = '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $PSCommandPath + '" -Work'
    $psi.UseShellExecute = $true
    $psi.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
    [void][System.Diagnostics.Process]::Start($psi)
    exit 0
}

$claude = $null
foreach ($dir in ($env:PATH -split ';')) {
    if (-not $dir) { continue }
    $candidate = [System.IO.Path]::Combine($dir.Trim('"'), 'claude.exe')
    if ([System.IO.File]::Exists($candidate)) { $claude = $candidate; break }
}
if (-not $claude) {
    $ext = [System.IO.Path]::Combine($HOME, '.vscode', 'extensions')
    if ([System.IO.Directory]::Exists($ext)) {
        $claude = [System.IO.Directory]::GetDirectories($ext, 'anthropic.claude-code-*') |
            ForEach-Object { [System.IO.Path]::Combine($_, 'resources', 'native-binary', 'claude.exe') } |
            Where-Object { [System.IO.File]::Exists($_) } |
            Sort-Object { [System.IO.File]::GetLastWriteTime($_) } -Descending |
            Select-Object -First 1
    }
}
if (-not $claude) { exit 0 }

$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = $claude
$psi.Arguments = '-p "/usage" --output-format json --no-session-persistence --settings "{\"disableAllHooks\":true}"'
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$psi.CreateNoWindow = $true
$psi.WorkingDirectory = $tmp
$p = [System.Diagnostics.Process]::Start($psi)
$errors = $p.StandardError.ReadToEndAsync()
$out = $p.StandardOutput.ReadToEnd()
[void]$p.WaitForExit(60000)
$text = ($out | ConvertFrom-Json).result
if (-not $text) { exit 0 }

$culture = [System.Globalization.CultureInfo]::GetCultureInfo('en-US')
function Get-ResetEpoch([string]$s) {
    $s = $s.Trim()
    $now = [DateTime]::Now
    if ($s -match '^in\s') {
        $at = $now
        if ($s -match '(\d+)\s*d') { $at = $at.AddDays([int]$Matches[1]) }
        if ($s -match '(\d+)\s*h') { $at = $at.AddHours([int]$Matches[1]) }
        if ($s -match '(\d+)\s*m') { $at = $at.AddMinutes([int]$Matches[1]) }
        return [DateTimeOffset]::new($at).ToUnixTimeSeconds()
    }
    foreach ($format in 'MMM d, h:mmtt', 'MMM d, htt', 'MMM d, h:mm tt', 'MMM d, h tt', 'h:mmtt', 'htt') {
        $d = [DateTime]::MinValue
        if ([DateTime]::TryParseExact($s, $format, $culture, [System.Globalization.DateTimeStyles]::AllowWhiteSpaces, [ref]$d)) {
            if ($d -lt $now.AddHours(-1)) { $d = if ($format -like 'MMM*') { $d.AddYears(1) } else { $d.AddDays(1) } }
            return [DateTimeOffset]::new($d).ToUnixTimeSeconds()
        }
    }
    return $null
}
function New-Window([string]$pct, [string]$reset) {
    $w = [ordered]@{ used_percentage = [double]::Parse($pct, $culture) }
    $epoch = Get-ResetEpoch $reset
    if ($epoch) { $w.resets_at = $epoch }
    return $w
}

$payload = [ordered]@{}
$m = [regex]::Match($text, 'Current session:\s*([0-9.]+)%\s*used\W+resets\s+([^(\r\n]+)')
if ($m.Success) { $payload.five_hour = New-Window $m.Groups[1].Value $m.Groups[2].Value }
$m = [regex]::Match($text, 'Current week \(all models\):\s*([0-9.]+)%\s*used\W+resets\s+([^(\r\n]+)')
if ($m.Success) { $payload.seven_day = New-Window $m.Groups[1].Value $m.Groups[2].Value }
$scoped = @()
foreach ($m in [regex]::Matches($text, 'Current week \((?!all models)([^)]+)\):\s*([0-9.]+)%\s*used\W+resets\s+([^(\r\n]+)')) {
    $w = New-Window $m.Groups[2].Value $m.Groups[3].Value
    $w.label = $m.Groups[1].Value.Trim()
    $scoped += $w
}
if ($scoped.Count -gt 0) { $payload.scoped = $scoped }
if ($payload.Count -eq 0) { exit 0 }

try {
    $json = $payload | ConvertTo-Json -Compress -Depth 5
    $req = [System.Net.HttpWebRequest]::Create("$url/api/push")
    $req.Method = 'POST'
    $req.Proxy = $null
    $req.Timeout = 5000
    $req.ContentType = 'application/json'
    $req.Headers.Add('X-Clawdboard', '1')
    $req.Headers.Add('X-Clawdboard-Key', $key)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $req.ContentLength = $bytes.Length
    $stream = $req.GetRequestStream()
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Close()
    $req.GetResponse().Close()
} catch {
}
