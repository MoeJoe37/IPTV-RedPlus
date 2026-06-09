@echo off
setlocal
set "DIR=%~dp0"
set "GRADLE_VERSION=8.11.1"

rem Use a project-local Gradle user home. This prevents failures from locked global caches such as C:\Users\<user>\.gradle\caches\journal-1.
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%DIR%.gradle-user-home"
set "GRADLE_HOME=%DIR%.gradle\redplus-gradle\gradle-%GRADLE_VERSION%"
set "GRADLE_BAT=%GRADLE_HOME%\bin\gradle.bat"

if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%" >nul 2>nul

if exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
    java -jar "%DIR%gradle\wrapper\gradle-wrapper.jar" %*
    exit /b %ERRORLEVEL%
)

if not exist "%GRADLE_BAT%" (
    echo Gradle was not found in the project. Downloading Gradle %GRADLE_VERSION% locally...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force -Path '%DIR%.gradle\redplus-gradle' | Out-Null; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIR%.gradle\gradle-%GRADLE_VERSION%-bin.zip'; Expand-Archive -Path '%DIR%.gradle\gradle-%GRADLE_VERSION%-bin.zip' -DestinationPath '%DIR%.gradle\redplus-gradle' -Force"
    if errorlevel 1 (
        echo Failed to download Gradle. Check your internet connection or install Gradle %GRADLE_VERSION% manually.
        exit /b 1
    )
)

call "%GRADLE_BAT%" %*
exit /b %ERRORLEVEL%
