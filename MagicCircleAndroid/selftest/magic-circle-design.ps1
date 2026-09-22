param(
    [string]$Html = "$PSScriptRoot\..\app\src\main\assets\magic_circle.html"
)

$source = Get-Content -Raw -LiteralPath $Html
$i18n = Get-Content -Raw -LiteralPath (Join-Path (Split-Path $Html -Parent) 'i18n.js')
$runes = [regex]::Match($source, '(?s)<textPath\b[^>]*>(.*?)</textPath>')
$seals = [regex]::Match($source, '(?s)<g class="seals".*?(?=<g class="glyph")')

if (-not $runes.Success) { throw "Rune textPath is missing." }
if ($runes.Value -notmatch 'textLength="2174"') { throw "Runes do not span the full circle." }
if ($runes.Value -notmatch 'lengthAdjust="spacingAndGlyphs"') { throw "Rune spacing is not distributed evenly." }
if ($source -notmatch "MagicI18n\.t\('runeVerse',language\)") { throw "Rune phrase is not populated from the selected language." }
if ($i18n -notmatch "add\('runeVerse',\s*'[^']+',\s*'[^']+',\s*'[^']+'\)") { throw "Rune phrase is not available in all three languages." }
if (-not $seals.Success) { throw "Central seals are missing." }
if ($seals.Value -match 'class="[^"]*\btrace\b') { throw "Central seals still use a broken trace effect." }
if ($seals.Value -notmatch 'stroke-linejoin="round"') { throw "Central seal joins are not refined." }
if ($seals.Value -notmatch '<polygon class="gold octagram"') { throw "The octagram is not a connected polygon." }
if ($seals.Value -match '\borbit\b') { throw "Central seals must not rotate." }

Write-Output "MAGIC_CIRCLE_DESIGN_OK: browser/reference comparator"
