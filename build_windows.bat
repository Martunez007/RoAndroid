@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_windows.ps1"
if errorlevel 1 (
  echo.
  echo A compilacao nao foi concluida. Leia a mensagem acima.
  pause
  exit /b 1
)
echo.
echo APK gerado em app\build\outputs\apk\debug\app-debug.apk
pause
