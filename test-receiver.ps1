param([string]$ToolsRoot = (Join-Path $PSScriptRoot '..\..\work\android-tools'),[string]$JdkRoot=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $ToolsRoot){$jdk=(Get-ChildItem -LiteralPath $ToolsRoot -Recurse -Filter javac.exe | Select-Object -First 1).Directory.FullName}
elseif($JdkRoot){$jdk=Join-Path $JdkRoot 'bin'}else{throw 'Set JAVA_HOME or pass -JdkRoot.'}
$classes=Join-Path $PSScriptRoot 'app\build\receiver-tests'
New-Item -ItemType Directory -Force $classes | Out-Null
$base=Join-Path $PSScriptRoot 'app\src\main\java\cn\xidengju\app'
$fakes=@(Get-ChildItem (Join-Path $PSScriptRoot 'tests\fakes') -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$jdk\javac.exe" -encoding UTF-8 -d $classes "$base\ReminderPlan.java" "$base\NightFlowRules.java" "$base\NightFlow.java" "$base\ReminderReceiver.java" "$base\RestoreReceiver.java" "$base\ReminderLog.java" (Join-Path $PSScriptRoot 'tests\ReminderReceiverTest.java') @fakes
if($LASTEXITCODE -ne 0){throw 'Integration test compilation failed'}
& "$jdk\java.exe" -cp $classes cn.xidengju.app.ReminderReceiverTest
if($LASTEXITCODE -ne 0){throw 'Integration tests failed'}
