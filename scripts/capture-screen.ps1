param([string]$Serial = 'emulator-5554', [string]$Name = 'screen')
$ErrorActionPreference = 'Stop'
if ($Name -notmatch '^[A-Za-z0-9_-]+$') { throw '截图名称只能包含字母、数字、下划线或连字符。' }
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskAdb = Join-Path $taskRoot '.tools\sdk\platform-tools\adb.exe'
$taskOutput = Join-Path $taskRoot 'docs\screenshots'
New-Item -ItemType Directory -Force $taskOutput | Out-Null
& $taskAdb -s $Serial shell uiautomator dump /sdcard/myrecipe-capture-ui.xml | Out-Null
& $taskAdb -s $Serial shell screencap -p /sdcard/myrecipe-qa.png
if ($LASTEXITCODE -ne 0) { throw '截图失败。' }
& $taskAdb -s $Serial pull /sdcard/myrecipe-qa.png (Join-Path $taskOutput "$Name.png")
if ($LASTEXITCODE -ne 0) { throw '读取截图失败。' }
