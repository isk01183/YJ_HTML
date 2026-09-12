param(
    [string]$Html = "$PSScriptRoot\..\app\src\main\assets\magic_circle.html"
)

$source = Get-Content -Raw -LiteralPath $Html
$runes = [regex]::Match($source, '(?s)<textPath\b[^>]*>(.*?)</textPath>')
$seals = [regex]::Match($source, '(?s)<g class="seals".*?(?=<g class="glyph")')

if (-not $runes.Success) { throw "Rune textPath is missing." }
if ($runes.Value -notmatch 'textLength="2174"') { throw "Runes do not span the full circle." }
if ($runes.Value -notmatch 'lengthAdjust="spacing"') { throw "Rune spacing is not distributed evenly." }
if (($runes.Groups[1].Value | Select-String -AllMatches '古き星の記憶').Matches.Count -lt 2) {
    throw "Rune phrase is too short for a complete ring."
}
if (-not $seals.Success) { throw "Central seals are missing." }
if ($seals.Value -match 'class="[^"]*\btrace\b') { throw "Central seals still use a broken trace effect." }
if ($seals.Value -notmatch 'stroke-linejoin="round"') { throw "Central seal joins are not refined." }
if ($seals.Value -notmatch '<polygon class="gold octagram"') { throw "The octagram is not a connected polygon." }
if ($seals.Value -match '\borbit\b') { throw "Central seals must not rotate." }

Write-Output "MAGIC_CIRCLE_DESIGN_OK"
