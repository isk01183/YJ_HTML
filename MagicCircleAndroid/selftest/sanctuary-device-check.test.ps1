$ErrorActionPreference = 'Stop'
$global:LASTEXITCODE = 0
$calls = [Collections.Generic.List[string]]::new()
$deviceCount = 1
$version = '1.12'
$crash = ''
$gfx = 'Total frames rendered: 100'
function FakeAdb {
    $command = $args -join ' '
    $calls.Add($command)
    switch -Wildcard ($command) {
        'devices -l' { 'List of devices attached'; 1..$deviceCount | ForEach-Object { "device$_ device model:TEST" } }
        '*dumpsys package*' { "versionName=$version"; 'versionCode=15' }
        '*pidof*' { '123' }
        '*logcat*' { $crash }
        '*gfxinfo*' { $gfx }
        '*meminfo*' { 'TOTAL PSS: 10000' }
        default { throw "Unexpected device mutation/query: $command" }
    }
}
$result = (& "$PSScriptRoot/sanctuary-device-check.ps1" -Adb FakeAdb) -join "`n"
foreach ($expected in @('1.12', '15', '123', '100', '10000')) {
    if (!$result.Contains($expected)) { throw "Missing evidence: $expected" }
}
if ($calls.Where({ $_ -ne 'devices -l' -and $_ -notlike '-s device1 *' }).Count) { throw 'Device was not explicitly selected' }
$deviceCount = 2
$rejected = $false
try { & "$PSScriptRoot/sanctuary-device-check.ps1" -Adb FakeAdb } catch { $rejected = $_ -match 'exactly one' }
if (!$rejected) { throw 'Multiple devices must be rejected' }
$deviceCount = 1
$version = '1.11'
$rejected = $false
try { & "$PSScriptRoot/sanctuary-device-check.ps1" -Adb FakeAdb } catch { $rejected = $_ -match 'version differs' }
if (!$rejected) { throw 'Stale installed version must be rejected' }
$version = '1.12'
$crash = 'FATAL EXCEPTION: main'
$rejected = $false
try { & "$PSScriptRoot/sanctuary-device-check.ps1" -Adb FakeAdb } catch { $rejected = $_ -match 'App crash detected' }
if (!$rejected) { throw 'Current app crash must be rejected' }
$crash = ''
$gfx = 'Failure while dumping the app'
$rejected = $false
try { & "$PSScriptRoot/sanctuary-device-check.ps1" -Adb FakeAdb } catch { $rejected = $_ -match 'Graphics statistics unavailable' }
if (!$rejected) { throw 'Unavailable graphics must not look like a performance pass' }
'SANCTUARY_DEVICE_CHECK_OK'
