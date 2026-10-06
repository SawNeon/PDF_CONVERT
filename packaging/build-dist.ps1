[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Maven = 'mvn',
    [switch]$Msi,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$AppName = 'PdfLocal'
$MainClass = 'br.com.pdflocal.app.Launcher'
$JdkModules = @('java.base', 'java.desktop', 'java.logging', 'java.xml', 'jdk.unsupported', 'jdk.unsupported.desktop')
$JavaFxModules = @('javafx.base', 'javafx.graphics', 'javafx.controls', 'javafx.swing')
$MsiUpgradeUuid = '7d6b1c52-3c43-4d4f-9a52-5a1d2f6f8e11'

function Fail($message) {
    Write-Error $message
    exit 1
}

function Step($message) {
    Write-Host ''
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Invoke-Tool($path, [string[]]$arguments) {
    & $path @arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "$(Split-Path $path -Leaf) falhou com codigo $LASTEXITCODE"
    }
}

if (-not $JavaHome) {
    Fail 'Defina JAVA_HOME para um JDK 21 ou passe -JavaHome.'
}
$jlink = Join-Path $JavaHome 'bin\jlink.exe'
$jpackage = Join-Path $JavaHome 'bin\jpackage.exe'
$java = Join-Path $JavaHome 'bin\java.exe'
foreach ($tool in @($jlink, $jpackage, $java)) {
    if (-not (Test-Path $tool)) {
        Fail "Ferramenta nao encontrada: $tool"
    }
}
$versionLine = (cmd /c "`"$java`" -version 2>&1") | Select-Object -First 1
if ($versionLine -notmatch '"21\.') {
    Fail "E necessario o JDK 21. Encontrado: $versionLine"
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$target = Join-Path $root 'target'
$dist = Join-Path $target 'dist'
$inputDir = Join-Path $dist 'input'
$runtime = Join-Path $dist 'runtime'
$appImageDir = Join-Path $dist 'app-image'
$installerDir = Join-Path $dist 'installer'

$pom = Get-Content (Join-Path $root 'pom.xml') -Raw
if ($pom -notmatch '<artifactId>pdflocal</artifactId>\s*<version>([^<]+)</version>') {
    Fail 'Nao foi possivel ler a versao do pom.xml.'
}
$appVersion = ($Matches[1] -replace '-SNAPSHOT$', '')
if ($appVersion -notmatch '^\d+(\.\d+){0,2}$') {
    Fail "Versao invalida para o jpackage: $appVersion"
}

if (-not $SkipBuild) {
    Step 'Compilando e reunindo dependencias (mvn -Pdist package)'
    $env:JAVA_HOME = $JavaHome
    Push-Location $root
    try {
        Invoke-Tool $Maven @('-B', '-Pdist', '-DskipTests', 'clean', 'package')
    } finally {
        Pop-Location
    }
}

$appJar = Get-ChildItem $target -Filter 'pdflocal-*.jar' | Where-Object { $_.Name -notmatch '(sources|javadoc)' } | Select-Object -First 1
$fxJars = @(Get-ChildItem (Join-Path $dist 'javafx') -Filter '*-win.jar' -ErrorAction SilentlyContinue)
if (-not $appJar -or $fxJars.Count -eq 0 -or -not (Test-Path $inputDir)) {
    Fail 'Artefatos do build nao encontrados. Rode sem -SkipBuild.'
}
Copy-Item $appJar.FullName $inputDir -Force

Step 'Montando o runtime reduzido (jlink)'
if (Test-Path $runtime) {
    Remove-Item $runtime -Recurse -Force
}
$modulePath = (($fxJars | ForEach-Object { $_.FullName }) + (Join-Path $JavaHome 'jmods')) -join ';'
$addModules = ($JavaFxModules + $JdkModules) -join ','
Invoke-Tool $jlink @('--module-path', $modulePath, '--add-modules', $addModules, '--output', $runtime,
    '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=zip-6')

Step 'Copiando as bibliotecas nativas do JavaFX para o runtime'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$runtimeBin = Join-Path $runtime 'bin'
$copied = 0
foreach ($jar in $fxJars) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -like '*.dll' -and $entry.FullName -notlike '*/*') {
                $destination = Join-Path $runtimeBin $entry.Name
                if (-not (Test-Path $destination)) {
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination)
                    $copied++
                }
            }
        }
    } finally {
        $zip.Dispose()
    }
}
Write-Host "$copied bibliotecas nativas copiadas"

$commonArguments = @(
    '--name', $AppName,
    '--app-version', $appVersion,
    '--vendor', 'PdfLocal',
    '--description', 'Junta PDFs e converte imagens em PDF, tudo localmente',
    '--input', $inputDir,
    '--main-jar', $appJar.Name,
    '--main-class', $MainClass,
    '--runtime-image', $runtime,
    '--java-options', '-Xmx1g'
)

$iconPath = Join-Path $PSScriptRoot 'app-icon.ico'
if (Test-Path $iconPath) {
    $commonArguments += @('--icon', $iconPath)
}

Step 'Gerando o app-image portatil (jpackage)'
if (Test-Path $appImageDir) {
    Remove-Item $appImageDir -Recurse -Force
}
Invoke-Tool $jpackage (@('--type', 'app-image', '--dest', $appImageDir) + $commonArguments)

$zipName = "$AppName-$appVersion-windows-portable.zip"
$zipPath = Join-Path $dist $zipName
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}
Compress-Archive -Path (Join-Path $appImageDir $AppName) -DestinationPath $zipPath
$artifacts = @($zipPath)

if ($Msi) {
    Step 'Gerando o instalador MSI (jpackage + WiX)'
    $wix = Get-Command 'candle.exe' -ErrorAction SilentlyContinue
    if (-not $wix) {
        Fail 'O MSI exige o WiX Toolset 3 (candle.exe e light.exe) no PATH. Veja o README.'
    }
    if (Test-Path $installerDir) {
        Remove-Item $installerDir -Recurse -Force
    }
    Invoke-Tool $jpackage (@('--type', 'msi', '--dest', $installerDir, '--win-per-user-install', '--win-menu',
        '--win-shortcut', '--win-dir-chooser', '--win-upgrade-uuid', $MsiUpgradeUuid) + $commonArguments)
    $artifacts += (Get-ChildItem $installerDir -Filter '*.msi' | ForEach-Object { $_.FullName })
}

Step 'Calculando os hashes SHA-256'
$sumsPath = Join-Path $dist 'SHA256SUMS.txt'
$lines = foreach ($artifact in $artifacts) {
    $hash = (Get-FileHash $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash *$(Split-Path $artifact -Leaf)"
}
Set-Content -Path $sumsPath -Value $lines -Encoding ascii

Write-Host ''
Write-Host 'Pronto.' -ForegroundColor Green
Write-Host "App-image:  $(Join-Path $appImageDir $AppName)"
$artifacts | ForEach-Object { Write-Host "Artefato:   $_" }
Write-Host "Hashes:     $sumsPath"
$lines | ForEach-Object { Write-Host "            $_" }
