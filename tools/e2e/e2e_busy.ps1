$ErrorActionPreference = "Continue"
Set-Location "$env:TEMP\opencode"
$pkg = "it.stivy.rivo.personal.debug"
$cmp = "$pkg/com.grinch.rivo4.sip.SipCredentialReceiver"

Write-Output "=== [BUSY1] harness (no auto-inbound, busy INVITE 3s after ACK) ==="
Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object { $_.CommandLine -like "*local_sip_test*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Start-Sleep 1
Remove-Item local-sip.log, local-sip.err -ErrorAction SilentlyContinue
Start-Process python -ArgumentList "local_sip_test.py","86400","3" -RedirectStandardOutput local-sip.log -RedirectStandardError local-sip.err -WindowStyle Hidden
Start-Sleep 1
adb logcat -c

Write-Output "=== [BUSY2] register + place call ==="
$u=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("e2etester"))
$p=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("e2epass123"))
adb shell am broadcast -n $cmp --es username_b64 $u --es password_b64 $p --es domain 10.0.2.2 | Out-Null
Start-Sleep 6
adb shell am broadcast -n $cmp --es call_action place --es number 15001 | Out-Null

Write-Output "=== [BUSY3] wait for call active + 2nd INVITE + 486 ==="
$okAck = $false; $ok486 = $false
for ($i=0; $i -lt 30; $i++) {
  Start-Sleep 1
  $log = Get-Content local-sip.log -Raw -ErrorAction SilentlyContinue
  if (-not $okAck -and $log -match "ACK received") { $okAck = $true; Write-Output "call active (t=$i s)" }
  if ($okAck -and $log -match "486") { $ok486 = $true; Write-Output "got 486 (t=$i s)"; break }
}
if (-not $okAck) { Write-Output "FAIL: call never became active" }
if (-not $ok486) { Write-Output "FAIL: no 486 response" }

Write-Output "=== [BUSY4] logcat assertions ==="
$busy = adb logcat -d 2>$null | Select-String "declining busy" | Select-Object -First 1
$active = adb logcat -d 2>$null | Select-String "call_action=state" | Select-Object -Last 1
if ($busy) { Write-Output "PASS: $($busy.Line.Trim())" } else { Write-Output "FAIL: no 'declining busy' in logcat" }
Get-Content local-sip.log | Select-String "486|second INVITE|REFER" | ForEach-Object { $_.Line }

Write-Output "=== [BUSY5] cleanup: end call ==="
adb shell am broadcast -n $cmp --es call_action end | Out-Null
Start-Sleep 2
Write-Output "=== [BUSY done] ack=$okAck busy486=$ok486 ==="
exit 0
