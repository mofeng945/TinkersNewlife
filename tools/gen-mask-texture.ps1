# 生成 16×16「双向认知阻碍面具」贴图：**一张漆黑的假面，只有两条苍白眼缝**。
#
# 沿用同心戒那套程序化画法（同一批坑已经踩过，见 tools\gen-twin-ring-texture.ps1 的注释）：
#   ① 描边画在**形状外面**（否则 2px 宽的带子会被描边吃光）；
#   ② 渐变必须**平滑可导**（这里用"上亮下暗 + 左亮右暗"的线性混合，
#      比按角度着色更适合一张脸 —— 但同样不能用"离边缘的距离"这种逐像素跳变的东西）；
#   ③ 超采样 16× 再二值化，边缘是算出来的。
#
# 面具形状：上半是椭圆，下半向"下巴"收窄（rx 随深度衰减）——
# 直接画整椭圆会像个鸡蛋 ✗。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen-mask-texture.ps1
param(
    [string]$Name = 'cognitive_mask'
)

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$N = 16
$SS = 16

# 面具：椭圆中心/半径；下半按 depth 收窄成下巴
$CX = 8.0; $CY = 8.4
$RX = 5.1; $RY = 6.0
$CHIN = 0.55        # 下巴收窄比例（小了就成鸡蛋 ✗）

# 眼缝（两条横椭圆）
$EYE_Y = 7.5
$EYE_RX = 1.75; $EYE_RY = 0.95
$EYE_DX = 2.35

# 配色：漆黑本体 + 苍白眼缝
$C_OUT = @(18, 14, 20)
$C_DARK = @(42, 36, 48)
$C_MID = @(65, 58, 76)
$C_LIGHT = @(112, 102, 134)
$C_EYE = @(232, 228, 240)
$C_EYE_DIM = @(150, 146, 170)

function In-Mask([double]$x, [double]$y) {
    $dy = ($y - $CY) / $RY
    if ([Math]::Abs($dy) -gt 1.0) { return $false }
    $rx = $RX
    if ($dy -gt 0) {
        # 下半：向里收（^1.4 让收窄集中在下巴附近）
        $rx = $RX * (1.0 - $CHIN * [Math]::Pow($dy, 1.4))
    }
    $dx = ($x - $CX) / $rx
    return (($dx * $dx + $dy * $dy) -le 1.0)
}

function In-Eye([double]$x, [double]$y) {
    foreach ($ex in @(($CX - $EYE_DX), ($CX + $EYE_DX))) {
        $dx = ($x - $ex) / $EYE_RX
        $dy = ($y - $EYE_Y) / $EYE_RY
        if (($dx * $dx + $dy * $dy) -le 1.0) { return $true }
    }
    return $false
}

# ---------- 1) 超采样：本体覆盖率 + 眼缝覆盖率 ----------
$body = New-Object 'double[,]' $N, $N
$eye = New-Object 'double[,]' $N, $N
$sub = 1.0 / $SS
for ($py = 0; $py -lt $N; $py++) {
    for ($px = 0; $px -lt $N; $px++) {
        $cb = 0; $ce = 0
        for ($sy = 0; $sy -lt $SS; $sy++) {
            $y = $py + ($sy + 0.5) * $sub
            for ($sx = 0; $sx -lt $SS; $sx++) {
                $x = $px + ($sx + 0.5) * $sub
                if (In-Mask $x $y) { $cb++ }
                if (In-Eye $x $y) { $ce++ }
            }
        }
        $tot = $SS * $SS
        $body[$px, $py] = $cb / $tot
        $eye[$px, $py] = $ce / $tot
    }
}

# ---------- 2) 掩码（眼缝只在本体上生效）----------
$owner = New-Object 'int[,]' $N, $N   # 0=空 1=本体 2=眼缝
for ($py = 0; $py -lt $N; $py++) {
    for ($px = 0; $px -lt $N; $px++) {
        if ($body[$px, $py] -lt 0.4) { continue }
        $owner[$px, $py] = if ($eye[$px, $py] -ge 0.5) { 2 } else { 1 }
    }
}

# ---------- 3) 上色 ----------
$bmp = [System.Drawing.Bitmap]::new($N, $N)
for ($py = 0; $py -lt $N; $py++) {
    for ($px = 0; $px -lt $N; $px++) {
        $ow = $owner[$px, $py]
        if ($ow -eq 0) {
            # 描边画在形状外面（不占本体 ✓）
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
        if ($ow -eq 2) {
            # 眼缝：中间亮、上下略暗（一条细缝的体积感）
            $dy = [Math]::Abs($py + 0.5 - $EYE_Y) / $EYE_RY
            $c = if ($dy -lt 0.55) { $C_EYE } else { $C_EYE_DIM }
            $bmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb(255, $c[0], $c[1], $c[2]))
            continue
        }
        # 本体：左上打光的线性混合（平滑，不会出现相邻像素跳档的噪点）
        $vx = 1.0 - ($px + 0.5) / $N          # 左亮
        $vy = 1.0 - ($py + 0.5) / $N          # 上亮
        $v = 0.62 * $vy + 0.38 * $vx
        if ($v -lt 0.34) { $c = $C_DARK }
        elseif ($v -lt 0.52) { $c = $C_MID }
        else { $c = $C_LIGHT }
        $bmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb(255, $c[0], $c[1], $c[2]))
    }
}

$dst = Join-Path $outDir "$Name.png"
$bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)

# 8× 预览（只放 build/，方便肉眼验收）
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
Write-Host "  生成 $Name.png（16x16 面具；预览 build\preview_$Name.png）"
