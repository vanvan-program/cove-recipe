param([switch]$Offline, [switch]$DeviceTests)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if (Test-Path -LiteralPath (Join-Path $taskRoot '.tools\sdk\platforms\android-36')) { $env:ANDROID_HOME = Join-Path $taskRoot '.tools\sdk' }
$env:GRADLE_USER_HOME = Join-Path $taskRoot '.tools\gradle-home'
$taskGradle = Join-Path $taskRoot '.tools\gradle-8.11.1\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $taskGradle)) { $taskGradle = Join-Path $taskRoot 'gradlew.bat' }
$taskArguments = @('-p', $taskRoot, ':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', '--console', 'plain')
if ($Offline) { $taskArguments += '--offline' }
if ($DeviceTests) { $taskArguments += ':app:assembleDebugAndroidTest' }
& $taskGradle @taskArguments
if ($LASTEXITCODE -ne 0) { throw '构建或验证失败。' }
if ($DeviceTests) {
    $taskAdb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    $taskEmulators = @(& $taskAdb devices | ForEach-Object { if ($_ -match '^(emulator-\d+)\s+device$') { $Matches[1] } })
    if ($taskEmulators.Count -ne 1) { throw '设备测试会清空测试数据，请启动且只保留一个专用模拟器。脚本不会在个人真机上运行测试。' }
    $taskSerial = $taskEmulators[0]
    & $taskAdb -s $taskSerial install -r (Join-Path $taskRoot 'app\build\outputs\apk\debug\app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw '应用安装失败。' }
    & $taskAdb -s $taskSerial install -r (Join-Path $taskRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
    if ($LASTEXITCODE -ne 0) { throw '测试包安装失败。' }
    $taskOutput = & $taskAdb -s $taskSerial shell am instrument -w cn.myrecipe.app.test/androidx.test.runner.AndroidJUnitRunner
    $taskOutput | Tee-Object -FilePath (Join-Path $taskRoot 'docs\设备测试结果.txt') | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($taskOutput -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($taskOutput -join "`n") -match 'FAILURES!!!') { throw '设备测试失败，请查看 docs/设备测试结果.txt。' }
}
$taskDist = Join-Path $taskRoot 'dist'
New-Item -ItemType Directory -Force $taskDist | Out-Null
Copy-Item -LiteralPath (Join-Path $taskRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination (Join-Path $taskDist 'CoveRecipe-1.4.0.apk') -Force
Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskDist 'CoveRecipe-1.4.0.apk') | Format-List
