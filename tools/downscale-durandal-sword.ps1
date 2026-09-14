# 杜兰达尔之剑：把原 256×256 贴图缩绘成像素风（保留原本造型）
#
# 输入：tools/art-src/durandal_sword_256.png（用户原图）
# 输出：textures/item/durandal_sword.png（默认 64×64；$SIZE 改 32 出 32×32）
#
# 直接"方块平均 + 阈值"会让细处（尖刺、剑尖）被透明像素稀释，断成虚线，像一团残缺的雾。所以：
#   1) 轮廓用「区块内 alpha 最大值」判定 —— 细刺只要碰到就保留，不会断；
#   2) 颜色只用「区块内 alpha>=0.6 的实心像素」求平均 —— 不被边缘半透明拉灰；
#   3) 形态学三步：去孤立点 → 填 1px 孔洞 → 膨胀 Npx（加粗，MC 风格更硬朗）；
#   4) 色调量化到有限档位 —— 避免渐变糊成一团。
#
# 性能：LockBits + byte[]（GetPixel/SetPixel 6 万次托管调用要几十秒）。
# 注意 1：数组一律用 [double[]]::new($n) —— New-Object double[] $n 在 PowerShell 里会得到 null。
# 注意 2：PowerShell 变量名不区分大小写！颜色数组叫 $pR/$pG/$pB，累加变量叫 $r/$g/$b，
#         否则 `$r = 0.0` 会把数组 $R 覆盖成 double（"Unable to index into an object of type System.Double"）。

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$srcPath = Join-Path $root 'tools\art-src\durandal_sword_256.png'
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'

$SIZE = 64
$MASK_THRESHOLD = 0.50   # 0.5 = 区块半数以上被覆盖才算实体（贴合原图粗细；越小越粗）
$CORE_ALPHA = 0.60       # 求颜色时只统计 alpha >= 它的实心像素
$DILATE = 0              # 0 = 不加粗（加粗会明显变胖，原图是细剑）
$L_IN = 0.0              # 亮度映射区间：0~255 + 输出 0~255 = 恒等
$H_IN = 255.0
$LO_OUT = 0.0            # 输出下限 0（原图本身就是黑剑，不额外提亮）
$HI_OUT = 255.0
$BEVEL = 0.0             # 0 = 不加人为斜面光（要立体感可 0.2~0.35）
$GAMMA_OUT = 1.00
$POSTERIZE = 6           # 色调量化档数（0/1 = 不量化）
$SMOOTH = 1              # 上色前 3x3 平均（去麻点）
$HILIGHT = 0.0           # 0 = 纯平均（保持原图那种含蓄的亮线；调高会让亮带变宽变亮）

if (-not (Test-Path $srcPath)) { throw ("missing source art: " + $srcPath) }
$src = New-Object System.Drawing.Bitmap $srcPath
$SW = $src.Width; $SH = $src.Height
$pf = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
$srcData = $src.LockBits((New-Object System.Drawing.Rectangle 0, 0, $SW, $SH), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, $pf)
$stride = $srcData.Stride
$sb = [byte[]]::new($stride * $SH)
[System.Runtime.InteropServices.Marshal]::Copy($srcData.Scan0, $sb, 0, $sb.Length)
$src.UnlockBits($srcData)
$src.Dispose()

$block = $SW / [double]$SIZE
$N = $SIZE * $SIZE
$mask = [byte[]]::new($N)
$pR = [double[]]::new($N)
$pG = [double[]]::new($N)
$pB = [double[]]::new($N)

