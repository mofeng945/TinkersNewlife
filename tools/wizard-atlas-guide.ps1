# 生成"巫师套装底图导览图"：把 128x128 底图放大 8 倍，标出每个方块的 UV 矩形 + 每个面的位置
#
# 用途：用户要**手绘**法袍贴图（"一个一个面画"）⇒ 必须知道哪个矩形是哪块方块的哪个面 ✓
# 数据来源：直接解析 WizardArmorModel.java 里的 addBox / addLocalBox 调用（模型是唯一权威 ✓）
# 输出：build\wizard-atlas-guide-x8.png（导览图 ✓）、build\wizard-atlas-regions.txt（区域清单 ✓）
#
# 面布局（与 tools\convert-robe-model.ps1 的绘制代码一致 ✓，标准 MC 盒式 UV ✓）：
#   rect 左上角 (u,v)，方块尺寸 w×h×d：
#     顶 up   = x[u+d, u+d+w]      y[v, v+d]          宽w 高d
#     底 down = x[u+d+w, u+d+2w]   y[v, v+d]          宽w 高d   （注意：底在顶的右边 ✓ 匠魂画法 ✓）
#     东 east = x[u, u+d]          y[v+d, v+d+h]      宽d 高h
#     北 north= x[u+d, u+d+w]      y[v+d, v+d+h]      宽w 高h
#     西 west = x[u+d+w, u+d+w+d]  y[v+d, v+d+h]      宽d 高h
#     南 south= x[u+d+w+d, u+2d+2w] y[v+d, v+d+h]     宽w 高h
#   整块矩形总宽 = 2d+2w、总高 = d+h ✓
param(
    [string]$Java = 'src\main\java\com\mofengbaizhi\tinkersnewlife\client\model\WizardArmorModel.java',
    [int]$Zoom = 8
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$atlasPath = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard\grey.png'
$outDir = Join-Path $root 'build'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

$src = [System.IO.File]::ReadAllLines((Join-Path $root $Java), [System.Text.Encoding]::UTF8)
$boxes = @()

# ① 法帽：addBox(head, "名", fromX, fromY, fromZ, toX, toY, toZ, u, v) —— 尺寸要乘 HAT_SCALE(0.8) ✓
$rxHat = 'addBox\(\w+, "([^"]+)",\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*(\d+),\s*(\d+)\)'
foreach ($line in $src) {
    $m = [regex]::Match($line, $rxHat)
    if ($m.Success) {
        $fx = [double]$m.Groups[2].Value; $fz = [double]$m.Groups[4].Value
        $tx = [double]$m.Groups[5].Value; $tz = [double]$m.Groups[7].Value
        $ty = [double]$m.Groups[6].Value; $fy = [double]$m.Groups[3].Value
        $boxes += [pscustomobject]@{
            Name = $m.Groups[1].Value; Kind = '帽'
            W = ($tx - $fx) * 0.8; H = ($ty - $fy) * 0.8; D = ($tz - $fz) * 0.8
            U = [int]$m.Groups[8].Value; V = [int]$m.Groups[9].Value
        }
    }
}
# ② 法袍：addLocalBox(父, "名", x, y, z, w, h, d, u, v) —— 尺寸已经是格 ✓
$rxLocal = 'addLocalBox\(\w+, "([^"]+)",\s*[-\d.]+F,\s*[-\d.]+F,\s*[-\d.]+F,\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F,\s*(\d+),\s*(\d+)\)'
foreach ($line in $src) {
    $m = [regex]::Match($line, $rxLocal)
    if ($m.Success) {
        $boxes += [pscustomobject]@{
            Name = $m.Groups[1].Value; Kind = '袍'
            W = [double]$m.Groups[2].Value; H = [double]$m.Groups[3].Value; D = [double]$m.Groups[4].Value
            U = [int]$m.Groups[5].Value; V = [int]$m.Groups[6].Value
        }
    }
}
# ③ 护腿 / 靴子占位（texOffs 写死在代码里 ✓）
$boxes += [pscustomobject]@{ Name = 'leg_wrap(占位)'; Kind = '占位'; W = 5.0; H = 10.0; D = 5.0; U = 96; V = 36 }
$boxes += [pscustomobject]@{ Name = 'boot_cuff(占位)'; Kind = '占位'; W = 6.0; H = 4.0; D = 6.0; U = 96; V = 52 }

# ---------- 生成导览图 ----------
$img = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($atlasPath))
$big = New-Object System.Drawing.Bitmap (128 * $Zoom), (128 * $Zoom)
$g = [System.Drawing.Graphics]::FromImage($big)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.Clear([System.Drawing.Color]::FromArgb(255, 20, 20, 26))
$g.DrawImage($img, 0, 0, 128 * $Zoom, 128 * $Zoom)

