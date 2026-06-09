param(
    [Parameter(Mandatory=$true)]
    [string]$GradleUserHome
)

$ErrorActionPreference = 'Stop'

function Test-IsAdmin {
    try {
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        $principal = New-Object Security.Principal.WindowsPrincipal($identity)
        return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    } catch {
        return $false
    }
}

function Save-EnvironmentVariable([string]$Name, [string]$Value) {
    [Environment]::SetEnvironmentVariable($Name, $Value, 'User')
    Write-Host "Saved USER env: $Name=$Value"

    if (Test-IsAdmin) {
        try {
            [Environment]::SetEnvironmentVariable($Name, $Value, 'Machine')
            Write-Host "Saved SYSTEM env: $Name=$Value"
        } catch {
            Write-Host "Could not save SYSTEM env for ${Name}: $($_.Exception.Message)"
        }
    } else {
        Write-Host "SYSTEM env for $Name was not changed because BUILD.bat is not running as Administrator. USER env is enough for this build."
    }
}

New-Item -ItemType Directory -Force -Path $GradleUserHome | Out-Null
Save-EnvironmentVariable 'REDPLUS_GRADLE_USER_HOME' $GradleUserHome
