# 生成 16×16「同心戒」贴图：**两枚相扣的戒指**。
#
# 为什么不用现成贴图改色：命灯指轮那张是"灯+火焰"的轮廓，跟"戒指"没关系，
# 照它改色出来的还是灯 ✗。同心戒的关键视觉就是**两枚扣在一起**，所以这里用
# 程序化几何画（超采样 16× 再二值化成像素，边缘是算出来的、不是手描的）✓。
#
# 迭代过的三个坑（都写在代码注释里）：
#   1. 描边画在本体像素上 → 2px 宽的环带被描边吃光，整枚戒指变黑 ✗
#      正解：描边画在**形状外面**那一圈空像素 ✓；
#   2. 着色掺了"离环带中心的距离" → 相邻像素在色档间反复横跳，像随机噪点 ✗
#      正解：只用**绕环一周的角度**着色，得到平滑的光照渐变 ✓；
#   3. 圆心距太小（5.9，而两外半径和 8.4）→ 两枚糊成一团 ✗
#      正解：圆心距拉到 ~6.7，只让环带在两侧各交叉一次 = 真正的"相扣" ✓。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen-twin-ring-texture.ps1
param(
    [string]$Name = 'ring_of_one_mind'
)

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$N = 16          # 画布
$SS = 16         # 超采样倍率
$ROUT = 4.2      # 圆环外半径
$RIN = 1.9       # 圆环内半径（外-内 = 环带宽度 ≈ 2.3px，够放下描边之外的本体）
# 两枚圆心：圆心距 = √(5.4² + 4.0²) ≈ 6.7，两外半径和 = 8.4 →
# 相交但不过度重叠，环带只在两侧各交叉一次 = 相扣 ✓
$AX = 5.2; $AY = 6.0
$BX = 10.6; $BY = 10.0

# 四档色 + 描边（玫瑰金：比命灯指轮的黄/红一眼能分开）
$C_OUT = @(58, 26, 48)
$C_DARK = @(140, 60, 92)
$C_MID = @(198, 96, 128)
$C_LIGHT = @(240, 152, 178)
$C_HI = @(255, 220, 232)

function In-Annulus([double]$x, [double]$y, [double]$cx, [double]$cy) {
    $dx = $x - $cx; $dy = $y - $cy
    $d = [Math]::Sqrt($dx * $dx + $dy * $dy)
    return ($d -ge $RIN -and $d -le $ROUT)
}

# ---------- 1) 超采样求覆盖率 + 相扣归属 ----------
$inA = New-Object 'double[,]' $N, $N
$inB = New-Object 'double[,]' $N, $N
$sgnSum = New-Object 'int[,]' $N, $N
$sub = 1.0 / $SS
for ($py = 0; $py -lt $N; $py++) {
    for ($px = 0; $px -lt $N; $px++) {
        $ca = 0; $cb = 0; $sgn = 0
        for ($sy = 0; $sy -lt $SS; $sy++) {
            $y = $py + ($sy + 0.5) * $sub
            for ($sx = 0; $sx -lt $SS; $sx++) {
                $x = $px + ($sx + 0.5) * $sub
                $a = In-Annulus $x $y $AX $AY
                $b = In-Annulus $x $y $BX $BY
                if ($a) { $ca++ }
                if ($b) { $cb++ }
                if ($a -and $b) {
                    # 叉积 (B-A)×(P-A)：两圆交点分居圆心连线两侧 →
                    # 一侧 B 在上、另一侧 A 在上 = 一处 B 压 A、另一处 A 压 B ✓
                    $cross = ($BX - $AX) * ($y - $AY) - ($BY - $AY) * ($x - $AX)
                    if ($cross -gt 0) { $sgn++ } else { $sgn-- }
                }
            }
        }
        $tot = $SS * $SS
        $inA[$px, $py] = $ca / $tot
        $inB[$px, $py] = $cb / $tot
        $sgnSum[$px, $py] = $sgn
    }
}

