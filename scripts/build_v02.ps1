#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateRange(15,2147483647)][int]$VersionCode,
    [Parameter(Mandatory)][ValidatePattern('^[0-9A-Za-z.-]+$')][string]$VersionName,
    [string]$AndroidSdkRoot=$env:ANDROID_SDK_ROOT,
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$PythonExecutable='python',
    [switch]$Optimize=$true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$projectRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$appRoot = Join-Path $projectRoot 'app'
$buildRoot = Join-Path $appRoot 'build'
$artifactsRoot = Join-Path $projectRoot 'artifacts'
$logsRoot = Join-Path $projectRoot 'logs'
$sdkRoot = if($AndroidSdkRoot){$AndroidSdkRoot}else{Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
$buildTools = Join-Path $sdkRoot 'build-tools\36.0.0'
$androidJar = Join-Path $sdkRoot 'platforms\android-34\android.jar'
if(-not $JavaHome){throw 'Set JAVA_HOME or pass -JavaHome for JDK 17.'}
$jdkBin = Join-Path $JavaHome 'bin'
$aapt2 = Join-Path $buildTools 'aapt2.exe'
$aapt = Join-Path $buildTools 'aapt.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$d8Jar = Join-Path $buildTools 'lib\d8.jar'
$apksignerJar = Join-Path $buildTools 'lib\apksigner.jar'
$java = Join-Path $jdkBin 'java.exe'
$javac = Join-Path $jdkBin 'javac.exe'
$jar = Join-Path $jdkBin 'jar.exe'
$keytool = Join-Path $jdkBin 'keytool.exe'
$manifest = Join-Path $appRoot 'AndroidManifest.xml'
$resourceInputs = Join-Path $appRoot 'res'
$resources = Join-Path $buildRoot 'resources'
$sources = Join-Path $appRoot 'src'
$compiledResources = Join-Path $buildRoot 'compiled-res.zip'
$unsignedApk = Join-Path $buildRoot 'TwinGrid-unsigned.apk'
$alignedApk = Join-Path $buildRoot 'TwinGrid-aligned.apk'
$classesRoot = Join-Path $buildRoot 'classes'
$generatedRoot = Join-Path $buildRoot 'generated'
$dexRoot = Join-Path $buildRoot 'dex'
$candidateRoot = Join-Path $artifactsRoot 'candidates'
$null = New-Item -ItemType Directory -Path $candidateRoot -Force
$outputApk = Join-Path $candidateRoot ('TwinGrid-'+$VersionName+'-code'+$VersionCode+'-unsigned.apk')
if(Test-Path -LiteralPath $outputApk){throw 'Build output already exists. Preserve it or use a new checkout; never overwrite frozen APK bytes.'}
$buildReport = Join-Path $artifactsRoot 'build_report.json'
$logPath = Join-Path $logsRoot 'build_v02.log'

function Invoke-CheckedProcess {
    param(
        [Parameter(Mandatory)] [string] $FileName,
        [Parameter(Mandatory)] [string[]] $Arguments,
        [string] $WorkingDirectory,
        [hashtable] $Environment=@{}
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FileName
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = $utf8NoBom
    $startInfo.StandardErrorEncoding = $utf8NoBom
    foreach($name in $Environment.Keys){$startInfo.Environment[$name]=[string]$Environment[$name]}
    if ($WorkingDirectory) {
        $startInfo.WorkingDirectory = $WorkingDirectory
    }
    foreach ($argument in $Arguments) {
        $startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::Start($startInfo)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $result = [pscustomobject]@{
        ExitCode = $process.ExitCode
        Stdout = $stdoutTask.GetAwaiter().GetResult()
        Stderr = $stderrTask.GetAwaiter().GetResult()
    }
    if ($result.ExitCode -ne 0) {
        $message = @(
            "Process failed ($($result.ExitCode)): $FileName (arguments omitted to protect signing credentials)"
            $result.Stdout
            $result.Stderr
        ) -join [Environment]::NewLine
        throw $message
    }
    return $result
}

foreach ($requiredFile in @(
    $aapt2,
    $aapt,
    $zipalign,
    $d8Jar,
    $apksignerJar,
    $java,
    $javac,
    $jar,
    $keytool,
    $androidJar,
    $manifest
)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required build input is missing: $requiredFile"
    }
}
foreach ($requiredDirectory in @($resourceInputs, $sources)) {
    if (-not (Test-Path -LiteralPath $requiredDirectory -PathType Container)) {
        throw "Required source directory is missing: $requiredDirectory"
    }
}

$resolvedApp = [System.IO.Path]::GetFullPath($appRoot).TrimEnd('\')
$resolvedBuild = [System.IO.Path]::GetFullPath($buildRoot)
if (-not $resolvedBuild.StartsWith(
    $resolvedApp + '\',
    [System.StringComparison]::OrdinalIgnoreCase
)) {
    throw "Refusing to clean build path outside the app: $resolvedBuild"
}
if (Test-Path -LiteralPath $resolvedBuild) {
    Remove-Item -LiteralPath $resolvedBuild -Recurse -Force
}
foreach ($directory in @(
    $buildRoot,
    $artifactsRoot,
    $logsRoot,
    $classesRoot,
    $generatedRoot,
    $dexRoot
)) {
    $null = New-Item -ItemType Directory -Path $directory -Force
}

$python = $PythonExecutable
# javac's Windows zipfs close resolves the SDK jar through a restricted parent.
# Compile against a hash-identical local copy; never modify the installed SDK.
$sdkJarInput = $androidJar
$androidJar = Join-Path $buildRoot 'android-sdk.jar'
Copy-Item -LiteralPath $sdkJarInput -Destination $androidJar
if((Get-FileHash -LiteralPath $sdkJarInput -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $androidJar -Algorithm SHA256).Hash){throw 'SDK copy hash mismatch'}
$gate = Invoke-CheckedProcess -FileName $python -Arguments @((Join-Path $PSScriptRoot 'final_resource_gate.py'))
$resourceWhitelist = @('drawable/ic_launcher.xml','raw/nds_catalog.sqlite','raw/nds_catalog_manifest.json','raw/nds_pinyin.txt','raw/runtime_licenses.txt','raw/fusion_pixel_license.txt','font/fusion_pixel_10.ttf','values/colors.xml','values/strings.xml','values/styles.xml','values-zh/strings.xml','xml/locales_config.xml')
$resourceWhitelist += @('raw/cover_index.json','raw/cover_index_manifest.json')
$resourceWhitelist += @('values/code112_catalog_strings.xml','values-en/code112_catalog_strings.xml')
$resourceWhitelist += @('values/journey115.xml','values-zh/journey115.xml','values-en/journey115.xml')
$resourceWhitelist += @('values/battery_strings.xml','values-zh/battery_strings.xml')
foreach ($relative in $resourceWhitelist) {
    $resourceTarget = Join-Path $resources $relative
    $null = New-Item -ItemType Directory -Path (Split-Path -Parent $resourceTarget) -Force
    Copy-Item -LiteralPath (Join-Path $resourceInputs $relative) -Destination $resourceTarget
}

$overviewTarget = Join-Path $resources 'raw/overview_copy.json'
Copy-Item -LiteralPath (Join-Path $resourceInputs 'raw/overview_copy.json') -Destination $overviewTarget

$startedAt = Get-Date
[System.IO.File]::AppendAllText(
    $logPath,
    "[$($startedAt.ToString('o'))] Build started.$([Environment]::NewLine)",
    $utf8NoBom
)

$null = Invoke-CheckedProcess -FileName $aapt2 -Arguments @(
    'compile',
    '--dir',
    $resources,
    '-o',
    $compiledResources
)
$null = Invoke-CheckedProcess -FileName $aapt2 -Arguments @(
    'link',
    '-o',
    $unsignedApk,
    '--manifest',
    $manifest,
    '-I',
    $androidJar,
    '--java',
    $generatedRoot,
    '--min-sdk-version',
    '26',
    '--target-sdk-version',
    '34',
    '--version-code',
    [string]$VersionCode,
    '--version-name',
    $VersionName,
    '--auto-add-overlay',
    $compiledResources
)

$javaSources = @(
    Get-ChildItem -LiteralPath $sources -Recurse -File -Filter '*.java' |
        ForEach-Object FullName
)
$generatedSources = @(
    Get-ChildItem -LiteralPath $generatedRoot -Recurse -File -Filter '*.java' |
        ForEach-Object FullName
)
if ($javaSources.Count -eq 0 -or $generatedSources.Count -eq 0) {
    throw 'Java source or generated R.java was not found.'
}

$dependencyJars = @(Get-ChildItem -LiteralPath (Join-Path $appRoot 'libs') -File -Filter '*.jar' | ForEach-Object FullName)
$compileClassPath = (@($androidJar) + $dependencyJars) -join [System.IO.Path]::PathSeparator
$compileJavaArguments = @(
    '-J-Duser.language=en',
    '-J-Dfile.encoding=UTF-8',
    '-encoding',
    'UTF-8',
    '-source',
    '8',
    '-target',
    '8',
    '-classpath',
    $compileClassPath,
    '-d',
    $classesRoot
) + $javaSources + $generatedSources
$null = Invoke-CheckedProcess -FileName $javac -Arguments $compileJavaArguments

$classFiles = @(
    Get-ChildItem -LiteralPath $classesRoot -Recurse -File -Filter '*.class' |
        ForEach-Object FullName
)
if ($classFiles.Count -eq 0) {
    throw 'javac produced no class files.'
}
$d8Arguments = @(
    '-cp',
    $d8Jar,
    'com.android.tools.r8.D8',
    '--min-api',
    '26',
    '--output',
    $dexRoot
) + $classFiles + $dependencyJars
$null = Invoke-CheckedProcess -FileName $java -Arguments $d8Arguments

if ($Optimize) {
    $optimizationRoot = Join-Path $artifactsRoot ('size_trials\code'+$VersionCode)
    $null = New-Item -ItemType Directory -Path $optimizationRoot -Force
    $rules = Join-Path $appRoot 'proguard-rgds.pro'
    $runRules = Join-Path $optimizationRoot 'effective-rules.pro'
    $mappingPath = (Join-Path $optimizationRoot 'mapping.txt').Replace('\','/')
    $usagePath = (Join-Path $optimizationRoot 'usage.txt').Replace('\','/')
    [IO.File]::WriteAllText($runRules,([IO.File]::ReadAllText($rules)+"`n-printmapping $mappingPath`n-printusage $usagePath`n"),$utf8NoBom)
    $r8Arguments = @('-cp',$d8Jar,'com.android.tools.r8.R8','--release','--min-api','26','--lib',$androidJar,'--pg-conf',$runRules,'--output',$dexRoot) + $classFiles + $dependencyJars
    $r8Output = Invoke-CheckedProcess -FileName $java -Arguments $r8Arguments
    [IO.File]::WriteAllText((Join-Path $optimizationRoot 'r8.log'),[string]$r8Output,$utf8NoBom)
}

$classesDex = Join-Path $dexRoot 'classes.dex'
if (-not (Test-Path -LiteralPath $classesDex -PathType Leaf)) {
    throw 'D8 did not produce classes.dex.'
}
$assemblyArguments = @((Join-Path $PSScriptRoot 'final_apk_zip.py'),$unsignedApk,$classesDex)
if ($Optimize) {$assemblyArguments += '--optimized'}
$null = Invoke-CheckedProcess -FileName $python -Arguments $assemblyArguments
$null = Invoke-CheckedProcess -FileName $zipalign -Arguments @(
    '-f',
    '4',
    $unsignedApk,
    $alignedApk
)

# Public builds deliberately end before signing. Release APK bytes are frozen separately.
Copy-Item -LiteralPath $alignedApk -Destination $outputApk
$null = Invoke-CheckedProcess -FileName $zipalign -Arguments @('-c','4',$outputApk)
$badging = Invoke-CheckedProcess -FileName $aapt -Arguments @('dump','badging',$outputApk)
$null = Invoke-CheckedProcess -FileName $python -Arguments @((Join-Path $PSScriptRoot 'final_resource_gate.py'),$outputApk)
$hash=(Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash.ToLowerInvariant()
$report=[ordered]@{Status='PASS';Stage='PUBLIC_UNSIGNED_BUILD';VersionCode=$VersionCode;VersionName=$VersionName;Apk=[IO.Path]::GetRelativePath($projectRoot,$outputApk);SHA256=$hash;Bytes=(Get-Item -LiteralPath $outputApk).Length;SourceCount=$javaSources.Count;Optimized=[bool]$Optimize;Signed=$false;Badging=($badging.Stdout -split '\r?\n' | Select-Object -First 8)}
[IO.File]::WriteAllText($buildReport,($report|ConvertTo-Json -Depth 5),$utf8NoBom)
[IO.File]::WriteAllText($outputApk+'.sha256',$hash+'  '+[IO.Path]::GetFileName($outputApk)+[Environment]::NewLine,$utf8NoBom)
$report|ConvertTo-Json -Depth 5
$global:LASTEXITCODE=0
