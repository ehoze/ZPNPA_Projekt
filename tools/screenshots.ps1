param([string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & adb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie zainstalowac aplikacji.' }
    & adb -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie zainstalowac pakietu testow.' }
    & adb -s $Serial shell am instrument -w -e class pl.edu.zpnpa.skaner.ScreenshotTest pl.edu.zpnpa.skaner.test/androidx.test.runner.AndroidJUnitRunner
    if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie wykonac zrzutow.' }
    New-Item -ItemType Directory -Path docs/screenshots -Force | Out-Null
    & adb -s $Serial pull /sdcard/Android/data/pl.edu.zpnpa.skaner/files/screenshots/. docs/screenshots/
    if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie pobrac zrzutow.' }
} finally { Pop-Location }
