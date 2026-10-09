$ErrorActionPreference = "Continue"
Set-Location "$env:TEMP\opencode"
$pkg = "it.stivy.rivo.personal.debug"
$cmp = "$pkg/com.grinch.rivo4.sip.SipCredentialReceiver"
function Shot([string]$name) {
  adb shell screencap -p /sdcard/shot.png
  adb pull /sdcard/shot.png "$env:TEMP\opencode\$name.png" | Out-Null
  Add-Type -AssemblyName System.Drawing
  $img = [System.Drawing.Image]::FromFile("$env:TEMP\opencode\$name.png")
  $bmp = New-Object System.Drawing.Bitmap 360, 640
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.DrawImage($img, 0, 0, 360, 640)
  $bmp.Save("$env:TEMP\opencode\$name.jpg", [System.Drawing.Imaging.ImageFormat]::Jpeg)
  $g.Dispose(); $bmp.Dispose(); $img.Dispose()
}
function Dump() {
  adb shell uiautomator dump /sdcard/ui.xml 2>$null | Out-Null
  adb pull /sdcard/ui.xml "$env:TEMP\opencode\ui.xml" 2>$null | Out-Null
  return Get-Content "$env:TEMP\opencode\ui.xml" -Raw -ErrorAction SilentlyContinue
}
function BoundsOf($xml, $attr, $val) {
  $m = [regex]::Matches($xml, "$attr=`"([^`"]*)`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"") | Where-Object { $_.Groups[1].Value -eq $val } | Select-Object -First 1
  if (-not $m) { $m = [regex]::Matches($xml, "bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"[^>]*$attr=`"([^`"]*)`"") | Where-Object { $_.Groups[5].Value -eq $val } | Select-Object -First 1; if ($m) { return @([int]$m.Groups[2].Value,[int]$m.Groups[3].Value,[int]$m.Groups[4].Value,[int]$m.Groups[5].Value) } ; return $null }
  return @([int]$m.Groups[2].Value,[int]$m.Groups[3].Value,[int]$m.Groups[4].Value,[int]$m.Groups[5].Value)
}
function TapBounds($b) { if ($b) { $x=[int](($b[0]+$b[2])/2); $y=[int](($b[1]+$b[3])/2); adb shell input tap $x $y | Out-Null; return "$x,$y" } return $null }

Write-Output "=== [T1] harness + register + place ==="
Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object { $_.CommandLine -like "*local_sip_test*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Start-Sleep 1
Remove-Item local-sip.log, local-sip.err -ErrorAction SilentlyContinue
Start-Process python -ArgumentList "local_sip_test.py","86400","0" -RedirectStandardOutput local-sip.log -RedirectStandardError local-sip.err -WindowStyle Hidden
Start-Sleep 1
adb logcat -c
$u=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("e2etester"))
$p=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("e2epass123"))
adb shell am broadcast -n $cmp --es username_b64 $u --es password_b64 $p --es domain 10.0.2.2 | Out-Null
Start-Sleep 6
adb shell am broadcast -n $cmp --es call_action place --es number 15001 | Out-Null
$okAck = $false
for ($i=0; $i -lt 25; $i++) { Start-Sleep 1; if ((Get-Content local-sip.log -Raw -ErrorAction SilentlyContinue) -match "ACK received") { $okAck = $true; break } }
if (-not $okAck) { Write-Output "FAIL: call not active"; exit 1 }
Write-Output "call active"
Start-Sleep 2

Write-Output "=== [T2] open transfer dialog ==="
adb shell input tap 360 928 | Out-Null
Start-Sleep 1.5
$xml = Dump
$dlgOpen = $xml -match "Transfer call"
if (-not $dlgOpen) {
  Write-Output "dialog not open after tap 1 - retrying"
  adb shell input tap 360 928 | Out-Null
  Start-Sleep 1.5
  $xml = Dump
  $dlgOpen = $xml -match "Transfer call"
}
Write-Output "dialog_open=$dlgOpen"
Shot "t2_dialog"

Write-Output "=== [T3] fill destination + confirm ==="
if ($dlgOpen) {
  $fb = BoundsOf $xml "text" "Destination number"
  if (-not $fb) { $fb = BoundsOf $xml "class" "android.widget.EditText" }
  if (-not $fb) { $fb = BoundsOf $xml "resource-id" "android:id/editableInput" }
  $tap1 = TapBounds $fb; Write-Output "field tap at $tap1"
  Start-Sleep 1
  adb shell input text "15002"
  Start-Sleep 1
  $xml2 = Dump
  Shot "t3_filled"
  $cb = BoundsOf $xml2 "text" "Transfer"
  $tap2 = TapBounds $cb; Write-Output "confirm tap at $tap2"
  Start-Sleep 2
  Shot "t4_after"
} else {
  Write-Output "FAIL: dialog never opened"
}

Write-Output "=== [T4] assertions ==="
$log = adb logcat -d 2>$null
$transfer = $log | Select-String "Blind transfer to .*ret=0" | Select-Object -First 1
if ($transfer) { Write-Output "PASS app: $($transfer.Line.Trim())" } else { Write-Output "FAIL app: no ret=0"; ($log | Select-String "Blind transfer|transferCall" | Select-Object -First 5 | ForEach-Object { $_.Line }) }
Start-Sleep 2
$hlog = Get-Content local-sip.log -Raw -ErrorAction SilentlyContinue
if ($hlog -match "REFER received -> transferring to sip:15002@10\.0\.2\.2") { Write-Output "PASS harness: REFER with target sip:15002@10.0.2.2" }
elseif ($hlog -match "REFER received") { Write-Output "PARTIAL harness: REFER seen"; (Get-Content local-sip.log | Select-String "REFER" | ForEach-Object { $_.Line }) }
else { Write-Output "FAIL harness: no REFER" }

Write-Output "=== [T5] cleanup ==="
adb shell am broadcast -n $cmp --es call_action end | Out-Null
Start-Sleep 2
Write-Output "=== [T done] ==="
exit 0
