$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$GradleVersion = '8.9'

Write-Host '=== RO ANDROID 0.1 - COMPILADOR ===' -ForegroundColor Cyan

# Locate Android SDK.
$Sdk = $env:ANDROID_SDK_ROOT
if (-not $Sdk) { $Sdk = $env:ANDROID_HOME }
if (-not $Sdk) {
    $DefaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $DefaultSdk) { $Sdk = $DefaultSdk }
}
if (-not $Sdk -or -not (Test-Path (Join-Path $Sdk 'platforms\android-35\android.jar'))) {
    throw 'Android SDK Platform 35 nao encontrado. Instale Android Studio e Android SDK Platform 35 em Tools > SDK Manager. O caminho padrao e %LOCALAPPDATA%\Android\Sdk.'
}
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
Write-Host "Android SDK: $Sdk"

# Android Gradle Plugin 8.7.3 needs JDK 17.
$JavaCommand = Get-Command java -ErrorAction SilentlyContinue
$JavaVersion = if ($JavaCommand) { (& java -version 2>&1 | Out-String) } else { '' }
$JavaMajor = 0
if ($JavaVersion -match 'version "(\d+)') { $JavaMajor = [int]$Matches[1] }
if ($JavaMajor -lt 17) {
    $StudioJbrCandidates = @(
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Android\Android Studio\jbr\bin\..'
    )
    $StudioJbr = $StudioJbrCandidates | Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
    if ($StudioJbr) {
        $env:JAVA_HOME = $StudioJbr
        $env:PATH = (Join-Path $StudioJbr 'bin') + ';' + $env:PATH
        $JavaVersion = (& java -version 2>&1 | Out-String)
    } else {
        throw 'Java 17 ou superior nao encontrado. Instale Android Studio ou configure JAVA_HOME com JDK 17+.'
    }
}
Write-Host $JavaVersion

# Download a pinned Gradle distribution into the user's local cache when needed.
$CacheRoot = Join-Path $env:LOCALAPPDATA 'RoBuild'
$GradleHome = Join-Path $CacheRoot "gradle-$GradleVersion"
$GradleExe = Join-Path $GradleHome 'bin\gradle.bat'
if (-not (Test-Path $GradleExe)) {
    New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null
    $ZipPath = Join-Path $env:TEMP "gradle-$GradleVersion-bin.zip"
    $ExtractRoot = Join-Path $env:TEMP "Ro-Gradle-$GradleVersion"
    $Url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    Write-Host "Baixando Gradle $GradleVersion..."
    Invoke-WebRequest -Uri $Url -OutFile $ZipPath
    if (Test-Path $ExtractRoot) { Remove-Item -Recurse -Force $ExtractRoot }
    Expand-Archive -Path $ZipPath -DestinationPath $ExtractRoot -Force
    $Extracted = Join-Path $ExtractRoot "gradle-$GradleVersion"
    if (-not (Test-Path (Join-Path $Extracted 'bin\gradle.bat'))) {
        throw 'O arquivo do Gradle foi baixado, mas nao encontrei o executavel esperado.'
    }
    if (Test-Path $GradleHome) { Remove-Item -Recurse -Force $GradleHome }
    Move-Item -Path $Extracted -Destination $GradleHome
}

Push-Location $ProjectRoot
try {
    Write-Host 'Compilando o APK de depuracao...' -ForegroundColor Cyan
    & $GradleExe --no-daemon :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminou com codigo $LASTEXITCODE." }
    $Apk = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $Apk)) { throw 'A compilacao terminou sem encontrar app-debug.apk.' }
    Write-Host "APK pronto: $Apk" -ForegroundColor Green
} finally {
    Pop-Location
}
