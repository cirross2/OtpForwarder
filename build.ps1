$env:JAVA_HOME='E:\다운로드\jdk-17.0.20.1+1'
$env:ANDROID_HOME='E:\다운로드\android-sdk'
$env:ANDROID_SDK_ROOT='E:\다운로드\android-sdk'
$env:GRADLE_USER_HOME='E:\다운로드\.gradle'
Set-Location $PSScriptRoot
& '.\gradlew.bat' assembleDebug --no-daemon --console=plain
Write-Host "`nAPK: app\build\outputs\apk\debug\app-debug.apk"
