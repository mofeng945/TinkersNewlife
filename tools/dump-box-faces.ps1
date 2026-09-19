# 把某个方块的"用户源图六个面"与"我们底图六个面矩形"打成字符画，用于肉眼比对（'#'=不透明, '.'=透明, 字母=灰度档 ✓）
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_robe.json",
    [string]$Png  = "$env:USERPROFILE\Desktop\wizard_robe.png",
    [int]$Index = 7,
    [string]$BoxName = 'left_arm_maille_7',
    [double]$UvScale = 8.0
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
$j = [System.IO.File]::ReadAllText($Json, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$sheet = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($Png))
$grey = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile((Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard\grey.png')))

# 我们的方块（从 Java 读 w/h/d/u/v ✓）
$java = [System.IO.File]::ReadAllLines((Join-Path $root 'src\main\java\com\mofengbaizhi\tinkersnewlife\client\model\WizardArmorModel.java'), [System.Text.Encoding]::UTF8)
$box = $null
foreach ($l in $java) {
    $m = [regex]::Match($l, 'addLocalBox\(\w+, "([^"]+)",\s*[-\d.]+F,\s*[-\d.]+F,\s*[-\d.]+F,\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F,\s*(\d+),\s*(\d+)\)')
    if ($m.Success -and $m.Groups[1].Value -eq $BoxName) {
        $box = @{ W = [double]$m.Groups[2].Value; H = [double]$m.Groups[3].Value; D = [double]$m.Groups[4].Value; U = [double]$m.Groups[5].Value; V = [double]$m.Groups[6].Value }
    }
}
if (-not $box) { throw "Java 里没有 $BoxName ✗" }
$u = $box.U; $v = $box.V; $w = $box.W; $h = $box.H; $d = $box.D
# 面矩形（MC 真布局 ✓）
$target = @{
    down  = @(($u + $d + $w), $v, $w, $d)
    up    = @(($u + $d), $v, $w, $d)
    west  = @($u, ($v + $d), $d, $h)
    north = @(($u + $d), ($v + $d), $w, $h)
    east  = @(($u + $d + $w), ($v + $d), $d, $h)
    south = @(($u + $d + $w + $d), ($v + $d), $w, $h)
}
function Ch($p) {
    if ($p.A -eq 0) { return '.' }
    $g = [int](($p.R + $p.G + $p.B) / 3)
    if ($g -lt 64) { return '#' } elseif ($g -lt 128) { return '=' } elseif ($g -lt 192) { return '+' } else { return '-' }
}
"方块 $BoxName  w×h×d = $w×$h×$d   texOffs u=$u v=$v"
foreach ($fn in 'down', 'up', 'west', 'north', 'east', 'south') {
    $src = $j.elements[$Index].faces.$fn
    Write-Host ""
    Write-Host ("=== {0} ===" -f $fn)
    if ($src -and $src.uv) {
        $uv = $src.uv
        $x0 = [Math]::Min([double]$uv[0], [double]$uv[2]) * $UvScale; $x1 = [Math]::Max([double]$uv[0], [double]$uv[2]) * $UvScale
        $y0 = [Math]::Min([double]$uv[1], [double]$uv[3]) * $UvScale; $y1 = [Math]::Max([double]$uv[1], [double]$uv[3]) * $UvScale
        Write-Host ("  用户图 uv=[{0}] → 像素 x[{1}..{2}] y[{3}..{4}]  反向: u={5} v={6}" -f ($uv -join ','), $x0, $x1, $y0, $y1, ([double]$uv[0] -gt [double]$uv[2]), ([double]$uv[1] -gt [double]$uv[3]))
        for ($y = [Math]::Floor($y0); $y -lt [Math]::Ceiling($y1); $y++) {
            $row = '   '
            for ($x = [Math]::Floor($x0); $x -lt [Math]::Ceiling($x1); $x++) { $row += (Ch $sheet.GetPixel($x, $y)) }
            Write-Host $row
        }
    } else { Write-Host "  （用户模型里没有这个面）" }
    $t = $target[$fn]
    Write-Host ("  我们底图矩形 x[{0:N1}..{1:N1}] y[{2:N1}..{3:N1}]" -f $t[0], ($t[0] + $t[2]), $t[1], ($t[1] + $t[3]))
    for ($y = [Math]::Floor($t[1]); $y -lt [Math]::Ceiling($t[1] + $t[3]); $y++) {
        $row = '   '
        for ($x = [Math]::Floor($t[0]); $x -lt [Math]::Ceiling($t[0] + $t[2]); $x++) { $row += (Ch $grey.GetPixel($x, $y)) }
        Write-Host $row
    }
}
$sheet.Dispose(); $grey.Dispose()
