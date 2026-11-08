param([string]$Sdk = 'F:\android SDK', [string]$Jdk = 'C:\Program Files\Java\jdk-17')
$ErrorActionPreference = 'Stop'
$tools = Join-Path $Sdk 'build-tools/34.0.0'
$android = Join-Path $Sdk 'platforms/android-34/android.jar'
function Run-Tool($Executable, $Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with exit code $LASTEXITCODE" }
}
Push-Location $PSScriptRoot
$oldJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $Jdk
    [xml]$manifest = Get-Content -Raw app/src/main/AndroidManifest.xml
    $version = $manifest.DocumentElement.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
    $apk = "ColorTemperature-$version.apk"
    # Each invocation uses an empty output directory so stale classes/resources cannot enter the APK.
    $build = Join-Path $PSScriptRoot ('build/run-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force $build, "$build/classes", "$build/dex", "$build/generated", "$build/tests" | Out-Null
    Run-Tool "$Jdk/bin/javac.exe" @('-encoding', 'UTF-8', '--release', '8', '-d', "$build/tests",
        'app/src/main/java/cn/colortemperature/meter/CctRules.java',
        'app/src/main/java/cn/colortemperature/meter/RearLightRules.java', 'tests/CctRulesTest.java', 'tests/ResourceTest.java')
    Run-Tool "$Jdk/bin/java.exe" @('-cp', "$build/tests", 'cn.colortemperature.meter.CctRulesTest')
    Run-Tool "$Jdk/bin/java.exe" @('-cp', "$build/tests", 'cn.colortemperature.meter.ResourceTest')
    Run-Tool "$tools/aapt2.exe" @('compile', '--dir', 'app/src/main/res', '-o', "$build/resources.zip")
    Run-Tool "$tools/aapt2.exe" @('link', '-I', $android, '--manifest', 'app/src/main/AndroidManifest.xml',
        '--java', "$build/generated", '-o', "$build/unsigned.apk", "$build/resources.zip")
    $sources = Get-ChildItem app/src/main/java, "$build/generated" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
    Run-Tool "$Jdk/bin/javac.exe" (@('-encoding', 'UTF-8', '--release', '8', '-classpath', $android, '-d', "$build/classes") + $sources)
    Run-Tool "$Jdk/bin/jar.exe" @('cf', "$build/classes.jar", '-C', "$build/classes", '.')
    Run-Tool "$tools/d8.bat" @('--min-api', '30', '--lib', $android, '--output', "$build/dex", "$build/classes.jar")
    Push-Location "$build/dex"
    try { Run-Tool "$tools/aapt.exe" @('add', "$build/unsigned.apk", 'classes.dex') }
    finally { Pop-Location }
    Run-Tool "$tools/zipalign.exe" @('-f', '4', "$build/unsigned.apk", "$build/aligned.apk")
    $key = Join-Path $PSScriptRoot 'build/debug.keystore'
    if (!(Test-Path -LiteralPath $key)) {
        Run-Tool "$Jdk/bin/keytool.exe" @('-genkeypair', '-keystore', $key, '-storepass', 'android',
            '-keypass', 'android', '-alias', 'androiddebugkey', '-keyalg', 'RSA', '-keysize', '2048',
            '-validity', '10000', '-dname', 'CN=Color Temperature Debug,O=Local,C=CN')
    }
    Run-Tool "$tools/apksigner.bat" @('sign', '--ks', $key, '--ks-pass', 'pass:android', '--out', $apk, "$build/aligned.apk")
    Run-Tool "$tools/apksigner.bat" @('verify', '--verbose', $apk)
    Get-FileHash -LiteralPath $apk -Algorithm SHA256
    & (Join-Path $PSScriptRoot 'package-source.ps1')
}
finally { $env:JAVA_HOME = $oldJava; Pop-Location }