# ---------- 2) 掩码 ----------
# owner: 0=空 1=A 2=B（叠合处 = "谁在上"的那一枚）
$owner = New-Object 'int[,]' $N, $N
for ($py = 0; $py -lt $N; $py++) {
    for ($px = 0; $px -lt $N; $px++) {
        $a = $inA[$px, $py] -ge 0.4
        $b = $inB[$px, $py] -ge 0.4
        if (-not $a -and -not $b) { $owner[$px, $py] = 0; continue }
        if ($a -and $b) {
            $owner[$px, $py] = if ($sgnSum[$px, $py] -gt 0) { 2 } else { 1 }
        } elseif ($a) { $owner[$px, $py] = 1 } else { $owner[$px, $py] = 2 }
    }
}

# ---------- 3) 上色 ----------
$bmp = [System.Drawing.Bitmap]::new($N, $N)
for ($py = 0; $py -lt $N; $py++) {
    for ($px = 0; $px -lt $N; $px++) {
        $ow = $owner[$px, $py]
        if ($ow -eq 0) {
            # 空像素：四邻里有本体 → 描边（描边在形状**外面**，不占本体 ✓）
            $edge = $false
            foreach ($d in @(@(-1, 0), @(1, 0), @(0, -1), @(0, 1))) {
                $nx = $px + $d[0]; $ny = $py + $d[1]
                if ($nx -lt 0 -or $ny -lt 0 -or $nx -ge $N -or $ny -ge $N) { continue }
                if ($owner[$nx, $ny] -ne 0) { $edge = $true }
            }
            if ($edge) {
                $bmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb(255, $C_OUT[0], $C_OUT[1], $C_OUT[2]))
            } else {
                $bmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            }
            continue
        }

        # 相扣接缝：只在本体 B 贴着本体 A 的那一侧压一道深色（1px）→
        # 交叉处看得见"谁压着谁"，又不会像两侧都压那样把 2px 环带吃光 ✓
        if ($ow -eq 2) {
            $seam = $false
            foreach ($d in @(@(-1, 0), @(1, 0), @(0, -1), @(0, 1))) {
                $nx = $px + $d[0]; $ny = $py + $d[1]
                if ($nx -lt 0 -or $ny -lt 0 -or $nx -ge $N -or $ny -ge $N) { continue }
                if ($owner[$nx, $ny] -eq 1) { $seam = $true }
            }
            if ($seam) {
                # 接缝用"最暗的**本体色**"而不是描边色：交叉处看起来是"被压住"，而不是"断开一个洞" ✓
                $bmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb(255, $C_DARK[0], $C_DARK[1], $C_DARK[2]))
                continue
            }
        }

        # 只用**角度**着色（左上打光）：沿环一周平滑渐变 = 金属环的正常光感 ✓
        $cx = if ($ow -eq 1) { $AX } else { $BX }
        $cy = if ($ow -eq 1) { $AY } else { $BY }
        $vx = $px + 0.5 - $cx; $vy = $py + 0.5 - $cy
        $d = [Math]::Sqrt($vx * $vx + $vy * $vy)
        if ($d -lt 0.001) { $d = 0.001 }
        $light = (-($vx / $d) - ($vy / $d)) / [Math]::Sqrt(2)
        $v = ($light + 1) / 2

        if ($v -lt 0.30) { $c = $C_DARK }
        elseif ($v -lt 0.52) { $c = $C_MID }
        elseif ($v -lt 0.70) { $c = $C_LIGHT }
        else { $c = $C_HI }
        $bmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb(255, $c[0], $c[1], $c[2]))
    }
}

$dst = Join-Path $outDir "$Name.png"
$bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)

# 顺手出一张 8× 预览（不写进仓库资源，只放 build/ 方便肉眼验收）
$big = [System.Drawing.Bitmap]::new($N * 8, $N * 8)
$g = [System.Drawing.Graphics]::FromImage($big)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.DrawImage($bmp, 0, 0, $N * 8, $N * 8)
$g.Dispose()
$previewDir = Join-Path $root 'build'
if (-not (Test-Path $previewDir)) { New-Item -ItemType Directory -Path $previewDir -Force | Out-Null }
$big.Save((Join-Path $previewDir "preview_$Name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$big.Dispose()
$bmp.Dispose()
Write-Host "  生成 $Name.png（16x16 两枚相扣；预览 build\preview_$Name.png）"
