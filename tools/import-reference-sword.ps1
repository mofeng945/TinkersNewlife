# 参考图 -> 1:1 复刻成物品贴图
#
# 用户给一张"想要的剑"的截图（通常是把某个低分辨率贴图最近邻放大后的图），本脚本把它
# **按原始像素网格取样还原**，写进 textures/item/durandal_sword.png。
#
# 原理：最近邻放大后，每个贴图像素 = 一个 k×k 色块。沿剑身扫若干行做"游程长度"统计即可估出 k，
#       然后取每个色块的**中心像素**（不是平均！）—— 这对最近邻放大是**精确还原**。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools\import-reference-sword.ps1 -In tools\art-src\ref_sword.png [-Out 64]
#
# 性能：只做 O(S^2) 次取样 + 一次 O(W) 游程扫描；不要用"全图逐像素比较"那种写法（PowerShell 里要几分钟）。

param(
    [Parameter(Mandatory = $true)][string]$In,
    [int]$Out = 0,
    [switch]$KeepBackground,
    [int]$Tolerance = 26
)

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$srcPath = $In
if (-not [System.IO.Path]::IsPathRooted($srcPath)) { $srcPath = Join-Path $root $In }
if (-not (Test-Path $srcPath)) { throw ("reference image not found: " + $srcPath) }
$outPath = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item\durandal_sword.png'

$pf = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
$src = New-Object System.Drawing.Bitmap $srcPath
$W = $src.Width; $H = $src.Height
$d = $src.LockBits((New-Object System.Drawing.Rectangle 0, 0, $W, $H), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, $pf)
$SS = $d.Stride
$SB = [byte[]]::new($SS * $H)
[System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $SB, 0, $SB.Length)
$src.UnlockBits($d)
Write-Host ("reference: " + $W + "x" + $H)

# ---- 1) 检测块宽 k：统计"水平颜色跳变"的 x 位置，正确块宽下跳变都落在 k 的整数倍上 ----
# （不能用"游程长度众数"：剑身大片纯黑会让游程跨越多个块，众数会被背景/纯色区带偏）
if ($Out -le 0) {   # 给了 -Out 就跳过网格检测（检测依赖"无滤镜放大"，遇到带滤镜的截图会不准且慢）
$trans = New-Object System.Collections.Generic.List[int]
for ($y = 0; $y -lt $H; $y += 2) {
    $row = $y * $SS
    $prev = -1
    for ($x = 0; $x -lt $W; $x++) {
        $i = $row + $x * 4
        $cur = $SB[$i] -bor ($SB[$i + 1] -shl 8) -bor ($SB[$i + 2] -shl 16)
        if ($prev -ge 0 -and $cur -ne $prev) { $trans.Add($x) }
        $prev = $cur
    }
}
Write-Host ("horizontal transitions: " + $trans.Count)
if ($trans.Count -lt 20) { throw 'not enough transitions to detect a grid' }
$probe = New-Object System.Collections.Generic.List[int]
for ($i = 0; $i -lt $trans.Count; $i += 3) { $probe.Add($trans[$i]) }

$scores = @{}
$bestS = 0; $bestScore = [double]::MaxValue
foreach ($S in 8..256) {
    $k = $W / [double]$S
    $mis = 0
    foreach ($x in $probe) {
        $r = $x % $k
        if ($r -gt ($k / 2.0)) { $r = $k - $r }
        if ($r -gt ($k * 0.30)) { $mis++ }
    }
    $score = $mis / [double]$probe.Count
    $scores[$S] = $score
    if ($score -lt $bestScore) { $bestScore = $score; $bestS = $S }
}
# 倍数会"混叠"出同样好的分数，取其中**最小**的（即真实分辨率）
$finalS = $bestS
foreach ($S in ($scores.Keys | Sort-Object)) {
    if ($scores[$S] -le ($bestScore + 0.02)) { $finalS = $S; break }
}
Write-Host ("grid fit: best S=" + $bestS + " (mismatch " + [Math]::Round($bestScore * 100, 2) + "%)  ->  chosen S=" + $finalS)
$S = $finalS
    $S = [int]$Out
    Write-Host ("grid detection skipped, using -Out = " + $S)
}
if ($Out -gt 0) { $S = $Out }
$k = $W / [double]$S
Write-Host ("using sprite " + $S + "x" + $S + " (block width " + [Math]::Round($k, 3) + " px)")
# ---- 2) 取每个色块中心像素 ----
$outB = [byte[]]::new($S * $S * 4)
for ($sy = 0; $sy -lt $S; $sy++) {
    $my = [int][Math]::Min($H - 1, [Math]::Floor(($sy + 0.5) * $H / [double]$S))
    for ($sx = 0; $sx -lt $S; $sx++) {
        $mx = [int][Math]::Min($W - 1, [Math]::Floor(($sx + 0.5) * $W / [double]$S))
        $i = $my * $SS + $mx * 4
        $o = ($sy * $S + $sx) * 4
        $outB[$o] = $SB[$i]; $outB[$o + 1] = $SB[$i + 1]; $outB[$o + 2] = $SB[$i + 2]; $outB[$o + 3] = 255
    }
}

