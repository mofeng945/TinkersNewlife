# check-book-pages.ps1 - flag text pages that are too long for one Patchouli page.
# Patchouli TextLayouter: page text area is 156px tall and 116px wide. When the content is taller than
# that it either RESIZE-squeezes (letter spacing goes negative => visible overlap) or TRUNCATEs it.
# So a page must stay within ~156px of laid-out height (about 12 lines at lineHeight 13).
$ErrorActionPreference = 'Stop'
$D = [char]36
$base = Resolve-Path (Join-Path $PSScriptRoot '..\src\main\resources\assets\tinkersnewlife\patchouli_books\guide')
$MAX_W = 116      # px of text width
$MAX_H = 156      # px of text height
$LINE_H = 13      # px per line
$LINE_UNITS = 29  # half-width units per line (116 / 4)

function Get-Units([string]$s) {
    $u = 0
    foreach ($ch in $s.ToCharArray()) { if ([int]$ch -gt 0x2E7F) { $u += 2 } else { $u += 1 } }
    return $u
}

$over = 0
foreach ($lang in @('zh_cn', 'en_us')) {
    Get-ChildItem (Join-Path $base "$lang\entries") -File -Filter *.json | ForEach-Object {
        $f = $_
        $t = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($f.FullName))
        $idx = 0
        $page = 0
        while (($i = $t.IndexOf('"text"', $idx)) -ge 0) {
            $page++
            $q = $t.IndexOf(':', $i)
            $start = $t.IndexOf('"', $q) + 1
            $end = $t.IndexOf('"', $start)
            while ($end -gt 0 -and $t[$end - 1] -eq '\') { $end = $t.IndexOf('"', $end + 1) }
            $val = $t.Substring($start, $end - $start)
            $idx = $end
            if ($val.Length -eq 0) { continue }
            $parts = [regex]::Split($val, [regex]::Escape("$D(br2)") + '|' + [regex]::Escape("$D(br)"))
            $lines = 0
            foreach ($p in $parts) {
                $clean = [regex]::Replace($p, [regex]::Escape("$D(") + '[^\)]*\)', '')
                $u = Get-Units $clean
                if ($u -le 0) { continue }
                $lines += [Math]::Ceiling($u / $LINE_UNITS)
            }
            $h = $lines * $LINE_H
            if ($h -gt $MAX_H) {
                $over++
                Write-Output ("{0}/{1} page#{2}: est {3}px ({4} lines)  ratio={5:N2}" -f $lang, $f.BaseName, $page, $h, $lines, ($h / $MAX_H))
            }
        }
    }
}
Write-Output "OVERLONG PAGES: $over"