# ---- 1) 逐区块统计 ----
for ($oy = 0; $oy -lt $SIZE; $oy++) {
    for ($ox = 0; $ox -lt $SIZE; $ox++) {
        $maxA = 0.0; $cSum = 0.0; $rSum = 0.0; $gSum = 0.0; $bSum = 0.0
        $maxLum = -1.0; $maxR = 0.0; $maxG = 0.0; $maxB = 0.0
        $y0 = [int][Math]::Floor($oy * $block); $y1 = [int][Math]::Ceiling(($oy + 1) * $block)
        $x0 = [int][Math]::Floor($ox * $block); $x1 = [int][Math]::Ceiling(($ox + 1) * $block)
        for ($sy = $y0; $sy -lt $y1; $sy++) {
            if ($sy -lt 0 -or $sy -ge $SH) { continue }
            $base = $sy * $stride
            for ($sx = $x0; $sx -lt $x1; $sx++) {
                if ($sx -lt 0 -or $sx -ge $SW) { continue }
                $i = $base + $sx * 4
                $a = $sb[$i + 3] / 255.0
                if ($a -gt $maxA) { $maxA = $a }
                if ($a -ge $CORE_ALPHA) {
                    $cSum += 1.0
                    $pl = 0.299 * $sb[$i + 2] + 0.587 * $sb[$i + 1] + 0.114 * $sb[$i]
                    if ($pl -gt $maxLum) { $maxLum = $pl; $maxR = $sb[$i + 2]; $maxG = $sb[$i + 1]; $maxB = $sb[$i] }
                    $bSum += $sb[$i]; $gSum += $sb[$i + 1]; $rSum += $sb[$i + 2]
                }
            }
        }
        $idx = $oy * $SIZE + $ox
        if ($maxA -ge $MASK_THRESHOLD) {
            $mask[$idx] = 1
            if ($cSum -gt 0) {
                $mr = $rSum / $cSum; $mg = $gSum / $cSum; $mb = $bSum / $cSum
                $meanLum = 0.299 * $mr + 0.587 * $mg + 0.114 * $mb
                $mixLum = $meanLum
                if ($maxLum -gt $meanLum) { $mixLum = $meanLum + ($maxLum - $meanLum) * $HILIGHT }
                $k2 = 1.0
                if ($meanLum -gt 0.0001) { $k2 = $mixLum / $meanLum }
                $pR[$idx] = [Math]::Min(255.0, $mr * $k2)
                $pG[$idx] = [Math]::Min(255.0, $mg * $k2)
                $pB[$idx] = [Math]::Min(255.0, $mb * $k2)
            }
        }
    }
}
$afterMask = 0; for ($k = 0; $k -lt $N; $k++) { if ($mask[$k] -eq 1) { $afterMask++ } }

# ---- 2) 去孤立点 ----
$kill = New-Object System.Collections.Generic.List[int]
for ($y = 0; $y -lt $SIZE; $y++) {
    for ($x = 0; $x -lt $SIZE; $x++) {
        $idx = $y * $SIZE + $x
        if ($mask[$idx] -ne 1) { continue }
        $deg = 0
        if ($x -gt 0 -and $mask[$idx - 1] -eq 1) { $deg++ }
        if ($x -lt $SIZE - 1 -and $mask[$idx + 1] -eq 1) { $deg++ }
        if ($y -gt 0 -and $mask[$idx - $SIZE] -eq 1) { $deg++ }
        if ($y -lt $SIZE - 1 -and $mask[$idx + $SIZE] -eq 1) { $deg++ }
        if ($deg -eq 0) { $kill.Add($idx) }
    }
}
foreach ($k in $kill) { $mask[$k] = 0 }