# ---- 3) 背景透明化（四角众数为背景色）----
if (-not $KeepBackground) {
    $keys = @()
    foreach ($p in @(@(0, 0), @(($S - 1), 0), @(0, ($S - 1)), @(($S - 1), ($S - 1)))) {
        $o = ($p[1] * $S + $p[0]) * 4
        $keys += ($outB[$o].ToString() + ',' + $outB[$o + 1].ToString() + ',' + $outB[$o + 2].ToString())
    }
    $bg = ($keys | Group-Object | Sort-Object Count -Descending | Select-Object -First 1).Name
    $bp = $bg -split ','
    $br = [int]$bp[0]; $bgc = [int]$bp[1]; $bb = [int]$bp[2]
    $cleared = 0
    for ($i = 0; $i -lt ($S * $S); $i++) {
        $o = $i * 4
        if ([Math]::Abs($outB[$o] - $br) -le $Tolerance -and [Math]::Abs($outB[$o + 1] - $bgc) -le $Tolerance -and [Math]::Abs($outB[$o + 2] - $bb) -le $Tolerance) {
            $outB[$o + 3] = 0; $cleared++
        }
    }
    Write-Host ("background cleared: " + $cleared + " px (bg rgb " + $bg + ", tol " + $Tolerance + ")")
}

# ---- 4) 写图 ----
$canvas = [System.Drawing.Bitmap]::new($S, $S, $pf)
$dd = $canvas.LockBits((New-Object System.Drawing.Rectangle 0, 0, $S, $S), [System.Drawing.Imaging.ImageLockMode]::WriteOnly, $pf)
[System.Runtime.InteropServices.Marshal]::Copy($outB, 0, $dd.Scan0, $outB.Length)
$canvas.UnlockBits($dd)
$canvas.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
Write-Host ("wrote " + $outPath + " (" + $S + "x" + $S + ")")

# ---- 5) 自检 ----
$total = 0.0; $cnt = 0; $bad = 0; $transparent = 0
for ($sy = 0; $sy -lt $S; $sy++) {
    $my = [int][Math]::Min($H - 1, [Math]::Floor(($sy + 0.5) * $H / [double]$S))
    for ($sx = 0; $sx -lt $S; $sx++) {
        $o = ($sy * $S + $sx) * 4
        if ($outB[$o + 3] -eq 0) { $transparent++; continue }
        $mx = [int][Math]::Min($W - 1, [Math]::Floor(($sx + 0.5) * $W / [double]$S))
        $i = $my * $SS + $mx * 4
        $v = [Math]::Abs($SB[$i] - $outB[$o]) + [Math]::Abs($SB[$i + 1] - $outB[$o + 1]) + [Math]::Abs($SB[$i + 2] - $outB[$o + 2])
        $total += $v; $cnt++
        if ($v -gt 3) { $bad++ }
    }
}
Write-Host ("verify: sampled " + $cnt + " px, avg diff = " + [Math]::Round($total / [Math]::Max(1, $cnt), 3) + ", not-identical = " + $bad + ", transparent = " + $transparent)
$src.Dispose()
# 实测经验（2026-09 用户给的那张参考图）：
#   · 该图是把贴图**带滤镜**放大到 940x945 的（相邻色块之间有 1px 过渡带），
#     这种输入下"跳变落在整数倍"的自动识别会失准 -> 直接用 -Out 64 指定尺寸最稳。
#   · 背景是**双色棋盘格**（看图软件表示透明区），且与剑身暗部亮度很接近；
#     容差默认 12：26 会把剑身暗灰一起扣掉（实测剑身上半截被啃出缺口）。