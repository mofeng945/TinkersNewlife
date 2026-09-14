# 杜兰达尔之剑：把原 256×256 贴图"忠实缩绘"成像素风（保留原本造型，不重新设计）
#
# 输入：tools/art-src/durandal_sword_256.png（用户原图，256×256）
# 输出：textures/item/durandal_sword.png（默认 64×64；$SIZE 改成 32 出 32×32）
#
# 为什么缩绘而不是重画：原图是写实向的黑剑（实测饱和度比 0.006 ≈ 纯灰阶，
# 4115/5767 个不透明像素落在最暗的 0~15 亮度区间），造型细节都在轮廓里，重画必然走形。
#
# 性能：全程 LockBits + byte[] 直接读写像素。
# （不要用 GetPixel/SetPixel：6 万次托管调用要几十秒，这是上一版"跑很久"的原因。）
#
# 可调参数：
#   $SIZE             目标分辨率（32 / 64）
#   $ALPHA_THRESHOLD  轮廓阈值：方块平均不透明度低于它就判透明（决定胖瘦）
#   $GAIN / $GAMMA    亮度映射（原图极暗；GAIN 越小越"黑"，太小细节会糊掉）
#   $OUTLINE          是否描 1px 深色边

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$srcPath = Join-Path $root 'tools\art-src\durandal_sword_256.png'
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'

$SIZE = 64
$ALPHA_THRESHOLD = 0.52
$GAIN = 1.5
$GAMMA = 0.95
$OUTLINE = $false
$OUTLINE_HEX = '#0A0A0F'

if (-not (Test-Path $srcPath)) { throw ("missing source art: " + $srcPath) }
$src = New-Object System.Drawing.Bitmap $srcPath
$SW = $src.Width; $SH = $src.Height
$data = $src.LockBits((New-Object System.Drawing.Rectangle 0, 0, $SW, $SH), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $data.Stride
$srcBytes = New-Object byte[] ($stride * $SH)
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $srcBytes, 0, $srcBytes.Length)
$src.UnlockBits($data)
$src.Dispose()

$block = $SW / [double]$SIZE
$outB = New-Object byte[] ($SIZE * $SIZE * 4)   # 输出像素（BGRA）
$outA = New-Object byte[] ($SIZE * $SIZE)       # 只记 alpha，供描边判断

function Clamp255([double]$v) { if ($v -lt 0) { return 0 } elseif ($v -gt 255) { return 255 } else { return [int][Math]::Round($v) } }

for ($oy = 0; $oy -lt $SIZE; $oy++) {
    for ($ox = 0; $ox -lt $SIZE; $ox++) {
        $aSum = 0.0; $rSum = 0.0; $gSum = 0.0; $bSum = 0.0; $n = 0
        $y0 = [int][Math]::Floor($oy * $block); $y1 = [int][Math]::Ceiling(($oy + 1) * $block)
        $x0 = [int][Math]::Floor($ox * $block); $x1 = [int][Math]::Ceiling(($ox + 1) * $block)
        for ($sy = $y0; $sy -lt $y1; $sy++) {
            if ($sy -lt 0 -or $sy -ge $SH) { continue }
            $rowBase = $sy * $stride
            for ($sx = $x0; $sx -lt $x1; $sx++) {
                if ($sx -lt 0 -or $sx -ge $SW) { continue }
                $i = $rowBase + $sx * 4
                $a = $srcBytes[$i + 3] / 255.0
                $aSum += $a
                $bSum += $srcBytes[$i] * $a
                $gSum += $srcBytes[$i + 1] * $a
                $rSum += $srcBytes[$i + 2] * $a
                $n++
            }
        }
        if ($n -eq 0) { continue }
        if (($aSum / $n) -lt $ALPHA_THRESHOLD) { continue }

        if ($aSum -le 0.0001) { $r = 0.0; $g = 0.0; $b = 0.0 }
        else { $r = $rSum / $aSum; $g = $gSum / $aSum; $b = $bSum / $aSum }

        $lum = (0.299 * $r + 0.587 * $g + 0.114 * $b) / 255.0
        $boost = [Math]::Pow($lum, $GAMMA) * $GAIN
        $scale = if ($lum -gt 0.0001) { $boost / $lum } else { 1.0 }
        $o = ($oy * $SIZE + $ox) * 4
        $outB[$o]     = Clamp255 ($b * $scale)
        $outB[$o + 1] = Clamp255 ($g * $scale)
        $outB[$o + 2] = Clamp255 ($r * $scale)
        $outB[$o + 3] = 255
        $outA[$oy * $SIZE + $ox] = 1
    }
}

if ($OUTLINE) {
    $ocB = [Convert]::ToInt32($OUTLINE_HEX.Substring(5, 2), 16)
    $ocG = [Convert]::ToInt32($OUTLINE_HEX.Substring(3, 2), 16)
    $ocR = [Convert]::ToInt32($OUTLINE_HEX.Substring(1, 2), 16)
    $edge = New-Object System.Collections.Generic.List[int]
    for ($y = 0; $y -lt $SIZE; $y++) {
        for ($x = 0; $x -lt $SIZE; $x++) {
            if ($outA[$y * $SIZE + $x] -eq 1) { continue }
            $near = $false
            if ($x -gt 0 -and $outA[$y * $SIZE + $x - 1] -eq 1) { $near = $true }
            if (-not $near -and $x -lt $SIZE - 1 -and $outA[$y * $SIZE + $x + 1] -eq 1) { $near = $true }
            if (-not $near -and $y -gt 0 -and $outA[($y - 1) * $SIZE + $x] -eq 1) { $near = $true }
            if (-not $near -and $y -lt $SIZE - 1 -and $outA[($y + 1) * $SIZE + $x] -eq 1) { $near = $true }
            if ($near) { $edge.Add($y * $SIZE + $x) }
        }
    }
    foreach ($idx in $edge) {
        $o = $idx * 4
        $outB[$o] = $ocB; $outB[$o + 1] = $ocG; $outB[$o + 2] = $ocR; $outB[$o + 3] = 255
    }
}

$pf = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb   # 用 ::new() 传枚举，New-Object 会把枚举当字符串解析
$canvas = [System.Drawing.Bitmap]::new($SIZE, $SIZE, $pf)
$dst = $canvas.LockBits((New-Object System.Drawing.Rectangle 0, 0, $SIZE, $SIZE), [System.Drawing.Imaging.ImageLockMode]::WriteOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
[System.Runtime.InteropServices.Marshal]::Copy($outB, 0, $dst.Scan0, $outB.Length)
$canvas.UnlockBits($dst)

$out = Join-Path $outDir 'durandal_sword.png'
$canvas.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
Write-Host ("generated " + $out + " (" + $SIZE + "x" + $SIZE + ", gain=" + $GAIN + ", outline=" + $OUTLINE + ")")
