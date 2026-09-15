param(
    [string]$Adb = "adb",
    [ValidateRange(1, 10)][int]$Cycles = 3,
    [switch]$ScreenOff,
    [switch]$DiagnosticsOnly
)

$ErrorActionPreference = "Stop"

if ((& $Adb get-state) -ne "device") { throw "ADB device is not authorized." }

function Get-ChargingOverlay {
    $dump = (& $Adb shell dumpsys window windows) -join "`n"
    [regex]::Matches($dump, '(?ms)^  Window #\d+ Window\{[^\r\n]*com\.yj\.magiccircle.*?(?=^  Window #|\z)') |
        Where-Object { $_.Value -match 'ty=(2032|ACCESSIBILITY_OVERLAY)\b' } |
        Select-Object -First 1
}

function Write-ChargingDiagnostics {
    Write-Output 'PACKAGE'
    & $Adb shell dumpsys package com.yj.magiccircle |
        Select-String 'versionName=|versionCode=|stopped=' | ForEach-Object { $_.Line }
    Write-Output 'ACCESSIBILITY'
    & $Adb shell settings get secure enabled_accessibility_services
    & $Adb shell dumpsys accessibility | Select-String 'com\.yj\.magiccircle' |
        ForEach-Object { $_.Line }
    Write-Output 'POWER'
    & $Adb shell dumpsys power | Select-String 'mWakefulness=|mIsPowered=|Display Power:' |
        ForEach-Object { $_.Line }
    Write-Output 'BATTERY'
    & $Adb shell dumpsys battery
    Write-Output 'OVERLAY'
    $current = Get-ChargingOverlay
    if ($current) { $current.Value } else { 'No active charging overlay' }
    Write-Output 'CHARGING LOG'
    & $Adb logcat -d -t 200 -v time -s MagicCircleCharging:D '*:S'
}

if ($DiagnosticsOnly) {
    Write-ChargingDiagnostics
    return
}
if ((& $Adb shell settings get secure enabled_accessibility_services) -notmatch "com\.yj\.magiccircle") {
    Write-ChargingDiagnostics
    throw "MagicCircle accessibility service is not enabled."
}

try {
    # Do not launch the app or UIAutomator: that can hide background-only failures.
    & $Adb shell input keyevent KEYCODE_HOME
    for ($cycle = 1; $cycle -le $Cycles; $cycle++) {
        & $Adb shell cmd battery unplug -f | Out-Null
        if ($ScreenOff) { & $Adb shell input keyevent KEYCODE_SLEEP }
        else { & $Adb shell input keyevent KEYCODE_WAKEUP }
        Start-Sleep -Milliseconds 1500
        if ($ScreenOff -and ((& $Adb shell dumpsys power) -join "`n") -notmatch 'mWakefulness=(Asleep|Dozing)') {
            throw "Cycle ${cycle}: screen-off precondition failed."
        }
        $since = [long]((& $Adb shell date +%s) -join '').Trim()
        $timer = [System.Diagnostics.Stopwatch]::StartNew()
        & $Adb shell cmd battery reset -f | Out-Null
        Start-Sleep -Milliseconds 1500

        $power = (& $Adb shell dumpsys power) -join "`n"
        if ($power -notmatch 'mWakefulness=Awake') {
            throw "Cycle ${cycle}: display stayed asleep after charging connection."
        }
        $overlay = Get-ChargingOverlay
        if (-not $overlay) { throw "Cycle ${cycle}: charging overlay was not created." }
        if ($overlay.Value -notmatch 'mViewVisibility=0x0') { throw "Overlay is not VISIBLE." }
        if ($overlay.Value -notmatch 'mHasSurface=true') { throw "Overlay has no drawable surface." }
        do {
            $log = ((& $Adb logcat -d -v epoch -s MagicCircleCharging:D '*:S') | Where-Object {
                $_ -match '^\s*(\d+)\.\d+' -and [long]$Matches[1] -ge $since
            }) -join "`n"
            if ($log -match 'Animation running=true' -and $log -match 'Visual callback ready') { break }
            Start-Sleep -Milliseconds 250
        } while ($timer.ElapsedMilliseconds -lt 5500)
        if ($log -notmatch 'Event CONNECT' -or $log -notmatch 'Animation running=true') {
            throw "Cycle ${cycle}: no confirmed JavaScript animation start. $log"
        }
        if ($log -notmatch 'Visual callback ready') {
            throw "Cycle ${cycle}: JavaScript started, but rendering remains unconfirmed. $log"
        }

        # Allow 0.5 seconds for ADB transport and the window-removal transaction.
        Start-Sleep -Milliseconds ([Math]::Max(1, 7500 - $timer.ElapsedMilliseconds))
        if (Get-ChargingOverlay) { throw "Cycle ${cycle}: overlay did not close after 7 seconds." }
        Write-Output "BACKGROUND_REPLAY_OK cycle=$cycle screenOff=$ScreenOff"
    }

    # Also reconnect before a previous animation finishes, then disconnect early.
    & $Adb shell cmd battery unplug -f | Out-Null
    & $Adb shell cmd battery reset -f | Out-Null
    Start-Sleep -Milliseconds 1200
    & $Adb shell cmd battery unplug -f | Out-Null
    Start-Sleep -Milliseconds 300
    if (Get-ChargingOverlay) { throw "Overlay remained after disconnect." }
    & $Adb shell cmd battery reset -f | Out-Null
    Start-Sleep -Milliseconds 1500
    if (-not (Get-ChargingOverlay)) { throw "Rapid reconnect did not create a fresh overlay." }
    & $Adb shell cmd battery unplug -f | Out-Null
    Start-Sleep -Milliseconds 300
    if (Get-ChargingOverlay) { throw "Overlay remained after final disconnect." }
    Write-Output "DEVICE_OVERLAY_READY"
} catch {
    Write-ChargingDiagnostics
    throw
} finally {
    & $Adb shell cmd battery reset -f | Out-Null
}
