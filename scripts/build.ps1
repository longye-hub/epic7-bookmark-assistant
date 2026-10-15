param(
 [ValidateSet('all','root','mobile')][string]$Variant='all',
 [string]$JavaHome=$env:JAVA_HOME,
 [string]$SdkRoot=$env:ANDROID_HOME,
 [string]$BuildToolsVersion='34.0.0',
 [string]$BuildToolsPath,
 [string]$RootAndroidJar,
 [string]$MobileAndroidJar
)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
if(!$JavaHome){throw 'Set JAVA_HOME or pass -JavaHome (JDK 17).'}
if(!$BuildToolsPath){if(!$SdkRoot){throw 'Set ANDROID_HOME or pass -SdkRoot.'};$BuildToolsPath=Join-Path $SdkRoot "build-tools/$BuildToolsVersion"}
if(!$RootAndroidJar -and $Variant -ne 'mobile'){$RootAndroidJar=Join-Path $SdkRoot 'platforms/android-33/android.jar'}
if(!$MobileAndroidJar -and $Variant -ne 'root'){$MobileAndroidJar=Join-Path $SdkRoot 'platforms/android-34/android.jar'}
$taskJava=Join-Path $JavaHome 'bin/java.exe'
$taskJavac=Join-Path $JavaHome 'bin/javac.exe'
$taskKeytool=Join-Path $JavaHome 'bin/keytool.exe'
function Invoke-Tool([string]$Exe,[string[]]$Arguments){
 & $Exe @Arguments
 if($LASTEXITCODE -ne 0){throw "Tool failed: $Exe (exit $LASTEXITCODE)"}
}
foreach($taskFile in @($taskJava,$taskJavac,$taskKeytool,(Join-Path $BuildToolsPath 'aapt.exe'),(Join-Path $BuildToolsPath 'zipalign.exe'),(Join-Path $BuildToolsPath 'lib/d8.jar'),(Join-Path $BuildToolsPath 'lib/apksigner.jar'))){if(!(Test-Path -LiteralPath $taskFile)){throw "Missing prerequisite: $taskFile"}}
$taskSigning=Join-Path $taskRoot '.local-signing'
New-Item -ItemType Directory -Force $taskSigning,(Join-Path $taskRoot 'dist') | Out-Null
$taskKey=Join-Path $taskSigning 'local-debug.jks'
# A new local debug key, never the publisher's key. Standard debug password only.
if(!(Test-Path -LiteralPath $taskKey)){
 Invoke-Tool $taskKeytool @('-genkeypair','-noprompt','-keystore',$taskKey,'-storepass','android','-keypass','android','-alias','local-debug','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Local Debug,O=Local Build,C=US')
}
$taskVariants=if($Variant -eq 'all'){@('root','mobile')}else{@($Variant)}
foreach($taskVariant in $taskVariants){
 $taskSource=Join-Path $taskRoot $(if($taskVariant -eq 'root'){'apksrc'}else{'mobile'})
 $taskAndroid=if($taskVariant -eq 'root'){$RootAndroidJar}else{$MobileAndroidJar}
 if(!(Test-Path -LiteralPath $taskAndroid)){throw "Missing Android platform: $taskAndroid"}
 [xml]$taskManifest=Get-Content -LiteralPath (Join-Path $taskSource 'AndroidManifest.xml') -Raw
 $taskVersion=$taskManifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
 $taskBuild=Join-Path $taskRoot ("build/$taskVariant-"+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
 $taskObj=Join-Path $taskBuild 'obj'
 New-Item -ItemType Directory -Force $taskObj | Out-Null
 $taskShared=Get-ChildItem (Join-Path $taskRoot 'apksrc/java/com/e7/bmfarm') -Filter '*.java'
 if($taskVariant -eq 'mobile'){
  $taskOverrides=Get-ChildItem (Join-Path $taskSource 'java/com/e7/bmfarm') -Filter '*.java'
  $taskSources=@($taskOverrides.FullName)+@($taskShared | Where-Object {$_.Name -notin $taskOverrides.Name -and $_.Name -ne 'ForegroundGuard.java'} | ForEach-Object FullName)
 }else{$taskSources=@($taskShared.FullName)}
 Write-Output "Build $taskVariant v$taskVersion"
 Invoke-Tool $taskJavac (@('-encoding','UTF-8','--release','8','-classpath',$taskAndroid,'-d',$taskObj)+$taskSources)
 if($taskVariant -eq 'mobile'){
  & (Join-Path $PSScriptRoot 'test-rotation.ps1') -JavaHome $JavaHome -MobileClasses $taskObj -AndroidJar $taskAndroid
 }
 $taskClasses=Get-ChildItem $taskObj -Filter '*.class' -Recurse | ForEach-Object FullName
 Invoke-Tool $taskJava (@('-cp',(Join-Path $BuildToolsPath 'lib/d8.jar'),'com.android.tools.r8.D8','--release','--min-api','26','--lib',$taskAndroid,'--output',$taskBuild)+$taskClasses)
 $taskBase=Join-Path $taskBuild 'base.apk'
 Invoke-Tool (Join-Path $BuildToolsPath 'aapt.exe') @('package','-f','-M',(Join-Path $taskSource 'AndroidManifest.xml'),'-I',$taskAndroid,'-A',(Join-Path $taskRoot 'apksrc/assets'),'-S',(Join-Path $taskSource 'res'),'-F',$taskBase)
 $taskZip=[IO.Compression.ZipFile]::Open($taskBase,[IO.Compression.ZipArchiveMode]::Update)
 try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip,(Join-Path $taskBuild 'classes.dex'),'classes.dex',[IO.Compression.CompressionLevel]::Optimal) | Out-Null}finally{$taskZip.Dispose()}
 $taskAligned=Join-Path $taskBuild 'aligned.apk'
 Invoke-Tool (Join-Path $BuildToolsPath 'zipalign.exe') @('-f','4',$taskBase,$taskAligned)
 $taskOut=Join-Path $taskRoot "dist/epic7-$taskVariant-v$taskVersion-local.apk"
 Invoke-Tool $taskJava @('-jar',(Join-Path $BuildToolsPath 'lib/apksigner.jar'),'sign','--ks',$taskKey,'--ks-key-alias','local-debug','--ks-pass','pass:android','--key-pass','pass:android','--out',$taskOut,$taskAligned)
 Invoke-Tool $taskJava @('-jar',(Join-Path $BuildToolsPath 'lib/apksigner.jar'),'verify','--verbose',$taskOut)
 Invoke-Tool (Join-Path $BuildToolsPath 'zipalign.exe') @('-c','4',$taskOut)
 Write-Output "BUILD_DONE $taskOut"
}
