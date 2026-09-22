param([string]$Adb = 'adb', [string]$ExpectedVersion = '1.13')
$ErrorActionPreference = 'Stop'
$devices = @((& $Adb devices -l) | Where-Object { $_ -match '^\S+\s+device\b' })
if ($LASTEXITCODE -ne 0 -or $devices.Count -ne 1) { throw 'Connect exactly one authorized device.' }
$serial = ($devices[0] -split '\s+')[0]
function Read-Device {
    $result = & $Adb -s $serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB query failed: $($args -join ' ')" }
    $result
}
$package = (Read-Device shell dumpsys package com.yj.magiccircle) -join "`n"
$package -split "`n" | Select-String 'versionName=|versionCode=' | ForEach-Object { $_.Line.Trim() }
if ($package -notmatch ('versionName=' + [regex]::Escape($ExpectedVersion) + '(\s|$)')) { throw 'Installed version differs from expected version.' }
$appPid = ((Read-Device shell pidof com.yj.magiccircle) -join '').Trim()
if (!$appPid) { throw 'App process is not running; open the app manually.' }
"PID=$appPid"
$crashes = (Read-Device logcat -d -b crash --pid=$appPid -v brief) -join "`n"
if ($crashes -match 'FATAL EXCEPTION|Fatal signal') { throw "App crash detected: $crashes" }
'No crash for current PID in crash buffer.'
$graphics = (Read-Device shell dumpsys gfxinfo com.yj.magiccircle) -join "`n"
$graphics
Read-Device shell dumpsys meminfo com.yj.magiccircle
if ($graphics -match 'Failure while dumping' -or $graphics -notmatch 'Total frames rendered: [1-9]') {
    throw 'Graphics statistics unavailable; show the app and repeat. No performance pass recorded.'
}
