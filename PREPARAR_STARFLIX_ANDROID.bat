@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Preparar StarFlix Android

echo =============================================
echo       STARFLIX ANDROID - PREPARACION
echo =============================================
echo.

rem Configurar automaticamente la ruta del Android SDK en Windows.
if exist "%LOCALAPPDATA%\Android\Sdk" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$sdk=(Join-Path $env:LOCALAPPDATA 'Android\Sdk').Replace('\','\\'); Set-Content -Encoding ASCII 'local.properties' ('sdk.dir=' + $sdk)"
  echo [OK] Android SDK configurado en local.properties.
)

set "WRAPPER=gradle\wrapper\gradle-wrapper.jar"
set "WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v9.5.0/gradle/wrapper/gradle-wrapper.jar"

if exist "%WRAPPER%" (
  echo [OK] Gradle Wrapper ya esta instalado.
  goto DONE
)

echo Descargando Gradle Wrapper 9.5.0 desde el repositorio oficial de Gradle...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Invoke-WebRequest -UseBasicParsing '%WRAPPER_URL%' -OutFile '%WRAPPER%'"

if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo descargar gradle-wrapper.jar.
  echo Comprueba tu conexion a Internet y vuelve a ejecutar este archivo.
  pause
  exit /b 1
)

:DONE
echo.
echo [OK] Proyecto preparado.
echo.
echo Ahora:
echo 1. Abre Android Studio.
echo 2. File ^> Open.
echo 3. Selecciona esta carpeta.
echo 4. Espera al Gradle Sync.
echo 5. Ejecuta la app en tu Android.
echo.
pause
