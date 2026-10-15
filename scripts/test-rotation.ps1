param([string]$JavaHome=$env:JAVA_HOME,[Parameter(Mandatory=$true)][string]$MobileClasses,[Parameter(Mandatory=$true)][string]$AndroidJar)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskOut=Join-Path $taskRoot 'build/rotation-tests'
New-Item -ItemType Directory -Force $taskOut | Out-Null
$taskStubs=Get-ChildItem (Join-Path $taskRoot 'tests/android-stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -cp "$MobileClasses;$AndroidJar" -d $taskOut @taskStubs (Join-Path $taskRoot 'tests/CaptureRotationTests.java')
if($LASTEXITCODE -ne 0){throw 'Rotation harness compilation failed'}
& (Join-Path $JavaHome 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp "$taskOut;$MobileClasses;$AndroidJar" com.e7.bmfarm.CaptureRotationTests
if($LASTEXITCODE -ne 0){throw 'Rotation regression failed'}
