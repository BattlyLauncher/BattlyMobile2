$ErrorActionPreference = 'Stop'

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$sourceRoot = Join-Path $PSScriptRoot 'src'
$workDir = Join-Path ([System.IO.Path]::GetTempPath()) ("battly-tts-stub-" + [guid]::NewGuid().ToString('N'))
$classesDir = Join-Path $workDir 'classes'
$targetJar = Join-Path $projectRoot 'app_pojavlauncher\src\main\assets\components\lwjgl3\android-text2speech-stub.jar'

New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

try {
    $sources = Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' |
        ForEach-Object { $_.FullName }
    if ($sources.Count -eq 0) {
        throw 'No text-to-speech stub sources were found.'
    }

    & javac -source 8 -target 8 -d $classesDir @sources
    if ($LASTEXITCODE -ne 0) { throw 'javac failed.' }

    & jar cf $targetJar -C $classesDir .
    if ($LASTEXITCODE -ne 0) { throw 'jar failed.' }

    & jar tf $targetJar
} finally {
    if (Test-Path -LiteralPath $workDir) {
        Remove-Item -LiteralPath $workDir -Recurse -Force
    }
}
