# 扫描整合包里所有 jar 的物品标签（data/<ns>/tags/items/**/*.json）
# 用法：powershell -ExecutionPolicy Bypass -File tools\scan-item-tags.ps1
# 输出：
#   build\tag-scan\refs.tsv   每行：标签id <TAB> 引用它的 jar 数 <TAB> jar 列表(;分隔)
#   build\tag-scan\values.txt 每个标签（按引用数降序）下所有 jar 中出现的值（去重、含 #标签引用）
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$pack = 'G:\tex\.minecraft\versions\1.20.1-Forge_47.4.22'
$out  = 'G:\TinkersNewlife\build\tag-scan'
if (-not (Test-Path $out)) { New-Item -ItemType Directory -Path $out -Force | Out-Null }

$jars = @()
$jars += (Join-Path $pack '1.20.1-Forge_47.4.22.jar')
$jars += (Get-ChildItem (Join-Path $pack 'mods') -Filter *.jar | Select-Object -ExpandProperty FullName)

$refs = @{}     # tagid -> HashSet of jarname
$values = @{}   # tagid -> HashSet of values

foreach ($jar in $jars) {
    $short = Split-Path $jar -Leaf
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        foreach ($e in $zip.Entries) {
            $n = $e.FullName
            if ($n -notmatch '^data/([^/]+)/tags/items/(.+)\.json$') { continue }
            $ns = $Matches[1]
            $tag = $ns + ':' + $Matches[2]
            if (-not $refs.ContainsKey($tag)) {
                $refs[$tag] = New-Object 'System.Collections.Generic.HashSet[string]'
                $values[$tag] = New-Object 'System.Collections.Generic.HashSet[string]'
            }
            [void]$refs[$tag].Add($short)
            $sr = New-Object System.IO.StreamReader($e.Open(), [System.Text.Encoding]::UTF8)
            $raw = $sr.ReadToEnd()
            $sr.Close()
            try { $json = $raw | ConvertFrom-Json } catch { continue }
            if ($null -ne $json.values) {
                foreach ($v in $json.values) { if ($v -is [string]) { [void]$values[$tag].Add($v) } }
            }
        }
    } finally { $zip.Dispose() }
}

$sorted = $refs.Keys | Sort-Object { -$refs[$_].Count }, { $_ }

$lines = New-Object System.Collections.Generic.List[string]
foreach ($t in $sorted) {
    $lines.Add(($t + "`t" + $refs[$t].Count + "`t" + (($refs[$t] | Sort-Object) -join ';')))
}
[System.IO.File]::WriteAllLines((Join-Path $out 'refs.tsv'), $lines, (New-Object System.Text.UTF8Encoding($false)))

$vl = New-Object System.Collections.Generic.List[string]
foreach ($t in $sorted) {
    $vl.Add('=== ' + $t + '  (' + $refs[$t].Count + ' jars: ' + (($refs[$t] | Sort-Object) -join ', ') + ')')
    foreach ($v in ($values[$t] | Sort-Object)) { $vl.Add('    ' + $v) }
    $vl.Add('')
}
[System.IO.File]::WriteAllLines((Join-Path $out 'values.txt'), $vl, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ('jars scanned: ' + $jars.Count)
Write-Host ('distinct item tags: ' + $refs.Count)
Write-Host '--- top 60 ---'
$i = 0
foreach ($t in $sorted) {
    if ($i -ge 60) { break }
    Write-Host (('{0,4}  {1,-70} {2}' -f $refs[$t].Count, $t, (($refs[$t] | Sort-Object) -join ',')))
    $i++
}
