# check-book-links.ps1 - scan all $(l:...) links in our Patchouli book and report broken targets.
# Usage: powershell -ExecutionPolicy Bypass -File tools\check-book-links.ps1
# NOTE: $(l:https://...) / $(l:http://...) are web links, not entry links => skipped.
$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot '..\src\main\resources\assets\tinkersnewlife\patchouli_books\guide'
$root = (Resolve-Path $root).Path

$langs = @('zh_cn', 'en_us')
$bad = 0
$total = 0
$web = 0
$report = New-Object System.Collections.ArrayList

foreach ($lang in $langs) {
    $entriesDir = Join-Path $root "$lang\entries"
    $catsDir = Join-Path $root "$lang\categories"
    $entryIds = @{}
    Get-ChildItem $entriesDir -File -Filter *.json | ForEach-Object { $entryIds[$_.BaseName] = $true }
    $catIds = @{}
    Get-ChildItem $catsDir -File -Filter *.json | ForEach-Object { $catIds[$_.BaseName] = $true }

    $scanFiles = @()
    $scanFiles += (Get-ChildItem $entriesDir -File -Filter *.json)
    $bookJson = Join-Path $root "$lang\book.json"
    if (Test-Path $bookJson) { $scanFiles += (Get-Item $bookJson) }

    foreach ($f in $scanFiles) {
        $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
        $txt = [System.Text.Encoding]::UTF8.GetString($bytes)
        $m = [regex]::Matches($txt, '\$\(l:([^\)\]\$]+)')
        foreach ($x in $m) {
            $target = $x.Groups[1].Value.Trim()
            if ($target -match '^https?://') { $web++; continue }
            $total++
            $ok = $entryIds.ContainsKey($target) -or $catIds.ContainsKey($target)
            if (-not $ok) {
                $bad++
                [void]$report.Add("$lang/$($f.Name) -> '$target'")
            }
        }
    }
}

Write-Output "LINKS entry/category=$total broken=$bad web=$web"
if ($bad -gt 0) { $report | ForEach-Object { Write-Output "  BROKEN $_" } }
exit 0
