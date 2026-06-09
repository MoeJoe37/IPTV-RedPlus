@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem =====================================================
rem RedPlus IPTV persistent one-click builder
rem =====================================================
rem The old builds used a cache inside each extracted ZIP, so every new folder could
rem download Gradle/dependencies again. This build uses a permanent per-user cache.
rem It also writes user environment variables once, so future builds reuse the same files.

set "REDPLUS_HOME=%USERPROFILE%\.redplus-iptv"
if not defined REDPLUS_GRADLE_USER_HOME set "REDPLUS_GRADLE_USER_HOME=%REDPLUS_HOME%\gradle-cache"
set "GRADLE_USER_HOME=%REDPLUS_GRADLE_USER_HOME%"
set "GRADLE_OPTS=-Xmx4096m -Dfile.encoding=UTF-8 -Dorg.gradle.daemon=false -Dorg.gradle.vfs.watch=false"
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8"

if not exist "%REDPLUS_HOME%" mkdir "%REDPLUS_HOME%" >nul 2>nul
if not exist "%REDPLUS_GRADLE_USER_HOME%" mkdir "%REDPLUS_GRADLE_USER_HOME%" >nul 2>nul

rem Persist RedPlus build environment variables. If BUILD.bat is run as Administrator,
rem the variables are also written to SYSTEM env; otherwise USER env is used.
if exist "scripts\prepare_build_environment.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\prepare_build_environment.ps1" -GradleUserHome "%REDPLUS_GRADLE_USER_HOME%" >nul 2>nul
)

cls
echo =====================================================
echo   RedPlus IPTV - One-Click Android APK Builder
echo =====================================================
echo.
echo Persistent Gradle cache:
echo %REDPLUS_GRADLE_USER_HOME%
echo.
echo Project folder:
echo %cd%
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java was not found in PATH.
    echo Install JDK 17 or Android Studio, then run BUILD.bat again.
    echo If winget is available, you can install it with:
    echo winget install Microsoft.OpenJDK.17
    pause
    exit /b 1
)

if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat was not found in this folder.
    echo Make sure you extracted the full ZIP contents before running this file.
    pause
    exit /b 1
)

if not exist "scripts\prepare_android_sdk.ps1" (
    echo [ERROR] scripts\prepare_android_sdk.ps1 was not found.
    echo Make sure you extracted the full ZIP contents before running this file.
    pause
    exit /b 1
)

echo [1/5] Preparing Android SDK, environment variables, and local.properties...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\prepare_android_sdk.ps1"
if errorlevel 1 goto build_failed

echo.
echo [2/5] Stopping stale Gradle daemons that use the RedPlus cache...
call gradlew.bat --stop --console=plain >nul 2>nul

echo.
echo [3/5] Cleaning project...
call gradlew.bat --no-daemon --no-watch-fs --console=plain clean
if errorlevel 1 goto gradle_lock_help

echo.
echo [4/5] Building debug APK...
call gradlew.bat --no-daemon --no-watch-fs --console=plain :app:assembleDebug
if errorlevel 1 goto build_failed

echo.
echo [5/5] Done.
echo APK output:
echo %cd%\app\build\outputs\apk\debug\app-debug.apk
echo.
echo Reuse note:
echo Gradle and Android dependencies are cached under %REDPLUS_GRADLE_USER_HOME%.
echo Future RedPlus builds should reuse this cache instead of downloading everything again.
echo.
pause
exit /b 0

:gradle_lock_help
echo.
echo [ERROR] Gradle failed while cleaning the project.
echo This build uses the persistent RedPlus Gradle cache:
echo %REDPLUS_GRADLE_USER_HOME%
echo If the error mentions a lock in that folder, close Android Studio/Java/Gradle windows and run BUILD.bat again.
echo You can also delete this cache folder safely; it will be rebuilt next time:
echo %REDPLUS_GRADLE_USER_HOME%
pause
exit /b 1

:build_failed
echo.
echo [ERROR] Build failed. Read the output above.
echo This script creates local.properties, prepares Android SDK variables, and reuses one persistent Gradle cache.
echo If it still fails, check your internet connection and make sure no antivirus is blocking Gradle or Java.
pause
exit /b 1
