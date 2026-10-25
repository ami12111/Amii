@echo off
title Ami Client Launcher
cd /d "%~dp0"
call gradlew.bat runClient
pause
