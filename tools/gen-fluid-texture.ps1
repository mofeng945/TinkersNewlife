# 按"亮度 → 色带"给流体贴图重新上色，生成一套新的 still/flowing + .mcmeta。
#
# 为什么需要它：新流体（如「魔金精华」）要有一张和材料配色一致的贴图，
# 而手画 16×N / 32×M 的动画条不现实 —— 所以直接拿一张现成的同风格流体贴图
# （默认取已做好的熔融秘银），按像素亮度重映射到目标色带：图案与流动动画完全保留 ✓，
# 只换颜色 ✓。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\gen-fluid-texture.ps1 `
#       -Name magic_gold_essence -Source molten_mithril `
#       -Ramp FF2B1A33,FF57307A,FF8A4A6E,FFC07A50,FFDFA845,FFF5CE72,FFFFF2BC
#
# 色带 7 个色对应亮度 0/63/102/140/178/216/255（与材料 grey_to_sprite 调色板同一组）✓
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string[]]$Ramp,
    [int]$Frametime = 4
)

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\block'
$pf = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb

if ($Ramp.Count -eq 1 -and $Ramp[0] -like '*,*') { $Ramp = $Ramp[0].Split(',') }
if ($Ramp.Count -ne 7) { throw "色带必须是 7 个颜色（当前 $($Ramp.Count) 个）" }

function Parse-Color([string]$hex) {
    $h = $hex.TrimStart('#')
    if ($h.Length -eq 6) { $h = 'FF' + $h }
    return @{
        A = [Convert]::ToInt32($h.Substring(0, 2), 16)
        R = [Convert]::ToInt32($h.Substring(2, 2), 16)
        G = [Convert]::ToInt32($h.Substring(4, 2), 16)
        B = [Convert]::ToInt32($h.Substring(6, 2), 16)
    }
}

$stops = @(0, 63, 102, 140, 178, 216, 255)
$colors = @()
foreach ($c in $Ramp) { $colors += (Parse-Color $c) }

# 按亮度取色（在相邻两档之间线性插值）
function Ramp-Color([int]$grey) {
    if ($grey -le $stops[0]) { return $colors[0] }
    if ($grey -ge $stops[6]) { return $colors[6] }
    for ($i = 0; $i -lt 6; $i++) {
        $lo = $stops[$i]; $hi = $stops[$i + 1]
        if ($grey -ge $lo -and $grey -le $hi) {
            $t = ($grey - $lo) / [double]($hi - $lo)
            $a = $colors[$i]; $b = $colors[$i + 1]
            return @{
                A = [int]($a.A + ($b.A - $a.A) * $t)
                R = [int]($a.R + ($b.R - $a.R) * $t)
                G = [int]($a.G + ($b.G - $a.G) * $t)
                B = [int]($a.B + ($b.B - $a.B) * $t)
            }
        }
    }
    return $colors[6]
}

function Convert-Texture([string]$srcPath, [string]$dstPath) {
    $bmp = [System.Drawing.Bitmap]::new($srcPath)
    $w = $bmp.Width; $h = $bmp.Height
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, $pf)
    $stride = $data.Stride
    $bytes = [byte[]]::new($stride * $h)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    $bmp.UnlockBits($data); $bmp.Dispose()

    # 先统计亮度分布：源贴图的亮度往往挤在暗端（熔岩类贴图尤其明显），
    # 直接按绝对亮度套色带会整张偏暗 ✗ —— 所以取 2%~98% 分位做线性拉伸，
    # 让图案的明暗层次铺满整条色带 ✓（图案本身不变 ✓）。
    $hist = New-Object 'int[]' 256
    $count = 0
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $o = $y * $stride + $x * 4
            if ($bytes[$o + 3] -eq 0) { continue }
            $grey = [int](0.299 * $bytes[$o + 2] + 0.587 * $bytes[$o + 1] + 0.114 * $bytes[$o])
            $hist[$grey]++
            $count++
        }
    }
    $lo = 0; $hi = 255
    if ($count -gt 0) {
        $cut = [int]($count * 0.02)
        $acc = 0
        for ($i = 0; $i -lt 256; $i++) { $acc += $hist[$i]; if ($acc -ge $cut) { $lo = $i; break } }
        $acc = 0
        for ($i = 255; $i -ge 0; $i--) { $acc += $hist[$i]; if ($acc -ge $cut) { $hi = $i; break } }
    }
    if ($hi -le $lo) { $lo = 0; $hi = 255 }
    Write-Host ("    亮度范围 {0}~{1}（拉伸到 0~255）" -f $lo, $hi)

    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $o = $y * $stride + $x * 4
            $a = $bytes[$o + 3]
            if ($a -eq 0) { continue }
            $bb = $bytes[$o]; $gg = $bytes[$o + 1]; $rr = $bytes[$o + 2]
            $grey = 0.299 * $rr + 0.587 * $gg + 0.114 * $bb
            $norm = ($grey - $lo) / [double]($hi - $lo)
            if ($norm -lt 0) { $norm = 0 } elseif ($norm -gt 1) { $norm = 1 }
            $c = Ramp-Color ([int]($norm * 255))
            $bytes[$o] = [byte]$c.B
            $bytes[$o + 1] = [byte]$c.G
            $bytes[$o + 2] = [byte]$c.R
        }
    }

    $out = [System.Drawing.Bitmap]::new($w, $h, $pf)
    $od = $out.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly, $pf)
    [System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $od.Scan0, $bytes.Length)
    $out.UnlockBits($od)
    $out.Save($dstPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Host ("  生成 {0} ({1}x{2})" -f (Split-Path $dstPath -Leaf), $w, $h)
}

$mcmeta = "{`n  `"animation`": {`n    `"frametime`": $Frametime`n  }`n}`n"
foreach ($kind in @('still', 'flowing')) {
    $src = Join-Path $texDir "${Source}_${kind}.png"
    if (-not (Test-Path $src)) { throw "找不到源贴图 $src" }
    $dst = Join-Path $texDir "${Name}_${kind}.png"
    Convert-Texture $src $dst
    [System.IO.File]::WriteAllText("$dst.mcmeta", $mcmeta, (New-Object System.Text.UTF8Encoding($false)))
}
Write-Host "完成：$Name（源：$Source）"
