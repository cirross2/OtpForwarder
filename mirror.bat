@echo off
chcp 65001 >nul
powershell -ExecutionPolicy Bypass -File "%~dp0mirror.ps1" %*
pause

