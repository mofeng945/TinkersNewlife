# 封呪瓶（咒力容器）物品贴图生成脚本：画成一只"壶"
#
# 输出：textures/item/curse_bottle.png（32×32，带透明背景）
# 用法：powershell -ExecutionPolicy Bypass -File tools\gen-curse-bottle-art.ps1
# 想换配色改下面的 $C 调色板；想改造型改 Build-* 系列函数的椭圆/矩形参数。
#
# 绘制顺序：壶身（陶） → 能量窗口（紫色咒力） → 铁箍 → 壶盖与封条 → 把手 → 描边 → 高光

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$W = 32
# NOTE (pitfall): on a 32px canvas the centre is 16.0, NOT 15.5. Pixel p is centred at p+0.5,
# so left/right symmetry means left + right = 31. Using 15.5 shifts the whole sprite half a pixel
# to the left, which reads as an asymmetric pot. Mirror paired parts as 32 - c (handle 5.0 <-> 27.0).
$canvas = New-Object System.Drawing.Bitmap $W, $W
for ($y = 0; $y -lt $W; $y++) { for ($x = 0; $x -lt $W; $x++) { $canvas.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0)) } }

# ============ 调色板 ============
function C([string]$hex, [int]$a = 255) {
    $r = [Convert]::ToInt32($hex.Substring(1, 2), 16)
    $g = [Convert]::ToInt32($hex.Substring(3, 2), 16)
    $b = [Convert]::ToInt32($hex.Substring(5, 2), 16)
    return [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}
$OUT   = C '#0E0B12'   # 描边
$CLAY_D= C '#33333E'   # 陶土 暗
$CLAY_M= C '#454555'   # 陶土 中
$CLAY_L= C '#5A5A6C'   # 陶土 亮
$CLAY_H= C '#767688'   # 陶土 高光
$IRON_D= C '#4A4A56'
$IRON_M= C '#71717E'
$IRON_H= C '#A6ACBA'
$P_DEEP= C '#2E1250'
$P_MID = C '#6A28AE'
$P_LIT = C '#A868E4'
$P_HOT = C '#DCC0FF'
$SEAL_D= C '#4C121C'
$SEAL_M= C '#8A2434'
$SEAL_L= C '#B8485A'
$INK   = C '#140C1C'

# ============ 基础绘制工具（都用"已着色才覆盖"的规则，方便叠加） ============
$script:px = @{}   # key "x,y" -> Color
function Set-Px([int]$x, [int]$y, $color, [switch]$OnlyIfEmpty) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $W -or $y -ge $W) { return }
    $k = "$x,$y"
    if ($OnlyIfEmpty -and $script:px.ContainsKey($k)) { return }
    $script:px[$k] = $color
}
function Get-Px([int]$x, [int]$y) {
    $k = "$x,$y"
    if ($script:px.ContainsKey($k)) { return $script:px[$k] }
    return $null
}
# 椭圆：$mode = fill / ring（环带，$inner 为内半径比例）
function Draw-Ellipse([double]$cx, [double]$cy, [double]$rx, [double]$ry, $color, [double]$inner = -1.0, [switch]$OnlyIfEmpty) {
    for ($y = 0; $y -lt $W; $y++) {
        for ($x = 0; $x -lt $W; $x++) {
            $dx = ($x + 0.5 - $cx) / $rx
            $dy = ($y + 0.5 - $cy) / $ry
            $d = $dx * $dx + $dy * $dy
            if ($d -le 1.0 -and ($inner -lt 0 -or $d -ge $inner)) {
                Set-Px $x $y $color -OnlyIfEmpty:$OnlyIfEmpty
            }
        }
    }
}
function Draw-Rect([int]$x1, [int]$y1, [int]$x2, [int]$y2, $color, [switch]$OnlyIfEmpty) {
    for ($y = $y1; $y -le $y2; $y++) { for ($x = $x1; $x -le $x2; $x++) { Set-Px $x $y $color -OnlyIfEmpty:$OnlyIfEmpty } }
}
# 只在"已经画过东西"的地方上色（用于在壶身上面画箍、窗口、暗部）
function Draw-RectOnBody([int]$x1, [int]$y1, [int]$x2, [int]$y2, $color) {
    for ($y = $y1; $y -le $y2; $y++) {
        for ($x = $x1; $x -le $x2; $x++) {
            if (Get-Px $x $y) { Set-Px $x $y $color }
        }
    }
}

# ============ 1) 壶身 ============
# 肚子（最宽）
Draw-Ellipse 16.0 21.0 8.6 7.4 $CLAY_M
# 肩部
Draw-Ellipse 16.0 15.0 6.2 4.0 $CLAY_M
# 颈
Draw-Rect 13 10 18 16 $CLAY_M
# 足（外撇的圈足）
Draw-Ellipse 16.0 27.4 5.0 2.0 $CLAY_M
Draw-Rect 12 26 19 28 $CLAY_M
Draw-Rect 12 29 19 29 $CLAY_D

