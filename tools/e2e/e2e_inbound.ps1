$ErrorActionPreference = "Continue"
Set-Location "$env:TEMP\opencode"
$pkg = "it.stivy.rivo.personal.debug"
$cmp = "$pkg/com.grinch.rivo4.sip.SipCredentialReceiver"
$rec = "/sdcard/Android/data/$pkg/files/recordings"
function Act([string]$a) { adb shell am broadcast -n $cmp --es call_action $a | Out-Null }
function Sip([int]$n) { adb logcat -d -s VobizSip 2>$null | Select-Object -Last $n }
function RecSize { adb shell "ls -la $rec 2>/dev/null | tail -3" }

Write-Output "=== [A1] start harness ==="
Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object { $_.CommandLine -like "*local_sip_test*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Remove-Item local-sip.log, local-sip.err -ErrorAction SilentlyContinue
Start-Process python -ArgumentList "local_sip_test.py","3" -RedirectStandardOutput local-sip.log -RedirectStandardError local-sip.err -WindowStyle Hidden
Start-Sleep 1

Write-Output "=== [A2] register ==="
adb logcat -c
$u=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("e2etester"))
$p=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("e2epass123"))
adb shell am broadcast -n $cmp --es username_b64 $u --es password_b64 $p --es domain 10.0.2.2 | Out-Null
Start-Sleep 12
Write-Output "--- sip log after register ---"
Sip 8

Write-Output "=== [A3] answer ==="
Act answer
Start-Sleep 4
Write-Output "--- sip log after answer ---"
Sip 6

Write-Output "=== [A4] record ON ==="
Act record
Start-Sleep 2
Write-Output "--- after record on ---"
Sip 4
Write-Output "--- recordings dir ---"
RecSize

Write-Output "=== [A5] size snapshot 1 ==="
$s1 = adb shell "stat -c %s $rec/*.mkv 2>/dev/null"
Write-Output "s1=$s1"
Start-Sleep 5
$s2 = adb shell "stat -c %s $rec/*.mkv 2>/dev/null"
Write-Output "=== [A6] size snapshot 2 ==="
Write-Output "s2=$s2"

Write-Output "=== [A7] hold then resume ==="
Act hold
Start-Sleep 2
Act hold
Start-Sleep 3
$s3 = adb shell "stat -c %s $rec/*.mkv 2>/dev/null"
Write-Output "s3(after resume)=$s3"
Act state
Start-Sleep 1
Write-Output "--- state dump ---"
adb logcat -d 2>$null | Select-String "Debug call_action=state" | Select-Object -Last 1

Write-Output "=== [A8] record OFF ==="
Act record
Start-Sleep 3
$s4 = adb shell "stat -c %s $rec/*.mkv 2>/dev/null"
Write-Output "s4(after stop)=$s4"
Write-Output "--- record logs ---"
adb logcat -d -s VobizSip 2>$null | Select-String "Recording" | Select-Object -Last 5

Write-Output "=== [A9] end call ==="
Act end
Start-Sleep 4
Write-Output "--- sip log after end ---"
Sip 5
Write-Output "--- history log ---"
adb logcat -d 2>$null | Select-String "Vobiz history recorded" | Select-Object -Last 2
Write-Output "--- final recordings dir ---"
RecSize
Write-Output "--- harness log tail ---"
Get-Content local-sip.log -Tail 8
Write-Output "=== [A done] sizes: s1=$s1 s2=$s2 s3=$s3 s4=$s4 ==="
