# 呪蔵（咒藏 / Curse Vault）美术资源生成脚本
#
# 生成三样东西（都可重复执行，覆盖同名文件）：
#   textures/block/curse_vault_plate.png   16×16  铁质边框/底板（带倒角与铆钉）
#   textures/block/curse_vault_bar.png     16×16  笼条金属（上半两行=横向高光，左两列=纵向高光）
#   textures/block/curse_vault_energy.png  32×32×24 帧  旋转紫色能量（配合 .mcmeta 播放）
#
# 用法：powershell -ExecutionPolicy Bypass -File tools\gen-curse-vault-art.ps1
# 想换配色就改下面的 $IronRgb / $EnergyRamp。

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\block'
if (-not (Test-Path $texDir)) { New-Item -ItemType Directory -Path $texDir -Force | Out-Null }

# 简单可复现的伪随机（不用 Random，保证每次生成结果一致）
function Hash01([int]$a, [int]$b, [int]$c) {
    $h = ($a * 73856093) -bxor ($b * 19349663) -bxor ($c * 83492791)
    $h = $h -band 0x7FFFFFFF
    return ($h % 10000) / 10000.0
}
function Clamp([double]$v, [double]$lo, [double]$hi) {
    if ($v -lt $lo) { return $lo }
    if ($v -gt $hi) { return $hi }
    return $v
}

