# 程序化生成 16×16「锭」贴图（按材料色带上色）。
#
# 为什么程序化：魔金 / 圣灵是铁魔法联动材料，原版与各模组都没有对应的锭物品 ——
# 直接搬原版/别的模组的锭贴图来重上色会牵扯到别人的美术资源 ✗，所以这里自己画。
# （替换成手绘时保持同名 + 16×16 即可 ✓）
#
# 画法（试了两版才定）：
#   ✗ 第一版"手写掩码" → 形状随意，像块砖；
#   ✗ 第二版"斜顶面 + 按列取整" → 阶梯状锯齿，更难看；
#   ✓ 最终版 = **圆角长方条 + 竖直分层**：对称、整行同色，16px 下最不容易画脏：
#     · 从上到下逐层压暗（上亮下暗 = 金属感）
#     · 左两列整体提亮一档、右两列压暗一档 → 体积感
#     · 最上一行倒角高光 + 中上部一小段镜面高光
#     · 剪影描一圈最暗色（四角不描，圆角自然）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\gen-ingot-texture.ps1 `
#       -Name magic_gold_ingot -Ramp FF2B1A33,FF57307A,FF8A4A6E,FFC07A50,FFDFA845,FFF5CE72,FFFFF2BC
#
# 色带 7 个色对应亮度 0/63/102/140/178/216/255（与材料 grey_to_sprite 调色板同一组）✓
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string[]]$Ramp
)

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

if ($Ramp.Count -eq 1 -and $Ramp[0] -like '*,*') { $Ramp = $Ramp[0].Split(',') }
if ($Ramp.Count -ne 7) { throw "色带必须是 7 个颜色（当前 $($Ramp.Count) 个）" }

function Parse-Color([string]$hex) {
    $h = $hex.TrimStart('#')
    if ($h.Length -eq 6) { $h = 'FF' + $h }
    return [System.Drawing.Color]::FromArgb(
        [Convert]::ToInt32($h.Substring(0, 2), 16),
        [Convert]::ToInt32($h.Substring(2, 2), 16),
        [Convert]::ToInt32($h.Substring(4, 2), 16),
        [Convert]::ToInt32($h.Substring(6, 2), 16))
}

$c = @()
foreach ($x in $Ramp) { $c += (Parse-Color $x) }
$clear = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)

# ---- 几何：圆角长方条 ----
$x0 = 2; $x1 = 13      # 12 列宽
$y0 = 4; $y1 = 11      # 8 行高
# 每行的基础色阶（0=最暗 … 6=最亮）：上亮下暗
$rowShade = @{ 4 = 5; 5 = 4; 6 = 4; 7 = 3; 8 = 3; 9 = 2; 10 = 2; 11 = 1 }

$bmp = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, $y, $clear) } }

$filled = @{}
for ($y = $y0; $y -le $y1; $y++) {
    for ($x = $x0; $x -le $x1; $x++) {
        # 圆角：四角各切掉一个像素
        $corner = (($x -eq $x0 -or $x -eq $x1) -and ($y -eq $y0 -or $y -eq $y1))
        if ($corner) { continue }
        $idx = $rowShade[$y]
        if ($x -le $x0 + 1) { $idx = $idx + 1 }        # 左侧受光
        if ($x -ge $x1 - 1) { $idx = $idx - 1 }        # 右侧背光
        if ($idx -gt 6) { $idx = 6 }
        if ($idx -lt 0) { $idx = 0 }
        $bmp.SetPixel($x, $y, $c[$idx]); $filled["$x,$y"] = $true
    }
}

# ---- 顶行倒角高光 ----
for ($x = $x0 + 1; $x -le $x1 - 1; $x++) {
    if ($filled.ContainsKey("$x,$y0")) { $bmp.SetPixel($x, $y0, $c[6]) }
}
# ---- 中上部镜面高光小段 ----
for ($x = $x0 + 3; $x -le $x0 + 6; $x++) {
    if ($filled.ContainsKey("$x,$($y0 + 1)")) { $bmp.SetPixel($x, $y0 + 1, $c[6]) }
}
# ---- 底行压到最暗（影子）----
for ($x = $x0 + 1; $x -le $x1 - 1; $x++) {
    if ($filled.ContainsKey("$x,$y1")) { $bmp.SetPixel($x, $y1, $c[1]) }
}

# ---- 描边：剪影外侧一圈用最暗色 ----
$nb = @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))
foreach ($key in @($filled.Keys)) {
    $xy = $key.Split(','); $px = [int]$xy[0]; $py = [int]$xy[1]
    foreach ($d in $nb) {
        $nx = $px + $d[0]; $ny = $py + $d[1]
        if ($nx -lt 0 -or $ny -lt 0 -or $nx -gt 15 -or $ny -gt 15) { continue }
        if ($filled.ContainsKey("$nx,$ny")) { continue }
        $bmp.SetPixel($nx, $ny, $c[0])
    }
}

$dst = Join-Path $outDir "$Name.png"
$bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Host "  生成 $Name.png（16x16，主色 $($Ramp[4])）"