# ---- 3) 填 1px 孔洞（3 个以上邻居是实体就填实；颜色取邻居平均）----
$fill = New-Object System.Collections.Generic.List[int]
for ($y = 1; $y -lt $SIZE - 1; $y++) {
    for ($x = 1; $x -lt $SIZE - 1; $x++) {
        $idx = $y * $SIZE + $x
        if ($mask[$idx] -eq 1) { continue }
        $deg = 0
        if ($mask[$idx - 1] -eq 1) { $deg++ }
        if ($mask[$idx + 1] -eq 1) { $deg++ }
        if ($mask[$idx - $SIZE] -eq 1) { $deg++ }
        if ($mask[$idx + $SIZE] -eq 1) { $deg++ }
        if ($deg -ge 3) { $fill.Add($idx) }
    }
}
foreach ($k in $fill) {
    $mask[$k] = 1
    $r = 0.0; $g = 0.0; $b = 0.0; $c = 0.0
    foreach ($d in @(-1, 1, -$SIZE, $SIZE)) {
        $nidx = $k + $d
        if ($nidx -lt 0 -or $nidx -ge $N -or $nidx -eq $k) { continue }
        if ($mask[$nidx] -eq 1) { $r += $pR[$nidx]; $g += $pG[$nidx]; $b += $pB[$nidx]; $c += 1.0 }
    }
    if ($c -gt 0) { $pR[$k] = $r / $c; $pG[$k] = $g / $c; $pB[$k] = $b / $c }
}

# ---- 4) 膨胀 N 次 ----
for ($step = 0; $step -lt $DILATE; $step++) {
    $add = New-Object System.Collections.Generic.List[int]
    for ($y = 0; $y -lt $SIZE; $y++) {
        for ($x = 0; $x -lt $SIZE; $x++) {
            $idx = $y * $SIZE + $x
            if ($mask[$idx] -eq 1) { continue }
            $hit = $false
            if ($x -gt 0 -and $mask[$idx - 1] -eq 1) { $hit = $true }
            elseif ($x -lt $SIZE - 1 -and $mask[$idx + 1] -eq 1) { $hit = $true }
            elseif ($y -gt 0 -and $mask[$idx - $SIZE] -eq 1) { $hit = $true }
            elseif ($y -lt $SIZE - 1 -and $mask[$idx + $SIZE] -eq 1) { $hit = $true }
            if ($hit) { $add.Add($idx) }
        }
    }
    foreach ($k in $add) {
        $mask[$k] = 1
        $r = 0.0; $g = 0.0; $b = 0.0; $c = 0.0
        foreach ($d in @(-1, 1, -$SIZE, $SIZE)) {
            $nidx = $k + $d
            if ($nidx -lt 0 -or $nidx -ge $N -or $nidx -eq $k) { continue }
            if ($mask[$nidx] -eq 1) { $r += $pR[$nidx]; $g += $pG[$nidx]; $b += $pB[$nidx]; $c += 1.0 }
        }
        if ($c -gt 0) { $pR[$k] = $r / $c * 0.88; $pG[$k] = $g / $c * 0.88; $pB[$k] = $b / $c * 0.88 }
    }
}

