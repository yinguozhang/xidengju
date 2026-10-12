param([string]$ToolsRoot = (Join-Path $PSScriptRoot '..\..\work\android-tools'),[string]$JdkRoot=$env:JAVA_HOME,[string]$SdkRoot=$env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
& node (Join-Path $PSScriptRoot 'prepare-assets.js')
if ($LASTEXITCODE -ne 0) { throw 'Asset preparation failed' }
if(Test-Path -LiteralPath $ToolsRoot){
    $ToolsRoot = (Resolve-Path $ToolsRoot).Path
    $jdk = (Get-ChildItem -LiteralPath $ToolsRoot -Filter javac.exe -Recurse | Select-Object -First 1).Directory.Parent.FullName
    $buildTools = (Get-ChildItem -LiteralPath $ToolsRoot -Filter aapt2.exe -Recurse | Select-Object -First 1).DirectoryName
    $androidJar = (Get-ChildItem -LiteralPath $ToolsRoot -Filter android.jar -Recurse | Select-Object -First 1).FullName
}else{
    if(!$JdkRoot -or !$SdkRoot){throw 'Set JAVA_HOME and ANDROID_HOME, or pass -JdkRoot and -SdkRoot.'}
    $jdk=$JdkRoot
    $buildTools=Join-Path $SdkRoot 'build-tools\35.0.0'
    $androidJar=Join-Path $SdkRoot 'platforms\android-35\android.jar'
    $ToolsRoot=Join-Path $PSScriptRoot '.local-signing'
    New-Item -ItemType Directory -Force $ToolsRoot | Out-Null
}
if (!$jdk -or !$buildTools -or !$androidJar) { throw 'Missing extracted JDK, Android Build Tools, or platform.' }
foreach($required in @((Join-Path $jdk 'bin\javac.exe'),(Join-Path $buildTools 'aapt2.exe'),$androidJar)){if(!(Test-Path -LiteralPath $required)){throw "Missing required build input: $required"}}
$build = Join-Path $PSScriptRoot 'app\build\local'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
New-Item -ItemType Directory -Force $classes,$dex | Out-Null
function Run([string]$Exe,[string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Exe" }
}
$sources = @(Get-ChildItem (Join-Path $PSScriptRoot 'app\src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
Run "$jdk\bin\javac.exe" (@('-encoding','UTF-8','-source','17','-target','17','-classpath',$androidJar,'-d',$classes) + $sources)
$classFiles = @(Get-ChildItem $classes -Recurse -Filter '*.class' | ForEach-Object FullName)
Run "$jdk\bin\java.exe" (@('-cp',"$buildTools\lib\d8.jar",'com.android.tools.r8.D8','--min-api','26','--lib',$androidJar,'--output',$dex) + $classFiles)
$unsigned = Join-Path $build 'unsigned.apk'
$manifestPath = Join-Path $build 'AndroidManifest.xml'
[xml]$manifest = Get-Content -Raw (Join-Path $PSScriptRoot 'app\src\main\AndroidManifest.xml')
$manifest.manifest.SetAttribute('package','cn.xidengju.app')
$manifest.Save($manifestPath)
$resources = Join-Path $build 'resources.zip'
Run "$buildTools\aapt2.exe" @('compile','--dir',(Join-Path $PSScriptRoot 'app\src\main\res'),'-o',$resources)
Run "$buildTools\aapt2.exe" @('link','-I',$androidJar,'--manifest',$manifestPath,'--min-sdk-version','26','--target-sdk-version','35','--version-code','10','--version-name','0.8.2-dev','-A',(Join-Path $PSScriptRoot 'app\src\main\assets'),'-0','mp3','-o',$unsigned,$resources)
Run "$jdk\bin\jar.exe" @('uf',$unsigned,'-C',$dex,'classes.dex')
# Windows aapt2 may emit asset entry names with backslashes. Android AssetManager
# requires forward slashes. Normalize before alignment/signing, storing media raw.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($unsigned,[System.IO.Compression.ZipArchiveMode]::Update)
try {
    $incorrect = @($archive.Entries | Where-Object { $_.FullName.Contains('\') })
    foreach ($entry in $incorrect) {
        $fixed = $archive.CreateEntry($entry.FullName.Replace('\','/'),[System.IO.Compression.CompressionLevel]::NoCompression)
        $inputStream = $entry.Open()
        $outputStream = $fixed.Open()
        try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
        $entry.Delete()
    }
} finally { $archive.Dispose() }
$aligned = Join-Path $build 'aligned.apk'
Run "$buildTools\zipalign.exe" @('-f','4',$unsigned,$aligned)
$key = Join-Path $ToolsRoot 'lightsout-development.p12'
if (!(Test-Path -LiteralPath $key)) {
    Run "$jdk\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=LightsOut Development','-keyalg','RSA','-keysize','2048','-validity','10000','-storetype','PKCS12')
}
$output = Join-Path $PSScriptRoot '..\熄灯局-Android开发版.apk'
Run "$jdk\bin\java.exe" @('-jar',"$buildTools\lib\apksigner.jar",'sign','--ks',$key,'--ks-pass','pass:android','--out',$output,$aligned)
Run "$jdk\bin\java.exe" @('-jar',"$buildTools\lib\apksigner.jar",'verify','--verbose',$output)
Run "$buildTools\aapt2.exe" @('dump','badging',$output)
Write-Output "APK: $output"
