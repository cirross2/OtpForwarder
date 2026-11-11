param([string]$ip)
$ErrorActionPreference='Continue'
$scrcpyDir='E:\다운로드\scrcpy\scrcpy-win64-v5.0.1'
$adb=Join-Path $scrcpyDir 'adb.exe'
$scrcpy=Join-Path $scrcpyDir 'scrcpy.exe'
$comp='com.boss.otpforwarder/.ControlReceiver'
$sel=@()
if($ip){ & $adb connect "${ip}:5555" | Out-Null; $sel=@('-s',"${ip}:5555") }
& $adb @sel shell am broadcast -n $comp --es cmd pause | Out-Null
Write-Host '>> forwarding PAUSED. starting mirror (close window to resume)...' -ForegroundColor Yellow
try {
  & $scrcpy @sel --stay-awake --window-title 'Second Phone'
} finally {
  & $adb @sel shell am broadcast -n $comp --es cmd resume | Out-Null
  Write-Host '>> mirror closed. forwarding RESUMED.' -ForegroundColor Green
}
