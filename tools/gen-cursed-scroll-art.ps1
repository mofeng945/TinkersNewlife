# 古代咒术残卷：照参考图的**外形**重上色（灰黑纸张）
#
# 输入：tools/art-src/ref_scroll.png（用户给的参考图，16×16 原生像素画：两端木轴 + 中间纸张）
# 输出：textures/item/ancient_cursed_scroll.png（16×16）
#
# 做法：**逐像素保留参考图的形状与明暗层次**，只把配色换掉：
#   · 按"饱和度"区分 木轴（棕色，饱和度高）与 纸张（米黄，饱和度低）；
#   · 纸张：亮度映射到灰黑纸的灰阶（保留原来的卷层条纹明暗）；
#   · 木轴：亮度映射到近黑的暗木色（略带暖调，保留轴头的辨识度）。
# 外形 100% 来自参考图；配色是用户要的"灰黑色纸张"。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools\gen-cursed-scroll-art.ps1
# 想还原参考图的米黄配色：$PAPER_OUT_LO/HI 改 176/255、$WOOD_OUT_LO/HI 改 14/128。

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$srcPath = Join-Path $root 'tools\art-src\ref_scroll.png'
$outPath = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item\ancient_cursed_scroll.png'
if (-not (Test-Path $srcPath)) { throw ("missing reference: " + $srcPath) }

$PAPER_IN_LO = 176.0; $PAPER_IN_HI = 255.0; $PAPER_OUT_LO = 40.0; $PAPER_OUT_HI = 96.0
$WOOD_IN_LO  = 14.0;  $WOOD_IN_HI  = 130.0; $WOOD_OUT_LO  = 10.0; $WOOD_OUT_HI  = 52.0
$SAT_SPLIT = 0.40
$WOOD_WARM = 5

$pf = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
$src = [System.Drawing.Bitmap]::new($srcPath)
$W = $src.Width; $H = $src.Height
$d = $src.LockBits((New-Object System.Drawing.Rectangle 0, 0, $W, $H), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, $pf)
$st = $d.Stride
$sb = [byte[]]::new($st * $H)
[System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $sb, 0, $sb.Length)
$src.UnlockBits($d)

$out = [System.Drawing.Bitmap]::new($W, $H, $pf)
$od = $out.LockBits((New-Object System.Drawing.Rectangle 0, 0, $W, $H), [System.Drawing.Imaging.ImageLockMode]::WriteOnly, $pf)
$ob = [byte[]]::new($st * $H)

function Lerp([double]$v, [double]$lo, [double]$hi, [double]$olo, [double]$ohi) {
    $t = ($v - $lo) / ($hi - $lo)
    if ($t -lt 0) { $t = 0 } elseif ($t -gt 1) { $t = 1 }
    return $olo + ($ohi - $olo) * $t
}

for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
        $i = $y * $st + $x * 4
        $o = $y * $st + $x * 4
        if ($sb[($i + 3)] -eq 0) { $ob[($o + 3)] = 0; continue }
        $bb = [int]$sb[$i]; $gg = [int]$sb[($i + 1)]; $rr = [int]$sb[($i + 2)]
        $mx = [Math]::Max($rr, [Math]::Max($gg, $bb))
        $mn = [Math]::Min($rr, [Math]::Min($gg, $bb))
        $sat = 0.0
        if ($mx -gt 0) { $sat = ($mx - $mn) / [double]$mx }
        $lum = 0.299 * $rr + 0.587 * $gg + 0.114 * $bb
        if ($sat -ge $SAT_SPLIT) {
            $v = Lerp $lum $WOOD_IN_LO $WOOD_IN_HI $WOOD_OUT_LO $WOOD_OUT_HI
            $nr = [int][Math]::Min(255, $v + $WOOD_WARM)
            $ng = [int]$v
            $nb = [int][Math]::Max(0, $v - 2)
        }
        else {
            $v = Lerp $lum $PAPER_IN_LO $PAPER_IN_HI $PAPER_OUT_LO $PAPER_OUT_HI
            $nr = [int]$v; $ng = [int]$v; $nb = [int][Math]::Min(255, $v + 3)
        }
        $ob[$o] = [byte]$nb; $ob[($o + 1)] = [byte]$ng; $ob[($o + 2)] = [byte]$nr; $ob[($o + 3)] = 255
    }
}
[System.Runtime.InteropServices.Marshal]::Copy($ob, 0, $od.Scan0, $ob.Length)
$out.UnlockBits($od)
$src.Dispose()
$out.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose()
Write-Host ("generated " + $outPath + " (" + $W + "x" + $H + ", palette remapped from ref_scroll.png)")
