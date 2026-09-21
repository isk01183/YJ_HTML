$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project=Split-Path $PSScriptRoot -Parent
$apk=Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'
if((Get-Item -LiteralPath $apk).Length -gt 50MB){throw 'Review APK unexpectedly large: inspect obsolete entries or incremental ZIP free space'}
$zip=[IO.Compression.ZipFile]::OpenRead($apk)
try {
    if(@($zip.Entries | Where-Object FullName -Like 'assets/collection/art/*').Count -ne 0){throw 'Unused contour archive must not ship in the direct-vector review APK'}
    foreach($name in @('direct-circles.js','direct-extra.js','gallery.html','gallery.js','collection_circle.html','collection-catalog.js','i18n.js')){
        $entry=$zip.GetEntry('assets/'+$name)
        if(!$entry){throw "Missing packaged asset: $name"}
        $reader=[IO.StreamReader]::new($entry.Open())
        try{$actual=$reader.ReadToEnd()}finally{$reader.Dispose()}
        $expected=[IO.File]::ReadAllText((Join-Path $project ('app/src/main/assets/'+$name)))
        if($actual -cne $expected){throw "Stale packaged asset: $name"}
    }
    if(@($zip.Entries | Where-Object FullName -Like 'assets/collection/thumbs/*.png').Count -ne 118){throw 'Existing thumbnail inventory must remain available'}
    Write-Output 'REVIEW_APK_OK: current shared renderers and UI packaged; no obsolete contour archives; 118 thumbnails preserved'
} finally {$zip.Dispose()}
