# Starts the Android Auto Desktop Head Unit connected to a USB phone.
# Usage: tools\dhu.cmd [-Serial <adb serial>] [-SkipPermissions]
param(
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
    & $dhu
} finally {
    Pop-Location
    & $adb @adbArgs forward --remove tcp:5277 2>&1 | Out-Null
    Write-Host 'DHU closed, port forward removed.'
}