# ---- 4.5) 平滑：3x3 平均（只统计实体像素），去掉缩绘产生的麻点 ----
for ($pass = 0; $pass -lt $SMOOTH; $pass++) {
    $tR = [double[]]::new($N); $tG = [double[]]::new($N); $tB = [double[]]::new($N)
    for ($y = 0; $y -lt $SIZE; $y++) {
        for ($x = 0; $x -lt $SIZE; $x++) {
            $idx = $y * $SIZE + $x
            if ($mask[$idx] -ne 1) { continue }
            $sr = 0.0; $sg = 0.0; $sbl = 0.0; $sc = 0.0
            for ($dy = -1; $dy -le 1; $dy++) {
                $ny = $y + $dy
                if ($ny -lt 0 -or $ny -ge $SIZE) { continue }
                for ($dx = -1; $dx -le 1; $dx++) {
                    $nx = $x + $dx
                    if ($nx -lt 0 -or $nx -ge $SIZE) { continue }
                    $nidx = $ny * $SIZE + $nx
                    if ($mask[$nidx] -ne 1) { continue }
                    $sr += $pR[$nidx]; $sg += $pG[$nidx]; $sbl += $pB[$nidx]; $sc += 1.0
                }
            }
            if ($sc -gt 0) { $tR[$idx] = $sr / $sc; $tG[$idx] = $sg / $sc; $tB[$idx] = $sbl / $sc }
        }
    }
    for ($idx = 0; $idx -lt $N; $idx++) { if ($mask[$idx] -eq 1) { $pR[$idx] = $tR[$idx]; $pG[$idx] = $tG[$idx]; $pB[$idx] = $tB[$idx] } }
}
# ---- 5) 亮度拉伸 + 量化 + 写图 ----
$outB = [byte[]]::new($N * 4)
for ($idx = 0; $idx -lt $N; $idx++) {
    if ($mask[$idx] -ne 1) { continue }
    $r = $pR[$idx]; $g = $pG[$idx]; $b = $pB[$idx]
    $lum = 0.299 * $r + 0.587 * $g + 0.114 * $b
    # 黑白场映射：<=黑场 → 0（纯黑），>=白场 → 255（纯白），中间按 gamma 过渡
    $tt = ($lum - $L_IN) / ($H_IN - $L_IN)
    if ($tt -lt 0) { $tt = 0.0 } elseif ($tt -gt 1) { $tt = 1.0 }
    $tt = $tt * $tt * (3.0 - 2.0 * $tt)   # smoothstep S-curve: darks stay dark, band pops
    $xpx = ($idx % $SIZE) + 0.5
    $ypx = [Math]::Floor($idx / $SIZE) + 0.5
    $ss = ($xpx - 6.0 * $SIZE / 256.0) * 0.6967 + ($ypx - 245.0 * $SIZE / 256.0) * 0.7172
    $bev = 1.0 + $BEVEL * [Math]::Max(-1.0, [Math]::Min(1.0, -$ss / 4.0))
    $target = ($LO_OUT + ($HI_OUT - $LO_OUT) * $tt) * $bev
    if ($target -gt 255) { $target = 255 } elseif ($target -lt 0) { $target = 0 }
    $scale = 0.0
    if ($lum -gt 0.0001) { $scale = $target / $lum }
    $r2 = $r * $scale; $g2 = $g * $scale; $b2 = $b * $scale
    if ($r2 -lt 0) { $r2 = 0 } elseif ($r2 -gt 255) { $r2 = 255 }
    if ($g2 -lt 0) { $g2 = 0 } elseif ($g2 -gt 255) { $g2 = 255 }
    if ($b2 -lt 0) { $b2 = 0 } elseif ($b2 -gt 255) { $b2 = 255 }
    if ($POSTERIZE -gt 1) {
        $stepv = 255.0 / ($POSTERIZE - 1)
        $r2 = [Math]::Round($r2 / $stepv) * $stepv
        $g2 = [Math]::Round($g2 / $stepv) * $stepv
        $b2 = [Math]::Round($b2 / $stepv) * $stepv
    }
    $o = $idx * 4
    $outB[$o] = [byte][Math]::Round($b2)
    $outB[$o + 1] = [byte][Math]::Round($g2)
    $outB[$o + 2] = [byte][Math]::Round($r2)
    $outB[$o + 3] = 255
}

$canvas = [System.Drawing.Bitmap]::new($SIZE, $SIZE, $pf)
$dst = $canvas.LockBits((New-Object System.Drawing.Rectangle 0, 0, $SIZE, $SIZE), [System.Drawing.Imaging.ImageLockMode]::WriteOnly, $pf)
[System.Runtime.InteropServices.Marshal]::Copy($outB, 0, $dst.Scan0, $outB.Length)
$canvas.UnlockBits($dst)
$out = Join-Path $outDir 'durandal_sword.png'
$canvas.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()

$final = 0; for ($k = 0; $k -lt $N; $k++) { if ($mask[$k] -eq 1) { $final++ } }
Write-Host ("generated " + $out + " (" + $SIZE + "px) mask " + $afterMask + " -> kill " + $kill.Count + " -> fill " + $fill.Count + " -> final " + $final)
