# 把"用户手绘贴图"上的每个 UV 岛标出来（画方块编号），方便核对"哪块像素属于哪个方块的哪个面"
#
# 为什么需要：导入转换时若某个面看起来不对（发黑 / 接缝不齐 / 图案错位），
#   第一步永远是**先确认那个面在你画里取的是哪块像素** ✓ —— 这张图把 13 个方块的 54 个面
#   逐个框出来并编号（同一方块的面用同一个编号 ✓），配上脚本打印的坐标表就能一目了然 ✓。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\map-paint-islands.ps1 `
#       -Json <导出的 json> -Texture <你画的 png> [-Zoom 6] [-UvScale 8]
# 输出：build\paint-islands.png（放大后的贴图 + 编号框）+ 控制台坐标表

param(
    [Parameter(Mandatory = $true)][string]$Json,
    [Parameter(Mandatory = $true)][string]$Texture,
    [int]$Zoom = 6,
    [double]$UvScale = 0     # 0 = 自动（按 UV 包围盒 ↔ 贴图内容包围盒 推定 ✓）
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
$buildDir = Join-Path $root 'build'
if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir | Out-Null }

$j = [System.IO.File]::ReadAllText((Resolve-Path $Json), [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$img = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile((Resolve-Path $Texture)))

# 面 → uv 字段名
$names = 'up', 'down', 'east', 'north', 'west', 'south'
$cubes = @()
for ($i = 0; $i -lt $j.elements.Count; $i++) {
    $e = $j.elements[$i]
    $rot = 0.0; if ($e.rotation) { $rot = [double]$e.rotation.angle }
    $w = [double]$e.to[0] - [double]$e.from[0]
    $h = [double]$e.to[1] - [double]$e.from[1]
    $d = [double]$e.to[2] - [double]$e.from[2]
    $cubes += [pscustomobject]@{ Index = $i; El = $e; W = $w; H = $h; D = $d; Rotation = $rot }
}

# UV 空间自动换算（与 import 脚本同一套逻辑 ✓）
$mnx = 1e9; $mny = 1e9; $mxx = -1e9; $mxy = -1e9
foreach ($c in $cubes) {
    foreach ($n in $names) {
        $uv = $c.El.faces.$n.uv
        if (-not $uv) { continue }
        foreach ($v in @([double]$uv[0], [double]$uv[2])) { $mnx = [Math]::Min($mnx, $v); $mxx = [Math]::Max($mxx, $v) }
        foreach ($v in @([double]$uv[1], [double]$uv[3])) { $mny = [Math]::Min($mny, $v); $mxy = [Math]::Max($mxy, $v) }
    }
}
$cx0 = $img.Width; $cy0 = $img.Height; $cx1 = -1; $cy1 = -1
for ($y = 0; $y -lt $img.Height; $y++) {
    for ($x = 0; $x -lt $img.Width; $x++) {
        if ($img.GetPixel($x, $y).A -eq 0) { continue }
        if ($x -lt $cx0) { $cx0 = $x }; if ($x -gt $cx1) { $cx1 = $x }
        if ($y -lt $cy0) { $cy0 = $y }; if ($y -gt $cy1) { $cy1 = $y }
    }
}
$k = 1.0
if ($UvScale -gt 0) { $k = $UvScale }
elseif (($mxx - $mnx) -gt 0.01 -and ($mxy - $mny) -gt 0.01) {
    $k = [Math]::Min(($cx1 - $cx0 + 1) / ($mxx - $mnx), ($cy1 - $cy0 + 1) / ($mxy - $mny))
}
Write-Host ("UV 包围盒：{0:N2},{1:N2} ~ {2:N2},{3:N2}   贴图内容：{4},{5} ~ {6},{7}   换算 uv × {8:N4}" -f `
    $mnx, $mny, $mxx, $mxy, $cx0, $cy0, $cx1, $cy1, $k)

# ---------- 画图 ----------
$W = $img.Width * $Zoom; $H = $img.Height * $Zoom
$out = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($out)
$g.Clear([System.Drawing.Color]::FromArgb(255, 20, 20, 24))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.DrawImage($img, 0, 0, $W, $H)

# 每 1 个 UV 单位（= k 像素）一条淡网格
$penGrid = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(40, 255, 255, 255)), 1
for ($u = 0; $u -le [Math]::Ceiling($mxx); $u++) {
    $x = [int][Math]::Round($u * $k * $Zoom)
    if ($x -lt $W) { $g.DrawLine($penGrid, $x, 0, $x, $H) }
}
for ($v = 0; $v -le [Math]::Ceiling($mxy); $v++) {
    $y = [int][Math]::Round($v * $k * $Zoom)
    if ($y -lt $H) { $g.DrawLine($penGrid, 0, $y, $W, $y) }
}
$penGrid.Dispose()

$font = New-Object System.Drawing.Font 'Consolas', 12, ([System.Drawing.FontStyle]::Bold)
$white = [System.Drawing.Brushes]::White
$palette = @(
    [System.Drawing.Color]::FromArgb(230, 255, 90, 90),
    [System.Drawing.Color]::FromArgb(230, 120, 255, 120),
    [System.Drawing.Color]::FromArgb(230, 120, 170, 255),
    [System.Drawing.Color]::FromArgb(230, 255, 220, 80),
    [System.Drawing.Color]::FromArgb(230, 255, 130, 255),
    [System.Drawing.Color]::FromArgb(230, 120, 255, 255),
    [System.Drawing.Color]::FromArgb(230, 255, 170, 90)
)

$rows = @()
foreach ($c in $cubes) {
    $col = $palette[$c.Index % $palette.Count]
    $pen = New-Object System.Drawing.Pen $col, 2
    $first = $true
    foreach ($n in $names) {
        $uv = $c.El.faces.$n.uv
        if (-not $uv) { continue }
        $u1 = [double]$uv[0]; $v1 = [double]$uv[1]; $u2 = [double]$uv[2]; $v2 = [double]$uv[3]
        $x = [int][Math]::Round([Math]::Min($u1, $u2) * $k * $Zoom)
        $y = [int][Math]::Round([Math]::Min($v1, $v2) * $k * $Zoom)
        $w = [int][Math]::Max(2, [Math]::Round([Math]::Abs($u2 - $u1) * $k * $Zoom))
        $h = [int][Math]::Max(2, [Math]::Round([Math]::Abs($v2 - $v1) * $k * $Zoom))
        $g.DrawRectangle($pen, $x, $y, $w, $h)
        if ($first) {
            # 编号徽章（同编号 = 同一个方块 ✓）
            $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(230, 255, 255, 255))), $x, $y, 20, 16)
            $g.DrawString([string]$c.Index, $font, [System.Drawing.Brushes]::Black, ($x + 1), ($y - 1))
            $first = $false
        }
        $rows += ("{0,2} {1,-6} uv=({2,6:N2},{3,6:N2})-({4,6:N2},{5,6:N2})  → 贴图 px ({6,4:N1},{7,4:N1})-({8,4:N1},{9,4:N1})" -f `
            $c.Index, $n, $u1, $v1, $u2, $v2, ([Math]::Min($u1, $u2) * $k), ([Math]::Min($v1, $v2) * $k), ([Math]::Max($u1, $u2) * $k), ([Math]::Max($v1, $v2) * $k))
    }
    $pen.Dispose()
}
$g.Dispose()

# ---------- 也生成一张"按编号着色"的示意（同编号 = 同方块 ✓，看分组归属更直观） ----------
$outPath = Join-Path $buildDir 'paint-islands.png'
$out.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose(); $img.Dispose()

Write-Host ""
Write-Host ("{0,-3} {1,-22} {2}" -f '#', '方块尺寸(模型单位)', '面 → 贴图坐标')
$lastIdx = -1
foreach ($r in $rows) {
    $idx = [int]($r.Substring(0, 2).Trim())
    if ($idx -ne $lastIdx) {
        $c = $cubes | Where-Object { $_.Index -eq $idx }
        Write-Host ("{0,-3} {1,-22}" -f $idx, ("{0}x{1}x{2}" -f $c.W, $c.H, $c.D))
        $lastIdx = $idx
    }
    Write-Host ("    {0}" -f $r.Substring(3))
}
Write-Host ""
Write-Host "已输出：$outPath  （同编号 = 同一个方块 ✓）"
