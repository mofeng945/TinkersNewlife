# 巫师套装底图（grey.png）区域图 + 对照表
#
# 作用：直接读 WizardArmorModel.java 里的 addBox(...) 调用，算出每个方块在 128x128 底图上
#       占用的"盒式 UV 布局"范围与六个面的具体矩形，画成一张放大 4 倍的标注图，并打印对照表。
#       ⇒ 用户自己改底图时，一眼知道哪块像素属于哪个方块的哪个面 ✓（不用手算 ✗）。
#
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\wizard-atlas-map.ps1
# 输出：docs\wizard-atlas-map.png（4 倍放大，带格子与标签 ✓）

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$java = Join-Path $root 'src\main\java\com\mofengbaizhi\tinkersnewlife\client\model\WizardArmorModel.java'
$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard'
$grey = Join-Path $texDir 'grey.png'
$outDir = Join-Path $root 'docs'
$outPng = Join-Path $outDir 'wizard-atlas-map.png'

$src = [System.IO.File]::ReadAllText($java, [System.Text.Encoding]::UTF8)

$m = [regex]::Match($src, 'HAT_SCALE\s*=\s*([\d.]+)F')
if (-not $m.Success) { Write-Host "没找到 HAT_SCALE"; exit 1 }
$S = [double]$m.Groups[1].Value
Write-Host "HAT_SCALE = $S"

# addBox(head, "名字", fromX, fromY, fromZ, toX, toY, toZ, u, v)
$rx = [regex]'addBox\(head,\s*"([a-z_0-9]+)",\s*(-?[\d.]+)F,\s*(-?[\d.]+)F,\s*(-?[\d.]+)F,\s*(-?[\d.]+)F,\s*(-?[\d.]+)F,\s*(-?[\d.]+)F,\s*(\d+),\s*(\d+)\);'
$cubes = @()
foreach ($mm in $rx.Matches($src)) {
    $g = $mm.Groups
    $w = ([double]$g[5].Value - [double]$g[2].Value) * $S
    $h = ([double]$g[6].Value - [double]$g[3].Value) * $S
    $d = ([double]$g[7].Value - [double]$g[4].Value) * $S
    $u = [int]$g[8].Value; $v = [int]$g[9].Value
    $cubes += [pscustomobject]@{
        Name = $g[1].Value; U = $u; V = $v; W = $w; H = $h; D = $d
        BoxW = [Math]::Round(2 * $w + 2 * $d, 2); BoxH = [Math]::Round($h + $d, 2)
    }
}

Write-Host ""
Write-Host ("{0,-4} {1,-20} {2,5} {3,5} {4,7} {5,7} {6,7} {7,10} {8,10}" -f '#', '方块', 'u', 'v', 'w', 'h', 'd', '布局宽', '布局高')
$idx = 0
foreach ($c in $cubes) {
    $idx++
    Write-Host ("{0,-4} {1,-20} {2,5} {3,5} {4,7:N2} {5,7:N2} {6,7:N2} {7,10:N2} {8,10:N2}" -f $idx, $c.Name, $c.U, $c.V, $c.W, $c.H, $c.D, $c.BoxW, $c.BoxH)
}

# ---------- 画图 ----------
$scale = 6
$img = New-Object System.Drawing.Bitmap (128 * $scale), (128 * $scale)
$gr = [System.Drawing.Graphics]::FromImage($img)
$gr.Clear([System.Drawing.Color]::FromArgb(255, 24, 24, 28))
$gr.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$gr.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

$base = [System.Drawing.Image]::FromFile($grey)
$gr.DrawImage($base, 0, 0, 128 * $scale, 128 * $scale)
$base.Dispose()

# 面 → 颜色
$faceColor = @{
    'up'    = [System.Drawing.Color]::FromArgb(90, 255, 80, 80)
    'down'  = [System.Drawing.Color]::FromArgb(90, 80, 140, 255)
    'east'  = [System.Drawing.Color]::FromArgb(90, 90, 220, 120)
    'north' = [System.Drawing.Color]::FromArgb(90, 255, 210, 60)
    'west'  = [System.Drawing.Color]::FromArgb(90, 220, 90, 220)
    'south' = [System.Drawing.Color]::FromArgb(90, 60, 230, 230)
}
$font = New-Object System.Drawing.Font 'Consolas', 9
$fontSmall = New-Object System.Drawing.Font 'Consolas', 7
$white = [System.Drawing.Brushes]::White

$n = 0
foreach ($c in $cubes) {
    $n++
    $u = $c.U; $v = $c.V; $w = $c.W; $h = $c.H; $d = $c.D
    $faces = @(
        @{ n = 'up';    x = $u + $d;         y = $v;     w = $w; h = $d },
        @{ n = 'down';  x = $u + $d + $w;    y = $v;     w = $w; h = $d },
        @{ n = 'east';  x = $u;              y = $v + $d; w = $d; h = $h },
        @{ n = 'north'; x = $u + $d;         y = $v + $d; w = $w; h = $h },
        @{ n = 'west';  x = $u + $d + $w;    y = $v + $d; w = $d; h = $h },
        @{ n = 'south'; x = $u + $d + $w + $d; y = $v + $d; w = $w; h = $h }
    )
    foreach ($f in $faces) {
        $x = [int][Math]::Round($f.x * $scale); $y = [int][Math]::Round($f.y * $scale)
        $ww = [Math]::Max(1, [int][Math]::Round($f.w * $scale)); $hh = [Math]::Max(1, [int][Math]::Round($f.h * $scale))
        $brush = New-Object System.Drawing.SolidBrush $faceColor[$f.n]
        $gr.FillRectangle($brush, $x, $y, $ww, $hh)
        $brush.Dispose()
    }
    # 布局外框 + 编号徽章（编号与上面打印的表格一致 ✓；名字在小格子里会糊成一团 ✗ ⇒ 只画编号 ✓）
    $bx = [int][Math]::Round($u * $scale); $by = [int][Math]::Round($v * $scale)
    $bw = [Math]::Max(2, [int][Math]::Round($c.BoxW * $scale)); $bh = [Math]::Max(2, [int][Math]::Round($c.BoxH * $scale))
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(200, 255, 255, 255)), 1
    $gr.DrawRectangle($pen, $bx, $by, $bw, $bh)
    $pen.Dispose()
    $badge = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(230, 255, 220, 40))
    $gr.FillRectangle($badge, ($bx + 1), ($by + 1), 15, 13)
    $badge.Dispose()
    $gr.DrawString([string]$n, $fontSmall, [System.Drawing.Brushes]::Black, ($bx + 3), ($by + 2))
}

# 100 行以下"自由区"提示
$penFree = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(160, 120, 255, 120)), 1
$gr.DrawRectangle($penFree, 0, (100 * $scale), (128 * $scale - 1), (28 * $scale - 1))
$gr.DrawString('FREE (rows 100-127)', $font, [System.Drawing.Brushes]::LightGreen, 4, (101 * $scale))
$penFree.Dispose()

$gr.Dispose()
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$img.Save($outPng, [System.Drawing.Imaging.ImageFormat]::Png)
$img.Dispose()
Write-Host ""
Write-Host "已输出：$outPng"
