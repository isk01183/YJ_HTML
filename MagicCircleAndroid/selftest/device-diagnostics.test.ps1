$ErrorActionPreference = 'Stop'
$adbCalls = [System.Collections.Generic.List[string]]::new()
function Test-Adb {
    $command = $args -join ' '
    $adbCalls.Add($command)
    if ($command -match 'shell (input|cmd)|logcat -c') {
        throw "Diagnostics changed device state: $command"
    }
    switch -Wildcard ($command) {
        'get-state' { 'device' }
        '*enabled_accessibility_services*' { 'com.yj.magiccircle/.ChargingAccessibilityService' }
        '*dumpsys accessibility*' { 'Bound services: com.yj.magiccircle/.ChargingAccessibilityService' }
        '*dumpsys power*' { 'mWakefulness=Awake' }
        '*dumpsys battery*' { 'USB powered: true' }
        '*dumpsys window windows*' { "  Window #0 Window{123 com.yj.magiccircle}:`n    ty=ACCESSIBILITY_OVERLAY" }
        '*dumpsys package com.yj.magiccircle*' { 'versionName=1.9' }
        'logcat *' { 'run=2 Event CONNECT'; 'run=2 Animation running=true'; 'run=2 Visual callback ready' }
    }
}
$result = (& "$PSScriptRoot\device-charging-overlay.ps1" -Adb Test-Adb -DiagnosticsOnly) -join "`n"
foreach ($expected in @('Bound services:', 'mWakefulness=Awake', 'USB powered: true',
        'ACCESSIBILITY_OVERLAY', 'Animation running=true', 'versionName=1.9')) {
    if (!$result.Contains($expected)) { throw "Missing diagnostic evidence: $expected" }
}
if ($adbCalls.Count -lt 6) { throw 'Diagnostics did not inspect the component boundaries.' }
Write-Output 'DEVICE_DIAGNOSTICS_OK: captures component state without input, battery mutation, or log clearing'
