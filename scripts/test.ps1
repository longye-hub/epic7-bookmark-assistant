param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(!$JavaHome){throw 'Set JAVA_HOME or pass -JavaHome (JDK 17).'}
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskOut=Join-Path $taskRoot 'build/public-tests'
New-Item -ItemType Directory -Force $taskOut | Out-Null
$taskFiles=@('apksrc/java/com/e7/bmfarm/UiSizing.java','apksrc/java/com/e7/bmfarm/MascotTimeline.java','mobile/java/com/e7/bmfarm/InputGuard.java','tests/LayoutTests.java','tests/MascotTimelineTests.java','tests/InputGuardTests.java') | ForEach-Object {Join-Path $taskRoot $_}
$taskFiles+=@('apksrc/java/com/e7/bmfarm/TransientFiles.java','apksrc/java/com/e7/bmfarm/UpdateInfo.java','mobile/java/com/e7/bmfarm/CapturePrivacyPolicy.java','tests/PrivacyTests.java','tests/UpdateInfoTests.java') | ForEach-Object {Join-Path $taskRoot $_}
$taskFiles+=@('apksrc/java/com/e7/bmfarm/UpdateTransport.java','tests/UpdateTransportTests.java') | ForEach-Object {Join-Path $taskRoot $_}
$taskFiles+=@('apksrc/java/com/e7/bmfarm/AnnouncementInfo.java','apksrc/java/com/e7/bmfarm/NoticeEndpoints.java','tests/AnnouncementTests.java') | ForEach-Object {Join-Path $taskRoot $_}
$taskFiles+=@('apksrc/java/com/e7/bmfarm/NoticeResult.java','tests/NoticeResultTests.java') | ForEach-Object {Join-Path $taskRoot $_}
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 --release 8 -d $taskOut @taskFiles
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
foreach($taskTest in @('LayoutTests','InputGuardTests','MascotTimelineTests','PrivacyTests','UpdateInfoTests','UpdateTransportTests','AnnouncementTests','NoticeResultTests')){
 [string[]]$taskArgs=@()
 if($taskTest -eq 'MascotTimelineTests'){$taskArgs=@((Join-Path $taskRoot 'tests/mascot-metadata.json'))}
 if($taskTest -eq 'UpdateInfoTests'){$taskArgs=@((Join-Path $taskRoot 'updates'))}
 & (Join-Path $JavaHome 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp $taskOut "com.e7.bmfarm.$taskTest" @taskArgs
 if($LASTEXITCODE -ne 0){throw "Test failed: $taskTest"}
}
