param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $studioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath $studioJdk) { $env:JAVA_HOME = $studioJdk }
    else { throw 'Ustaw JAVA_HOME na JDK 17 lub nowszy albo zainstaluj Android Studio.' }
}
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'local.properties'))) {
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    if (-not (Test-Path -LiteralPath $sdkRoot)) { throw 'Zainstaluj Android SDK w Android Studio.' }
    ('sdk.dir=' + $sdkRoot.Replace('\', '/').Replace(':', '\:')) | Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Encoding ASCII
}
Push-Location $projectRoot
try {
    & .\gradlew.bat @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle zakonczyl prace z kodem $LASTEXITCODE." }
} finally { Pop-Location }