# ============ 2) 壶身明暗（左上受光） ============
for ($y = 0; $y -lt $W; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
        $c = Get-Px $x $y
        if (-not $c -or $c.ToArgb() -ne $CLAY_M.ToArgb()) { continue }
        $dx = ($x + 0.5 - 16.0) / 8.6; $dy = ($y + 0.5 - 21.0) / 7.4
        $d = [Math]::Sqrt($dx * $dx + $dy * $dy)
        $light = -$dx * 0.9 - $dy * 0.9     # 左上更亮
        if ($light -gt 0.45) { Set-Px $x $y $CLAY_H }
        elseif ($light -gt 0.12) { Set-Px $x $y $CLAY_L }
        elseif ($light -lt -0.55) { Set-Px $x $y $CLAY_D }
    }
}

# ============ 3) 能量窗口：肚子里透出的紫色咒力 ============
Draw-Ellipse 16.0 21.5 5.8 4.4 $P_DEEP -OnlyIfEmpty:$false
for ($y = 14; $y -le 28; $y++) {
    for ($x = 4; $x -le 27; $x++) {
        $dx = ($x + 0.5 - 16.0) / 5.8
        $dy = ($y + 0.5 - 21.5) / 4.4
        if ($dx * $dx + $dy * $dy -gt 1.0) { continue }
        $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
        $th = [Math]::Atan2($dy, $dx)
        $swirl = [Math]::Sin(2.0 * $th + 4.2 * $r)
        $v = (1.0 - $r) * 0.85 + 0.35 * $swirl
        if ($v -gt 0.72) { Set-Px $x $y $P_HOT }
        elseif ($v -gt 0.45) { Set-Px $x $y $P_LIT }
        elseif ($v -gt 0.18) { Set-Px $x $y $P_MID }
        else { Set-Px $x $y $P_DEEP }
    }
}
# 窗口边缘压一圈深紫，像嵌进去的玻璃口
Draw-Ellipse 16.0 21.5 5.8 4.4 $P_DEEP -inner 0.80

# ============ 4) 铁箍（跨越肚子上部）与圈足铁边 ============
Draw-RectOnBody 5 15 26 16 $IRON_M
Draw-RectOnBody 5 15 26 15 $IRON_H        # 上沿高光
Draw-RectOnBody 5 17 26 17 $IRON_D        # 下沿暗线
# 箍上铆钉
foreach ($bx in @(8, 13, 18, 23)) {
    Set-Px $bx 16 $IRON_H
    Set-Px ($bx + 1) 16 $IRON_D
}
Draw-RectOnBody 12 29 19 29 $IRON_D

# ============ 5) 壶口 + 盖子 + 封条 ============
Draw-Ellipse 16.0 10.0 5.0 1.6 $IRON_D     # 口沿
Draw-Rect 12 9 19 10 $IRON_M
Draw-Rect 12 9 19 9 $IRON_H
# 盖
Draw-Ellipse 16.0 6.4 4.6 3.2 $CLAY_L
Draw-Ellipse 16.0 5.6 3.8 2.2 $CLAY_H
Draw-Rect 14 2 17 3 $IRON_M                # 盖钮
Set-Px 14 2 $IRON_H; Set-Px 17 3 $IRON_D
# 封条（压在盖与颈上，黑红符纸）
Draw-RectOnBody 15 4 16 11 $SEAL_M
Draw-RectOnBody 15 4 15 11 $SEAL_L
Draw-RectOnBody 15 6 16 7 $SEAL_D
Draw-RectOnBody 15 9 16 10 $SEAL_D
# 符文（黑墨）
foreach ($pp in @(@(16, 5), @(15, 7), @(16, 9))) {
    Set-Px $pp[0] $pp[1] $INK
}
# 封条两端的系绳
Draw-RectOnBody 13 10 14 11 $SEAL_D
Draw-RectOnBody 17 10 18 11 $SEAL_D

# ============ 6) 两只把手（耳） ============
Draw-Ellipse 5.0 14.0 2.0 2.7 $CLAY_M -inner 0.45
Draw-Ellipse 27.0 14.0 2.0 2.7 $CLAY_M -inner 0.45
Draw-Ellipse 5.0 14.0 2.0 2.7 $CLAY_L -inner 0.68
Draw-Ellipse 27.0 14.0 2.0 2.7 $CLAY_D -inner 0.68

# ============ 7) 自动描边（透明像素挨着实体就画深色边） ============
# ⚠ 必须"先收集、后落笔"：如果边扫边写，新写进去的描边像素会让它外侧的像素也变成"挨着实体"，
#    于是一圈变一片、一路扩散到画布边缘（第一次就是这么把背景涂黑的）。
$edgePixels = New-Object System.Collections.Generic.List[string]
for ($y = 0; $y -lt $W; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
        if (Get-Px $x $y) { continue }
        $near = $false
        foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
            if (Get-Px ($x + $d[0]) ($y + $d[1])) { $near = $true; break }
        }
        if ($near) { $edgePixels.Add("$x,$y") }
    }
}
foreach ($k in $edgePixels) {
    $p2 = $k -split ','
    Set-Px ([int]$p2[0]) ([int]$p2[1]) $OUT
}

# ============ 8) 落笔 ============
for ($y = 0; $y -lt $W; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
        $c = Get-Px $x $y
        if ($c) { $canvas.SetPixel($x, $y, $c) }
    }
}
$out = Join-Path $outDir 'curse_bottle.png'
$canvas.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
Write-Host ("已生成 " + $out + "（32×32）")
