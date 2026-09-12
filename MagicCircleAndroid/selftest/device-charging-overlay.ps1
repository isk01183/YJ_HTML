param(
    [string]$Adb = "adb"
)

$ErrorActionPreference = "Stop"

if ((& $Adb get-state) -ne "device") { throw "ADB device is not authorized." }
if ((& $Adb shell settings get secure enabled_accessibility_services) -notmatch "com\.yj\.magiccircle") {
    throw "MagicCircle accessibility service is not enabled."
}

try {
    & $Adb shell cmd battery unplug -f | Out-Null
    Start-Sleep -Milliseconds 500
    & $Adb shell cmd battery reset -f | Out-Null
    Start-Sleep -Milliseconds 1200

    $dump = (& $Adb shell dumpsys window windows) -join "`n"
    $overlay = [regex]::Matches($dump, '(?ms)^  Window #\d+ Window\{[^\r\n]*com\.yj\.magiccircle.*?(?=^  Window #|\z)') |
        Where-Object { $_.Value -match 'ty=2032' } |
        Select-Object -First 1

    if (-not $overlay) { throw "Charging accessibility overlay was not created." }
    if ($overlay.Value -notmatch 'mViewVisibility=0x0') { throw "Charging overlay is not VISIBLE." }
    if ($overlay.Value -notmatch 'mHasSurface=true') { throw "Charging overlay has no drawable surface." }

    Write-Output "DEVICE_OVERLAY_READY"
} finally {
    & $Adb shell cmd battery reset -f | Out-Null
}
