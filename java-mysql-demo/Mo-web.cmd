@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\Start-Web.ps1" %*
if errorlevel 1 pause
