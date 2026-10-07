# Starts the Android Auto Desktop Head Unit connected to a USB phone.
# Usage: tools\dhu.cmd [-Config <file.ini>] [-Serial <adb serial>] [-SkipPermissions]
# -Config: DHU profile (resolution, margins, dpi...), e.g. tools\dhu-uni-t.ini or one of
#          <sdk>\extras\google\auto\config\*.ini. A bare name is looked up in both places.
#          Defaults to the Uni-T profile; pass -Config none for the DHU built-in defaults.
param(
    [string]$Config = 'dhu-uni-t.ini',
    [string]$Serial,
    [switch]$SkipPermissions
)

$ErrorActionPreference = 'Stop'

$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$dhuDir = Join-Path $sdk 'extras\google\auto'
$dhu = Join-Path $dhuDir 'desktop-head-unit.exe'
$aa = 'com.google.android.projection.gearhead'

if (-not (Test-Path $adb)) { throw "adb not found: $adb" }
if (-not (Test-Path $dhu)) {
    throw "DHU not found: $dhu`nInstall 'Android Auto Desktop Head Unit Emulator' in SDK Manager > SDK Tools."
}

$dhuArgs = @()
if ($Config -and $Config -ne 'none') {
    $candidates = @($Config, (Join-Path $PSScriptRoot $Config), (Join-Path $dhuDir "config\$Config"))
    $cfg = $candidates | Where-Object { Test-Path $_ -PathType Leaf } | Select-Object -First 1
    if (-not $cfg) { throw "DHU config not found: $Config" }
    $cfg = (Resolve-Path $cfg).Path
    $dhuArgs = @('-c', $cfg)
    Write-Host "Config: $cfg"
}

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' } |
        ForEach-Object { ($_ -split '\t')[0] }
if (-not $devices) { throw 'No phone connected (check the cable and USB debugging authorization).' }
if (-not $Serial) {
    if (@($devices).Count -gt 1) { throw "Several devices connected, pass -Serial: $($devices -join ', ')" }
    $Serial = @($devices)[0]
}
$adbArgs = @('-s', $Serial)
Write-Host "Phone: $Serial"

if (-not $SkipPermissions) {
    # HyperOS/MIUI blocks Android Auto from starting its projection activity in the background,
    # which leaves the DHU on "waiting for phone". 10021/10020 are MIUI-only ops; ignored elsewhere.
    & $adb @adbArgs shell "cmd appops set $aa 10021 allow; cmd appops set $aa 10020 allow; cmd appops set $aa SYSTEM_ALERT_WINDOW allow; dumpsys deviceidle whitelist +$aa" 2>&1 | Out-Null
}

& $adb @adbArgs forward tcp:5277 tcp:5277 | Out-Null
Write-Host 'Port 5277 forwarded. Make sure "Start head unit server" is enabled in Android Auto.'
Write-Host 'Starting DHU (close it or type "quit" to stop)...'

try {
    Push-Location $dhuDir
    & $dhu @dhuArgs
} finally {
    Pop-Location
    & $adb @adbArgs forward --remove tcp:5277 2>&1 | Out-Null
    Write-Host 'DHU closed, port forward removed.'
}
