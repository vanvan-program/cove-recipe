@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Sign-CoveRecipe.ps1" %*
set "result=%errorlevel%"
pause
exit /b %result%
