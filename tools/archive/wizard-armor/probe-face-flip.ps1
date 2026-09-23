# 校验单个面：当前底图里的内容，等于"源图原样"还是"源图横向翻"？
# 用法：-Index <元素号> -Face <面名> -BoxName <我们方块名>
param(
    [string]$Json = "$env:USERPROFILE\Desktop\ghastlingSoundFix\wizard_robe.json",
    [string]$Png  = "$env:USERPROFILE\Desktop\ghastlingSoundFix\wizard_robe.png",
    [int]$Index = 1,
    [string]$Face = 'down',
    [string]$BoxName = 'body_plating_1',
    [double]$UvScale = 8.0
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
$j = [System.IO.File]::ReadAllText($Json, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$sheet = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($Png))
$atlas = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile((Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard\grey.png')))
$java = [System.IO.File]::ReadAllLines((Join-Path $root 'src\main\java\com\mofengbaizhi\tinkersnewlife\client\model\WizardArmorModel.java'), [System.Text.Encoding]::UTF8)
$box = $null
foreach ($l in $java) {
    $m = [regex]::Match($l, 'addLocalBox\(\w+, "([^"]+)",\s*[-\d.]+F,\s*[-\d.]+F,\s*[-\d.]+F,\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F,\s*(\d+),\s*(\d+)\)')
    if ($m.Success -and $m.Groups[1].Value -eq $BoxName) {
        $box = @{ W = [double]$m.Groups[2].Value; H = [double]$m.Groups[3].Value; D = [double]$m.Groups[4].Value; U = [double]$m.Groups[5].Value; V = [double]$m.Groups[6].Value }
    }
}
if (-not $box) { throw "没有 $BoxName ✗" }
$u = $box.U; $v = $box.V; $w = $box.W; $h = $box.H; $d = $box.D
# 源名 → 槽（§367 后的表 ✓）
$rect = switch ($Face) {
    'up'    { @(($u + $d), $v, $w, $d) }
    'down'  { @(($u + $d + $w), $v, $w, $d) }
    'west'  { @($u, ($v + $d), $d, $h) }
    'north' { @(($u + $d), ($v + $d), $w, $h) }
    'east'  { @(($u + $d + $w), ($v + $d), $d, $h) }
    'south' { @(($u + $d + $w + $d), ($v + $d), $w, $h) }
}
$fd = $j.elements[$Index].faces.$Face
$uv = $fd.uv
$sx0 = [Math]::Min([double]$uv[0], [double]$uv[2]) * $UvScale; $sx1 = [Math]::Max([double]$uv[0], [double]$uv[2]) * $UvScale
$sy0 = [Math]::Min([double]$uv[1], [double]$uv[3]) * $UvScale; $sy1 = [Math]::Max([double]$uv[1], [double]$uv[3]) * $UvScale
Write-Host ("源 uv=[{0}] 反向 u={1} v={2}  源矩形 x[{3}..{4}] y[{5}..{6}]  目标槽 x[{7:N1}..{8:N1}] y[{9:N1}..{10:N1}]" -f `
    ($uv -join ','), ([double]$uv[0] -gt [double]$uv[2]), ([double]$uv[1] -gt [double]$uv[3]), $sx0, $sx1, $sy0, $sy1, $rect[0], ($rect[0] + $rect[2]), $rect[1], ($rect[1] + $rect[3]))
function Ch($p) { if ($p.A -eq 0) { '.' } else { $g = [int](($p.R + $p.G + $p.B) / 3); if ($g -lt 64) { '#' } elseif ($g -lt 128) { '=' } elseif ($g -lt 192) { '+' } else { '-' } } }
Write-Host "假设A：不翻（源矩形原样贴过去）"
Write-Host "假设B：横向翻（u 反向）"
$matchA = 0; $matchB = 0; $n = 0
for ($py = [Math]::Floor($rect[1]); $py -lt [Math]::Ceiling($rect[1] + $rect[3]); $py++) {
    $rowA = '   '; $rowB = '   '; $rowC = '   '
    for ($px = [Math]::Floor($rect[0]); $px -lt [Math]::Ceiling($rect[0] + $rect[2]); $px++) {
        $tu = (($px + 0.5) - $rect[0]) / $rect[2]; $tv = (($py + 0.5) - $rect[1]) / $rect[3]
        $tu = [Math]::Max(0.0, [Math]::Min(1.0, $tu)); $tv = [Math]::Max(0.0, [Math]::Min(1.0, $tv))
        # A：不翻
        $ax = [int][Math]::Floor($sx0 + $tu * ($sx1 - $sx0)); $ay = [int][Math]::Floor($sy0 + $tv * ($sy1 - $sy0))
        $ax = [Math]::Max([Math]::Floor($sx0), [Math]::Min([Math]::Ceiling($sx1) - 1, $ax)); $ay = [Math]::Max([Math]::Floor($sy0), [Math]::Min([Math]::Ceiling($sy1) - 1, $ay))
        # B：横翻
        $bx = [int][Math]::Floor($sx0 + (1 - $tu) * ($sx1 - $sx0)); $by = $ay
        $bx = [Math]::Max([Math]::Floor($sx0), [Math]::Min([Math]::Ceiling($sx1) - 1, $bx))
        $ca = $sheet.GetPixel($ax, $ay); $cb = $sheet.GetPixel($bx, $by); $cc = $atlas.GetPixel($px, $py)
        $rowA += (Ch $ca); $rowB += (Ch $cb); $rowC += (Ch $cc)
        $n++
        if ($ca.ToArgb() -eq $cc.ToArgb()) { $matchA++ }
        if ($cb.ToArgb() -eq $cc.ToArgb()) { $matchB++ }
    }
    Write-Host ("A {0}   B {1}   底图 {2}" -f $rowA, $rowB, $rowC)
}
Write-Host ("像素 {0}：与假设A（不翻）一致 {1}，与假设B（横翻）一致 {2}" -f $n, $matchA, $matchB)
$sheet.Dispose(); $atlas.Dispose()
