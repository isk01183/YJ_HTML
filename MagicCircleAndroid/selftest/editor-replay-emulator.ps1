param([Parameter(Mandatory)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial,
    [Parameter(Mandatory)][string]$Adb,[ValidateSet(1000,3000,5000,7000)][int]$Duration=7000,
    [ValidateRange(1,10)][int]$Cycles=3)
$ErrorActionPreference='Stop'
function Invoke-Adb { $result=& $Adb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"}; return $result }
if((Invoke-Adb get-state) -ne 'device'){throw 'Emulator unavailable'}
if(((Invoke-Adb emu avd name) -join '') -notmatch 'MagicCircle_(Phone|Tablet)_API36'){throw 'Not a verified project AVD'}
if(((Invoke-Adb shell settings get secure enabled_accessibility_services) -join '') -notmatch 'com.yj.magiccircle'){throw 'Enable accessibility first'}
try {
 Invoke-Adb shell input keyevent KEYCODE_HOME | Out-Null
 for($i=0;$i -lt $Cycles;$i++) {
  Invoke-Adb shell cmd battery unplug -f | Out-Null
  Start-Sleep -Milliseconds 400
  Invoke-Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
  $since=[long](((Invoke-Adb shell date '+%s') -join '').Trim())
  Invoke-Adb shell cmd battery set usb 1 -f | Out-Null
  Start-Sleep -Milliseconds ($Duration+2600)
  $log=((Invoke-Adb logcat -d -v epoch -s MagicCircleCharging:D '*:S') | Where-Object {$_ -match '^\s*(\d+)\.\d+' -and [long]$Matches[1] -ge $since}) -join "`n"
  if($log -notmatch "Started duration=$Duration"){throw "No start: $log"}
  $matchesDone=[regex]::Matches($log,'Dismiss reason=deadline elapsed=\d+ visibleMs=(\d+)')
  if(!$matchesDone.Count){throw "No completion: $log"}
  $elapsed=[int]$matchesDone[-1].Groups[1].Value
  if($elapsed -lt $Duration-30 -or $elapsed -gt $Duration+300){throw "Wrong duration: $elapsed"}
  Write-Output "REPLAY_OK serial=$Serial cycle=$($i+1) duration=$Duration visible=$elapsed"
 }
 Invoke-Adb shell cmd battery unplug -f | Out-Null
 Start-Sleep -Milliseconds 300
 $since=[long](((Invoke-Adb shell date '+%s') -join '').Trim())
 Invoke-Adb shell cmd battery set usb 1 -f | Out-Null
 Start-Sleep -Milliseconds 500
 $removal=[Diagnostics.Stopwatch]::StartNew()
 Invoke-Adb shell cmd battery unplug -f | Out-Null
 # Battery shell returns before the asynchronous power broadcast/window transaction finishes.
 do {
  Start-Sleep -Milliseconds 80
  $windows=(Invoke-Adb shell dumpsys window windows) -join "`n"
  $active=[regex]::Matches($windows,'(?ms)^  Window #\d+ Window\{[^\r\n]*com\.yj\.magiccircle.*?(?=^  Window #|\z)') | Where-Object {$_.Value -match 'ty=(2032|ACCESSIBILITY_OVERLAY)\b' -and $_.Value -match 'mHasSurface=true'}
 } while($active -and $removal.ElapsedMilliseconds -lt 1000)
 if($active){throw "Overlay survived disconnect: $($active.Value -join '\n')"}
 $removedMs=$removal.ElapsedMilliseconds
 $log=((Invoke-Adb logcat -d -v epoch -s MagicCircleCharging:D '*:S') | Where-Object {$_ -match '^\s*(\d+)\.\d+' -and [long]$Matches[1] -ge $since}) -join "`n"
 if($log -notmatch 'Dismiss reason=disconnect'){throw "Disconnect was not confirmed: $log"}
 Write-Output "DISCONNECT_OK windowRemovedMs=$removedMs"
} finally {Invoke-Adb shell cmd battery reset -f | Out-Null}
