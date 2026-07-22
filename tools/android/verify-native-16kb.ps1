param(
    [string]$Path = ".",
    [string]$AndroidHome = $env:ANDROID_HOME,
    [switch]$SkipZipAlign,
    [switch]$VerboseZipAlign
)

$ErrorActionPreference = "Stop"

function Resolve-Tool([string]$Root, [string]$Pattern) {
    $tool = Get-ChildItem -Path $Root -Recurse -Filter $Pattern -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($null -eq $tool) {
        throw "No se ha encontrado $Pattern dentro de $Root"
    }
    return $tool.FullName
}

function Expand-Artifact([string]$ArtifactPath) {
    $temp = Join-Path ([System.IO.Path]::GetTempPath()) ("battly-native-16kb-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $temp | Out-Null
    $zipPath = Join-Path $temp "artifact.zip"
    Copy-Item -LiteralPath $ArtifactPath -Destination $zipPath -Force

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $tempPrefix = [System.IO.Path]::GetFullPath($temp) + [System.IO.Path]::DirectorySeparatorChar
    $archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        foreach ($entry in $archive.Entries) {
            $target = [System.IO.Path]::GetFullPath((Join-Path $temp $entry.FullName))
            if (-not $target.StartsWith($tempPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "Entrada ZIP fuera del directorio temporal: $($entry.FullName)"
            }
            if ([string]::IsNullOrEmpty($entry.Name)) {
                New-Item -ItemType Directory -Path $target -Force | Out-Null
                continue
            }
            $parent = Split-Path -Parent $target
            New-Item -ItemType Directory -Path $parent -Force | Out-Null
            $inputStream = $entry.Open()
            $outputStream = [System.IO.File]::Open($target, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
            try {
                $inputStream.CopyTo($outputStream)
            } finally {
                $outputStream.Dispose()
                $inputStream.Dispose()
            }
        }
    } finally {
        $archive.Dispose()
    }
    Remove-Item -LiteralPath $zipPath -Force
    return $temp
}

if ([string]::IsNullOrWhiteSpace($AndroidHome)) {
    throw "ANDROID_HOME no esta definido."
}

$objdump = Resolve-Tool (Join-Path $AndroidHome "ndk") "llvm-objdump.exe"
$zipalign = Resolve-Tool (Join-Path $AndroidHome "build-tools") "zipalign.exe"

$resolvedPath = Resolve-Path $Path
$scanRoot = $resolvedPath.Path
$tempRoot = $null

try {
    $extension = [System.IO.Path]::GetExtension($scanRoot).ToLowerInvariant()
    if ($extension -eq ".apk" -or $extension -eq ".aab") {
        if (-not $SkipZipAlign -and $extension -eq ".apk") {
            $zipalignOutput = & $zipalign -P 16 -c -v 4 $scanRoot 2>&1
            if ($LASTEXITCODE -ne 0) {
                $zipalignOutput | Out-Host
                throw "zipalign ha fallado para $scanRoot"
            }
            if ($VerboseZipAlign) {
                $zipalignOutput | Out-Host
            } else {
                Write-Host "OK: zipalign -P 16 ha verificado $scanRoot"
            }
        }
        $tempRoot = Expand-Artifact $scanRoot
        $scanRoot = $tempRoot
    }

    $libs = Get-ChildItem -Path $scanRoot -Recurse -Filter "*.so" -File
    if ($libs.Count -eq 0) {
        Write-Host "No se encontraron librerias nativas .so en $Path"
        exit 0
    }

    $failures = New-Object System.Collections.Generic.List[object]
    foreach ($lib in $libs) {
        $loads = & $objdump -p $lib.FullName 2>$null | Select-String -Pattern "LOAD .* align 2\*\*([0-9]+)"
        $minPower = 999
        foreach ($load in $loads) {
            if ($load.Line -match "align 2\*\*([0-9]+)") {
                $power = [int]$Matches[1]
                if ($power -lt $minPower) {
                    $minPower = $power
                }
            }
        }

        if ($minPower -eq 999) {
            continue
        }

        $alignBytes = [math]::Pow(2, $minPower)
        if ($alignBytes -lt 16384) {
            $failures.Add([pscustomobject]@{
                AlignBytes = [int]$alignBytes
                Power = $minPower
                Path = $lib.FullName.Replace($scanRoot, "").TrimStart("\", "/")
            })
        }
    }

    if ($failures.Count -gt 0) {
        $failures | Sort-Object AlignBytes, Path | Format-Table -AutoSize
        throw "$($failures.Count) librerias nativas no cumplen alineacion ELF minima de 16 KB."
    }

    Write-Host "OK: todas las librerias .so tienen LOAD alignment >= 16 KB."
} finally {
    if ($tempRoot -and (Test-Path $tempRoot)) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