# ============================================================
#  1) 铁质板：curse_vault_plate.png
# ============================================================
$plate = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        # 基色：偏蓝的暗铁 + 细噪点
        $n = (Hash01 $x $y 7) * 14.0 - 7.0
        $r = 60 + $n; $g = 63 + $n; $b = 72 + $n
        # 倒角：上/左亮，下/右暗
        if ($y -eq 0) { $r += 26; $g += 26; $b += 26 }
        if ($x -eq 0) { $r += 22; $g += 22; $b += 22 }
        if ($y -eq 15) { $r -= 26; $g -= 26; $b -= 26 }
        if ($x -eq 15) { $r -= 18; $g -= 18; $b -= 18 }
        # 两条竖缝，把板子切出三段
        if ($x -eq 5 -or $x -eq 10) { $r -= 14; $g -= 14; $b -= 12 }
        if ($x -eq 6 -or $x -eq 11) { $r += 10; $g += 10; $b += 10 }
        $plate.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,
            [int](Clamp $r 0 255), [int](Clamp $g 0 255), [int](Clamp $b 0 255)))
    }
}
# 四角铆钉（2×2，带一圈暗边）
foreach ($p in @(@(2, 2), @(12, 2), @(2, 12), @(12, 12))) {
    $cx = $p[0]; $cy = $p[1]
    for ($dy = -1; $dy -le 1; $dy++) {
        for ($dx = -1; $dx -le 1; $dx++) {
            $x = $cx + $dx; $y = $cy + $dy
            if ($x -lt 0 -or $x -gt 15 -or $y -lt 0 -or $y -gt 15) { continue }
            $c = $plate.GetPixel($x, $y)
            if ([Math]::Abs($dx) -eq 1 -or [Math]::Abs($dy) -eq 1) {
                $plate.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,
                    [int](Clamp ($c.R - 20) 0 255), [int](Clamp ($c.G - 20) 0 255), [int](Clamp ($c.B - 18) 0 255)))
            } else {
                $plate.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,
                    [int](Clamp ($c.R + 52) 0 255), [int](Clamp ($c.G + 52) 0 255), [int](Clamp ($c.B + 48) 0 255)))
            }
        }
    }
}
$plate.Save((Join-Path $texDir 'curse_vault_plate.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$plate.Dispose()
Write-Host '已生成 curse_vault_plate.png'

# ============================================================
#  2) 笼条：curse_vault_bar.png
#     ＊约定：第 0~1 行 = 横向高光（竖条用），第 0~1 列 = 纵向高光（横条用）
#       其余区域填中性金属，万一被采样到也不难看
# ============================================================
$bar = New-Object System.Drawing.Bitmap 16, 16
# 先铺底
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = (Hash01 $x $y 21) * 20.0 - 10.0
        $bar.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,
            [int](Clamp (80 + $n) 0 255), [int](Clamp (84 + $n) 0 255), [int](Clamp (94 + $n) 0 255)))
    }
}
# 第 0~1 列：纵向高光（暗→亮→暗，中段最亮）
for ($y = 0; $y -lt 16; $y++) {
    $t = 1.0 - ([Math]::Abs($y - 7.5) / 8.0)   # 0..~0.94
    $v = 44 + 112 * [Math]::Pow($t, 1.35)
    for ($x = 0; $x -le 1; $x++) {
        $bar.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,
            [int](Clamp $v 0 255), [int](Clamp ($v + 6) 0 255), [int](Clamp ($v + 20) 0 255)))
    }
}
# 第 0~1 行：横向高光（覆盖左上角，竖条采样到的就是这一段）
for ($x = 0; $x -lt 16; $x++) {
    $t = 1.0 - ([Math]::Abs($x - 7.5) / 8.0)
    $v = 44 + 112 * [Math]::Pow($t, 1.35)
    for ($y = 0; $y -le 1; $y++) {
        $bar.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,
            [int](Clamp $v 0 255), [int](Clamp ($v + 6) 0 255), [int](Clamp ($v + 20) 0 255)))
    }
}
$bar.Save((Join-Path $texDir 'curse_vault_bar.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bar.Dispose()
Write-Host '已生成 curse_vault_bar.png'

# ============================================================
#  3) 能量团：curse_vault_energy.png（32×32 × 24 帧，竖向排列）
# ============================================================
$frames = 24
$size = 32
$energy = New-Object System.Drawing.Bitmap $size, ($size * $frames)

# 颜色渐变（0=暗紫核 → 1=惨白紫），按色标插值
$ramp = @(
    @(0.00, 18, 6, 34),
    @(0.30, 52, 16, 104),
    @(0.55, 118, 44, 196),
    @(0.78, 178, 96, 236),
    @(0.92, 226, 170, 255),
    @(1.00, 246, 232, 255)
)
function RampColor([double]$v) {
    $v = Clamp $v 0 1
    for ($i = 0; $i -lt $ramp.Count - 1; $i++) {
        $a = $ramp[$i]; $b = $ramp[$i + 1]
        if ($v -ge $a[0] -and $v -le $b[0]) {
            $t = ($v - $a[0]) / ($b[0] - $a[0])
            return @(
                [int]($a[1] + ($b[1] - $a[1]) * $t),
                [int]($a[2] + ($b[2] - $a[2]) * $t),
                [int]($a[3] + ($b[3] - $a[3]) * $t))
        }
    }
    return @(246, 232, 255)
}

for ($f = 0; $f -lt $frames; $f++) {
    $phase = $f / [double]$frames
    $ang = $phase * 2.0 * [Math]::PI
    for ($y = 0; $y -lt $size; $y++) {
        for ($x = 0; $x -lt $size; $x++) {
            $dx = ($x - 15.5) / 15.5
            $dy = ($y - 15.5) / 15.5
            $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
            $th = [Math]::Atan2($dy, $dx)
            # 三条旋臂，随帧相位旋转；半径项让它成为螺旋而不是同心圆
            $arm = [Math]::Sin(3.0 * $th + 5.2 * $r - 2.0 * $ang)
            $arm2 = [Math]::Sin(5.0 * $th - 8.0 * $r + 3.0 * $ang)
            $swirl = 0.68 * $arm + 0.32 * $arm2
            # 径向衰减 + 中心热核（核收紧、压暗，免得整团糊成一个白球）
            $fall = Clamp (1.25 - $r * 1.0) 0 1
            $core = [Math]::Exp(-[Math]::Pow($r * 3.6, 2.0))
            # 底色：边缘留一层暗紫余晖，而不是死黑
            $base = 0.14 + 0.30 * $fall
            # 细噪点（按帧分块变化，避免高频闪烁）
            $noise = ((Hash01 $x $y ([int]($f / 3))) - 0.5) * 0.14
            $v = $base + 0.58 * $fall * (0.5 + 0.5 * $swirl) + $core * 0.62 + $noise
            $v = Clamp $v 0 1
            $v = [Math]::Pow($v, 1.45)
            $c = RampColor $v
            $energy.SetPixel($x, ($f * $size + $y),
                [System.Drawing.Color]::FromArgb(255, $c[0], $c[1], $c[2]))
        }
    }
}
$energy.Save((Join-Path $texDir 'curse_vault_energy.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$energy.Dispose()
Write-Host ("已生成 curse_vault_energy.png（{0}×{1}，{2} 帧）" -f $size, ($size * $frames), $frames)

# 动画定义：frametime 2 tick（20 fps），插值让旋转更顺
$mcmeta = @'
{
  "animation": {
    "frametime": 2,
    "interpolate": true
  }
}
'@
$mcmetaPath = Join-Path $texDir 'curse_vault_energy.png.mcmeta'
[System.IO.File]::WriteAllText($mcmetaPath, $mcmeta, (New-Object System.Text.UTF8Encoding($false)))
Write-Host '已生成 curse_vault_energy.png.mcmeta'
Write-Host '完成。'
