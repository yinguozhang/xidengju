param([string]$ToolsRoot = (Join-Path $PSScriptRoot '..\..\work\android-tools'),[string]$JdkRoot=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $ToolsRoot){$jdk=(Get-ChildItem -LiteralPath $ToolsRoot -Recurse -Filter javac.exe | Select-Object -First 1).Directory.FullName}
elseif($JdkRoot){$jdk=Join-Path $JdkRoot 'bin'}else{throw 'Set JAVA_HOME or pass -JdkRoot.'}
$classes=Join-Path $PSScriptRoot 'app\build\rule-tests'
New-Item -ItemType Directory -Force $classes | Out-Null
$base=Join-Path $PSScriptRoot 'app\src\main\java\cn\xidengju\app'
& "$jdk\javac.exe" -encoding UTF-8 -d $classes "$base\SleepRecord.java" "$base\BlockRules.java" "$base\ReminderPlan.java" "$base\NightFlowRules.java" (Join-Path $PSScriptRoot 'tests\SleepRecordTest.java') (Join-Path $PSScriptRoot 'tests\BlockRulesTest.java') (Join-Path $PSScriptRoot 'tests\ReminderPlanTest.java') (Join-Path $PSScriptRoot 'tests\NightFlowRulesTest.java')
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
foreach($suite in @('SleepRecordTest','BlockRulesTest','ReminderPlanTest','NightFlowRulesTest')){
    & "$jdk\java.exe" -cp $classes "cn.xidengju.app.$suite"
    if($LASTEXITCODE -ne 0){throw "Test failed: $suite"}
}
