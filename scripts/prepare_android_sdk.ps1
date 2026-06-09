$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$requiredApi = '35'
$requiredPlatform = "android-$requiredApi"
$requiredBuildTools = '35.0.0'
$cmdlineToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip'

function Write-Section([string]$text) {
    Write-Host ''
    Write-Host "== $text =="
}

function Test-SdkReady([string]$sdkRoot) {
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) { return $false }
    $sdkRoot = [Environment]::ExpandEnvironmentVariables($sdkRoot)
    if (-not (Test-Path $sdkRoot)) { return $false }
    $androidJar = Join-Path $sdkRoot "platforms\$requiredPlatform\android.jar"
    $aapt2 = Join-Path $sdkRoot "build-tools\$requiredBuildTools\aapt2.exe"
    return ((Test-Path $androidJar) -and (Test-Path $aapt2))
}

function Find-SdkManager([string]$sdkRoot) {
    if ([string]::IsNullOrWhiteSpace($sdkRoot) -or -not (Test-Path $sdkRoot)) { return $null }
    $preferred = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (Test-Path $preferred) { return $preferred }
    $cmdlineRoot = Join-Path $sdkRoot 'cmdline-tools'
    if (Test-Path $cmdlineRoot) {
        $found = Get-ChildItem -Path $cmdlineRoot -Recurse -Filter 'sdkmanager.bat' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

function Read-LocalPropertiesSdkDir {
    $localProperties = Join-Path $root 'local.properties'
    if (-not (Test-Path $localProperties)) { return $null }
    $line = Get-Content $localProperties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
    if (-not $line) { return $null }
    $value = ($line -replace '^\s*sdk\.dir\s*=\s*', '').Trim()
    if ([string]::IsNullOrWhiteSpace($value)) { return $null }
    return $value.Replace('/', '\')
}

function Get-CandidateSdkRoots {
    $items = New-Object System.Collections.Generic.List[string]

    $localSdk = Read-LocalPropertiesSdkDir
    if ($localSdk) { $items.Add($localSdk) }

    if ($env:ANDROID_SDK_ROOT) { $items.Add($env:ANDROID_SDK_ROOT) }
    if ($env:ANDROID_HOME) { $items.Add($env:ANDROID_HOME) }
    if ($env:LOCALAPPDATA) { $items.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk')) }

    $items.Add((Join-Path $root '.android-sdk'))

    return $items | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
}

function Get-UsableOrDefaultSdkRoot {
    foreach ($candidate in Get-CandidateSdkRoots) {
        $expanded = [Environment]::ExpandEnvironmentVariables($candidate)
        if (Test-SdkReady $expanded) {
            Write-Host "Using existing Android SDK: $expanded"
            return $expanded
        }
    }

    foreach ($candidate in Get-CandidateSdkRoots) {
        $expanded = [Environment]::ExpandEnvironmentVariables($candidate)
        if (Find-SdkManager $expanded) {
            Write-Host "Using Android SDK that has sdkmanager: $expanded"
            return $expanded
        }
    }

    $projectSdk = Join-Path $root '.android-sdk'
    Write-Host "No complete Android SDK was found. A local SDK will be prepared here: $projectSdk"
    return $projectSdk
}

function Download-CommandLineTools([string]$sdkRoot) {
    Write-Section 'Installing Android command-line tools'

    New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null

    $cacheDir = Join-Path $root '.build-cache'
    New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null

    $zipPath = Join-Path $cacheDir 'commandlinetools-win-latest.zip'
    $extractDir = Join-Path $cacheDir 'cmdline-tools-extracted'
    $latestDir = Join-Path $sdkRoot 'cmdline-tools\latest'

    if (-not (Test-Path $zipPath)) {
        Write-Host 'Downloading Android command-line tools from Google...'
        Write-Host $cmdlineToolsUrl
        Invoke-WebRequest -Uri $cmdlineToolsUrl -OutFile $zipPath
    } else {
        Write-Host "Using cached command-line tools ZIP: $zipPath"
    }

    if (Test-Path $extractDir) { Remove-Item -Recurse -Force $extractDir }
    New-Item -ItemType Directory -Force -Path $extractDir | Out-Null
    Expand-Archive -Path $zipPath -DestinationPath $extractDir -Force

    $sourceDir = Join-Path $extractDir 'cmdline-tools'
    if (-not (Test-Path (Join-Path $sourceDir 'bin\sdkmanager.bat'))) {
        throw "The downloaded command-line tools ZIP did not contain bin\sdkmanager.bat. Delete $zipPath and run BUILD.bat again."
    }

    if (Test-Path $latestDir) { Remove-Item -Recurse -Force $latestDir }
    New-Item -ItemType Directory -Force -Path $latestDir | Out-Null
    Copy-Item -Recurse -Force (Join-Path $sourceDir '*') $latestDir

    Remove-Item -Recurse -Force $extractDir
}

function Quote-Arg([string]$arg) {
    if ($null -eq $arg) { return '""' }
    if ($arg -match '[\s"]') {
        return '"' + ($arg -replace '"', '\"') + '"'
    }
    return $arg
}

function Invoke-SdkManagerWithYes([string]$sdkManager, [string[]]$arguments, [string]$title) {
    Write-Section $title
    Write-Host "$sdkManager $($arguments -join ' ')"

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    # Run through cmd.exe so sdkmanager.bat works even with redirected stdin.
    $psi.FileName = if ($env:COMSPEC) { $env:COMSPEC } else { 'cmd.exe' }
    $argLine = ($arguments | ForEach-Object { Quote-Arg $_ }) -join ' '
    $psi.Arguments = '/c ' + (Quote-Arg $sdkManager) + ' ' + $argLine
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $false
    $psi.RedirectStandardError = $false

    $process = [System.Diagnostics.Process]::Start($psi)
    try {
        for ($i = 0; $i -lt 120; $i++) {
            $process.StandardInput.WriteLine('y')
        }
        $process.StandardInput.Close()
    } catch {
        Write-Host "License input stream closed: $($_.Exception.Message)"
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "sdkmanager failed during: $title (exit code $($process.ExitCode))"
    }
}


function Test-IsAdmin {
    try {
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        $principal = New-Object Security.Principal.WindowsPrincipal($identity)
        return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    } catch {
        return $false
    }
}

function Save-EnvironmentVariable([string]$name, [string]$value) {
    try {
        [Environment]::SetEnvironmentVariable($name, $value, 'User')
        Write-Host "Saved USER env: $name=$value"
        if (Test-IsAdmin) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Machine')
            Write-Host "Saved SYSTEM env: $name=$value"
        } else {
            Write-Host "SYSTEM env for $name was not changed because BUILD.bat is not running as Administrator. USER env is enough for this build."
        }
    } catch {
        Write-Host "Could not save environment variable ${name}: $($_.Exception.Message)"
    }
}

function Write-UserEnvironmentVariables([string]$sdkRoot) {
    Save-EnvironmentVariable 'ANDROID_HOME' $sdkRoot
    Save-EnvironmentVariable 'ANDROID_SDK_ROOT' $sdkRoot
    Save-EnvironmentVariable 'REDPLUS_ANDROID_SDK_ROOT' $sdkRoot
    Write-Host 'New Command Prompt/PowerShell windows will see the saved variables. This build uses local.properties immediately.'
}

function Write-LocalProperties([string]$sdkRoot) {
    $localProperties = Join-Path $root 'local.properties'
    $escaped = $sdkRoot.Replace('\', '/')
    $content = @(
        '# This file is generated by BUILD.bat.',
        '# It points Gradle to the Android SDK used for this project.',
        "sdk.dir=$escaped"
    ) -join [Environment]::NewLine
    Set-Content -Path $localProperties -Value $content -Encoding ASCII
    Write-Host "Wrote local.properties with sdk.dir=$escaped"
}

Write-Section 'Checking Android SDK'
$sdkRoot = Get-UsableOrDefaultSdkRoot
New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null

$sdkManager = Find-SdkManager $sdkRoot
if (-not $sdkManager) {
    Download-CommandLineTools $sdkRoot
    $sdkManager = Find-SdkManager $sdkRoot
}
if (-not $sdkManager) {
    throw "sdkmanager.bat was not found after command-line tools setup. SDK root: $sdkRoot"
}

Write-Host "sdkmanager found: $sdkManager"

if (-not (Test-SdkReady $sdkRoot)) {
    Invoke-SdkManagerWithYes $sdkManager @("--sdk_root=$sdkRoot", '--licenses') 'Accepting Android SDK licenses'
    Invoke-SdkManagerWithYes $sdkManager @("--sdk_root=$sdkRoot", 'platform-tools', "platforms;$requiredPlatform", "build-tools;$requiredBuildTools") "Installing Android SDK packages: platform-tools, $requiredPlatform, build-tools $requiredBuildTools"
} else {
    Write-Host "Required Android SDK packages are already installed: $requiredPlatform / build-tools $requiredBuildTools"
}

if (-not (Test-SdkReady $sdkRoot)) {
    throw "Android SDK setup did not finish correctly. Missing $requiredPlatform or build-tools $requiredBuildTools under: $sdkRoot"
}

Write-LocalProperties $sdkRoot
Write-UserEnvironmentVariables $sdkRoot

Write-Section 'Android SDK ready'
Write-Host "SDK root: $sdkRoot"
exit 0
