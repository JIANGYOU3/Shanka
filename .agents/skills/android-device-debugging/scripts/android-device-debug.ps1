[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('devices', 'inspect', 'install', 'reverse', 'reverse-list', 'reverse-clear', 'instrument', 'start', 'force-stop')]
    [string]$Action,

    [string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Apk,
    [int]$DevicePort,
    [int]$HostPort,
    [string]$Package,
    [string]$Component,
    [string]$Runner,
    [string]$TestClass,
    [string]$ExtraKey,
    [string]$ExtraValue,
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) {
    throw "adb.exe not found: $Adb"
}

function Convert-ArgumentLine {
    param([string[]]$Values)

    ($Values | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + $_.Replace('"', '\"') + '"'
        } else {
            $_
        }
    }) -join ' '
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [int]$Timeout = $TimeoutSeconds
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Adb
    $startInfo.Arguments = Convert-ArgumentLine $Arguments
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "failed to start adb: $($Arguments -join ' ')"
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()

        if (-not $process.WaitForExit($Timeout * 1000)) {
            if (-not $process.HasExited) { $process.Kill() }
            throw "adb timed out after $Timeout seconds: $($Arguments -join ' ')"
        }
        $process.WaitForExit()

        $output = $stdoutTask.Result
        $errors = $stderrTask.Result
        if (-not [string]::IsNullOrWhiteSpace($output)) {
            $output.TrimEnd() -split "`r?`n" | Write-Output
        }
        # adb sometimes writes normal progress to stderr. Preserve it as output;
        # the process exit code remains the authority for success or failure.
        if (-not [string]::IsNullOrWhiteSpace($errors)) {
            $errors.TrimEnd() -split "`r?`n" | Write-Output
        }
        if ($process.ExitCode -ne 0) {
            throw "adb exited with code $($process.ExitCode): $($Arguments -join ' ')"
        }
    } finally {
        $process.Dispose()
    }
}

function Require-Serial {
    if ([string]::IsNullOrWhiteSpace($Serial)) {
        throw "-$Action requires -Serial. Run -Action devices first."
    }
}

function Require-Port {
    param([int]$Port, [string]$Name)
    if ($Port -lt 1 -or $Port -gt 65535) {
        throw "$Name must be between 1 and 65535."
    }
}

switch ($Action) {
    'devices' {
        Invoke-Adb @('devices', '-l')
    }
    'inspect' {
        Require-Serial
        Invoke-Adb @('-s', $Serial, 'get-state')
        foreach ($property in @(
            'ro.product.manufacturer',
            'ro.product.model',
            'ro.build.version.release',
            'ro.build.version.sdk',
            'ro.build.version.oplusrom'
        )) {
            Write-Output "$property="
            Invoke-Adb @('-s', $Serial, 'shell', 'getprop', $property)
        }
        Write-Output 'power='
        Invoke-Adb @('-s', $Serial, 'shell', 'dumpsys', 'power') |
            Select-String 'mWakefulness=|mHalInteractiveModeEnabled=|mStayOn=|Screen off timeout:'
        Write-Output 'reverse='
        Invoke-Adb @('-s', $Serial, 'reverse', '--list')
    }
    'install' {
        Require-Serial
        if ([string]::IsNullOrWhiteSpace($Apk)) { throw '-Action install requires -Apk.' }
        $resolvedApk = (Resolve-Path -LiteralPath $Apk).Path
        Invoke-Adb @('-s', $Serial, 'install', '-r', $resolvedApk)
    }
    'reverse' {
        Require-Serial
        Require-Port $DevicePort 'DevicePort'
        Require-Port $HostPort 'HostPort'
        Invoke-Adb @('-s', $Serial, 'reverse', "tcp:$DevicePort", "tcp:$HostPort")
        Invoke-Adb @('-s', $Serial, 'reverse', '--list')
    }
    'reverse-list' {
        Require-Serial
        Invoke-Adb @('-s', $Serial, 'reverse', '--list')
    }
    'reverse-clear' {
        Require-Serial
        Invoke-Adb @('-s', $Serial, 'reverse', '--remove-all')
        Invoke-Adb @('-s', $Serial, 'reverse', '--list')
    }
    'instrument' {
        Require-Serial
        if ([string]::IsNullOrWhiteSpace($Runner)) { throw '-Action instrument requires -Runner.' }
        $arguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
        if (-not [string]::IsNullOrWhiteSpace($ExtraKey)) {
            if ([string]::IsNullOrWhiteSpace($ExtraValue)) {
                throw '-ExtraKey requires -ExtraValue.'
            }
            $arguments += @('-e', $ExtraKey, $ExtraValue)
        }
        if (-not [string]::IsNullOrWhiteSpace($TestClass)) {
            $arguments += @('-e', 'class', $TestClass)
        }
        $arguments += $Runner
        Invoke-Adb $arguments
    }
    'start' {
        Require-Serial
        if ([string]::IsNullOrWhiteSpace($Component)) { throw '-Action start requires -Component.' }
        Invoke-Adb @('-s', $Serial, 'shell', 'am', 'start', '-W', '-n', $Component)
    }
    'force-stop' {
        Require-Serial
        if ([string]::IsNullOrWhiteSpace($Package)) { throw '-Action force-stop requires -Package.' }
        Invoke-Adb @('-s', $Serial, 'shell', 'am', 'force-stop', $Package)
    }
}