$fontSmall = New-Object System.Drawing.Font('Microsoft YaHei', 7)
$fontName = New-Object System.Drawing.Font('Microsoft YaHei', 9, [System.Drawing.FontStyle]::Bold)
$penRobe = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 255, 64, 64), 2)
$penHat = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 80, 160, 255), 2)
$penPh = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 80, 220, 120), 2)
$brFace = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 255, 240, 120))
$brName = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 255, 255, 255))

function Rect([double]$x, [double]$y, [double]$w, [double]$h) {
    New-Object System.Drawing.RectangleF ([single]($x * $Zoom)), ([single]($y * $Zoom)), ([single]($w * $Zoom)), ([single]($h * $Zoom))
}

$report = @()
foreach ($b in ($boxes | Sort-Object Kind, Name)) {
    $pen = if ($b.Kind -eq '袍') { $penRobe } elseif ($b.Kind -eq '帽') { $penHat } else { $penPh }
    $u = $b.U; $v = $b.V; $w = $b.W; $h = $b.H; $d = $b.D
    # 六个面（顺序：名、x0、y0、宽、高 ✓）
    $faces = @(
        @('顶', $u + $d, $v, $w, $d),
        @('底', $u + $d + $w, $v, $w, $d),
        @('东', $u, $v + $d, $d, $h),
        @('北', $u + $d, $v + $d, $w, $h),
        @('西', $u + $d + $w, $v + $d, $d, $h),
        @('南', $u + $d + $w + $d, $v + $d, $w, $h)
    )
    foreach ($f in $faces) {
        $r = Rect $f[1] $f[2] $f[3] $f[4]
        $g.DrawRectangle($pen, $r.X, $r.Y, $r.Width, $r.Height)
        if ($b.Kind -eq '袍') { $g.DrawString($f[0], $fontSmall, $brFace, ($r.X + 1), ($r.Y + 1)) }
    }
    # 整块矩形 + 名字（画在矩形上方一格）
    $all = Rect $u $v (2 * $d + 2 * $w) ($d + $h)
    $g.DrawRectangle($pen, $all.X, $all.Y, $all.Width, $all.Height)
    $g.DrawString($b.Name, $fontName, $brName, $all.X, [Math]::Max(0, $all.Y - 13))
    $report += ("{0,-24} {1}  矩形(u,v,w,h)=({2},{3},{4},{5})  尺寸w×h×d={6}×{7}×{8}" -f `
        $b.Name, $b.Kind, $u, $v, [Math]::Round(2 * $d + 2 * $w, 1), [Math]::Round($d + $h, 1), $w, $h, $d)
    $report += ("    顶(u+d,v,w,d)=({0},{1},{2},{3})  底=({4},{1},{2},{3})  东(u,v+d,d,h)=({5},{6},{3},{7})  北=({8},{6},{2},{7})  西=({9},{6},{3},{7})  南=({10},{6},{2},{7})" -f `
        [Math]::Round($u + $d, 1), $v, $w, $d, [Math]::Round($u + $d + $w, 1), $u, [Math]::Round($v + $d, 1), $h, `
        [Math]::Round($u + $d, 1), [Math]::Round($u + $d + $w, 1), [Math]::Round($u + $d + $w + $d, 1))
}

# 刻度：每 8 像素一条细线 + 数字（方便对着坐标画 ✓）
$penGrid = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(90, 255, 255, 255), 1)
$brTick = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 200, 200, 210))
for ($i = 0; $i -le 128; $i += 8) {
    $g.DrawLine($penGrid, ($i * $Zoom), 0, ($i * $Zoom), (128 * $Zoom))
    $g.DrawLine($penGrid, 0, ($i * $Zoom), (128 * $Zoom), ($i * $Zoom))
    if ($i % 16 -eq 0) {
        $g.DrawString([string]$i, $fontSmall, $brTick, ([Math]::Max(0, $i * $Zoom + 1)), 1)
        $g.DrawString([string]$i, $fontSmall, $brTick, 1, ([Math]::Max(0, $i * $Zoom + 1)))
    }
}
$g.Dispose()
$guide = Join-Path $outDir 'wizard-atlas-guide-x8.png'
$big.Save($guide, [System.Drawing.Imaging.ImageFormat]::Png)
$big.Dispose(); $img.Dispose()

$reportPath = Join-Path $outDir 'wizard-atlas-regions.txt'
[System.IO.File]::WriteAllLines($reportPath, $report, (New-Object Text.UTF8Encoding($false)))
Write-Host "导览图：$guide"
Write-Host "区域清单：$reportPath"
Write-Host ("方块数：{0}（袍 {1} / 帽 {2} / 占位 {3}）" -f $boxes.Count, ($boxes | Where-Object Kind -eq '袍').Count, ($boxes | Where-Object Kind -eq '帽').Count, ($boxes | Where-Object Kind -eq '占位').Count)
