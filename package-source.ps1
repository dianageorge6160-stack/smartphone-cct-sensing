$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression

$projectRoot = $PSScriptRoot
[xml]$manifest = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'app/src/main/AndroidManifest.xml')
$version = $manifest.DocumentElement.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
if ($version -notmatch '^[0-9A-Za-z][0-9A-Za-z._-]*$') { throw 'Invalid source version.' }
$apkName = "ColorTemperature-$version.apk"
$apkPath = Join-Path $projectRoot $apkName
if (!(Test-Path -LiteralPath $apkPath -PathType Leaf)) { throw 'Build the matching APK before packaging its source.' }
$archiveName = "ColorTemperature-$version-source.zip"
$archivePath = Join-Path $projectRoot $archiveName
$prefix = "ColorTemperature-$version/"
$sourcePrefix = [System.IO.Path]::GetFullPath($projectRoot).TrimEnd([char[]]@('\', '/')) + [System.IO.Path]::DirectorySeparatorChar

# Include the source tree explicitly; never walk the project root or build/signing directories.
$files = @('.gitignore', 'README.md', 'PRIVACY.md', 'TERMS.md', 'build.ps1', 'package-source.ps1') | ForEach-Object {
    Get-Item -LiteralPath (Join-Path $projectRoot $_)
}
$files += Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/src') -Recurse -File -Force
$files += Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests') -Recurse -File -Filter '*.java'
$files = @($files | Sort-Object FullName -Unique)
foreach ($file in $files) {
    if ($file.Extension -in @('.keystore', '.jks', '.p12', '.pfx', '.key', '.pem', '.apk', '.zip')) {
        throw "Excluded private key or generated artifact found in source tree: $($file.Name)"
    }
    if ($file.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
        throw "Source archive cannot contain symbolic links: $($file.Name)"
    }
}
$release = [ordered]@{
    applicationId = $manifest.DocumentElement.GetAttribute('package')
    versionName = $version
    versionCode = $manifest.DocumentElement.GetAttribute('versionCode', 'http://schemas.android.com/apk/res/android')
    apkFile = $apkName
    apkSHA256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash
    sourceFileCount = $files.Count
}
$output = [System.IO.File]::Open($archivePath, [System.IO.FileMode]::Create)
$zip = $null
try {
    $zip = [System.IO.Compression.ZipArchive]::new($output, [System.IO.Compression.ZipArchiveMode]::Create, $true)
    foreach ($file in $files) {
        if (!$file.FullName.StartsWith($sourcePrefix, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Source path escaped project root.' }
        $relative = $file.FullName.Substring($sourcePrefix.Length).Replace('\', '/')
        if ($relative.StartsWith('../') -or [System.IO.Path]::IsPathRooted($relative)) { throw 'Source path escaped project root.' }
        $entry = $zip.CreateEntry($prefix + $relative, [System.IO.Compression.CompressionLevel]::Optimal)
        $input = [System.IO.File]::OpenRead($file.FullName)
        $destination = $entry.Open()
        try { $input.CopyTo($destination) }
        finally { $destination.Dispose(); $input.Dispose() }
    }
    $entry = $zip.CreateEntry($prefix + 'release-info.json')
    $writer = [System.IO.StreamWriter]::new($entry.Open(), [System.Text.UTF8Encoding]::new($false))
    try { $writer.WriteLine(($release | ConvertTo-Json)) }
    finally { $writer.Dispose() }
}
finally { if ($null -ne $zip) { $zip.Dispose() }; $output.Dispose() }
Write-Output "Source archive: $archivePath ($($files.Count) source files, plus release-info.json)"
