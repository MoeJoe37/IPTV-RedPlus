@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem Keep Gradle completely inside this project so locked global caches under C:\Users\<user>\.gradle cannot break the build.
set "REDPLUS_GRADLE_USER_HOME=%~dp0.gradle-user-home"
set "GRADLE_USER_HOME=%REDPLUS_GRADLE_USER_HOME%"
set "GRADLE_OPTS=-Xmx4096m -Dfile.encoding=UTF-8 -Dorg.gradle.daemon=false -Dorg.gradle.vfs.watch=false"
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8"

if not exist "%REDPLUS_GRADLE_USER_HOME%" mkdir "%REDPLUS_GRADLE_USER_HOME%" >nul 2>nul

cls
echo =====================================================
echo   RedPlus IPTV - One-Click Android APK Builder
echo =====================================================
echo.
echo Gradle cache for this project:
echo %REDPLUS_GRADLE_USER_HOME%
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

echo [1/5] Preparing Android SDK and local.properties...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\prepare_android_sdk.ps1"
if errorlevel 1 goto build_failed

echo.
echo [2/5] Stopping stale Gradle daemons for this project cache...
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
pause
exit /b 0

:gradle_lock_help
echo.
echo [ERROR] Gradle failed while cleaning the project.
echo This build uses a local Gradle cache, so it should not be affected by C:\Users\%USERNAME%\.gradle locks.
echo If the error still mentions a lock inside this project, close Android Studio/Java/Gradle windows and run BUILD.bat again.
echo You can also delete this folder safely and retry:
echo %REDPLUS_GRADLE_USER_HOME%
pause
exit /b 1

:build_failed
echo.
echo [ERROR] Build failed. Read the output above.
echo This script creates local.properties, can install the Android SDK, and uses a project-local Gradle cache.
echo If it still fails, check your internet connection and make sure no antivirus is blocking Gradle or Java.
pause
exit /b 1
