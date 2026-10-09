$ErrorActionPreference = "Continue"
Set-Location "$env:TEMP\opencode"
$pkg = "it.stivy.rivo.personal.debug"
$cmp = "$pkg/com.grinch.rivo4.sip.SipCredentialReceiver"
$rec = "/sdcard/Android/data/$pkg/files/recordings"
function Act([string]$a) { adb shell am broadcast -n $cmp --es call_action $a | Out-Null }
function Sip([int]$n) { adb logcat -d -s VobizSip 2>$null | Select-Object -Last $n }

Write-Output "=== [B1] start harness (no auto inbound) ==="
Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object { $_.CommandLine -like "*local_sip_test*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Start-Sleep 1
Remove-Item local-sip.log, local-sip.err -ErrorAction SilentlyContinue
Start-Process python -ArgumentList "local_sip_test.py","800" -RedirectStandardOutput local-sip.log -RedirectStandardError local-sip.err -WindowStyle Hidden
Start-Sleep 1

Write-Output "=== [B2] register ==="
adb logcat -c
$u=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("e2etester"))
$p=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("e2epass123"))
adb shell am broadcast -n $cmp --es username_b64 $u --es password_b64 $p --es domain 10.0.2.2 | Out-Null
Start-Sleep 6
Write-Output "--- role check ---"
adb shell cmd role get-role-holders android.app.role_dialer 2>$null
Write-Output "--- register log ---"
Sip 3

Write-Output "=== [B3] place outbound call ==="
adb logcat -c
adb shell am broadcast -n $cmp --es call_action place --es number 15001 | Out-Null
Start-Sleep 5
Write-Output "--- invite log ---"
adb logcat -d -s VobizSip 2>$null | Select-String "invite placed|invite failed|place-failed|placed|Call event|Call state" | Select-Object -Last 8
Write-Output "--- harness ---"
Get-Content local-sip.log -Tail 4

Write-Output "=== [B4] record ON, verify growth ==="
Act record
Start-Sleep 2
Sip 3
$before = adb shell "ls $rec 2>/dev/null"
Write-Output "files before: $before"
Start-Sleep 5
$after = adb shell "ls $rec 2>/dev/null"
Write-Output "files after: $after"
$sizes = adb shell "stat -c '%n %s' $rec/*.mkv 2>/dev/null"
Write-Output "sizes: $sizes"

Write-Output "=== [B5] record OFF + end ==="
Act record
Start-Sleep 2
Act end
Start-Sleep 4
Write-Output "--- final sip log ---"
Sip 6
Write-Output "--- history ---"
adb logcat -d 2>$null | Select-String "Vobiz history recorded" | Select-Object -Last 2
Write-Output "--- harness tail ---"
Get-Content local-sip.log -Tail 6
Write-Output "=== [B done] ==="
exit 0
