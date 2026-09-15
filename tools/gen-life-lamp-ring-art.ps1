# 命灯指轮（life_lamp_ring）物品贴图
#
# 造型：一枚金环（circle）+ 环顶一盏小小的紫色命灯（灯焰 + 光晕），环身左下受光、右下压暗。
# 输出：textures/item/life_lamp_ring.png（16×16；$SCALE=2 可出 32×32）
# 用法：powershell -ExecutionPolicy Bypass -File tools\gen-life-lamp-ring-art.ps1
#
# 图例： . 透明   # 描边   G 金亮   g 金   d 金暗   P 灯焰亮   p 灯焰   W 焰心白   H 光晕

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
$SCALE = 1

$MAP = @(
    '................',
    '.......HH.......',
    '......HppH......',
    '......pWPp......',
    '......pWPp......',
    '.......pp.......',
    '.....##gg##.....',
    '....#GGggg#.....',
    '...#Gg...gg#....',
    '...#g.....g#....',
    '...#g.....g#....',
    '...#gg...gg#....',
    '....#gg.gg#.....',
    '.....#ddg#......',
    '......###.......',
    '................'
)

function C([string]$hex) {
    return [System.Drawing.Color]::FromArgb(255,
        [Convert]::ToInt32($hex.Substring(1, 2), 16),
        [Convert]::ToInt32($hex.Substring(3, 2), 16),
        [Convert]::ToInt32($hex.Substring(5, 2), 16))
}
$pal = @{}
$pal['#'] = C '#0A0A0D'   # 描边
$pal['G'] = C '#FFE08A'   # 金亮（受光）
$pal['g'] = C '#D9A93C'   # 金
$pal['d'] = C '#8A6414'   # 金暗
$pal['W'] = C '#FFFFFF'   # 焰心
$pal['P'] = C '#C79BFF'   # 灯焰亮
$pal['p'] = C '#8A5FD0'   # 灯焰
$pal['H'] = C '#5B3A96'   # 光晕

$H = $MAP.Count
$W = $MAP[0].Length
# 行长度自检（198 条教训：手写像素图落盘前必须查长度）
$bad = 0
for ($i = 0; $i -lt $H; $i++) { if ($MAP[$i].Length -ne $W) { Write-Host ("  !! 第 " + $i + " 行长度 " + $MAP[$i].Length + " != " + $W); $bad++ } }
if ($bad -gt 0) { throw 'pixel map rows must all be the same width' }

$canvas = [System.Drawing.Bitmap]::new($W * $SCALE, $H * $SCALE, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$empty = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
        for ($sy = 0; $sy -lt $SCALE; $sy++) {
            for ($sx = 0; $sx -lt $SCALE; $sx++) { $canvas.SetPixel($x * $SCALE + $sx, $y * $SCALE + $sy, $empty) }
        }
    }
}
$painted = 0
for ($y = 0; $y -lt $H; $y++) {
    $row = $MAP[$y]
    for ($x = 0; $x -lt $W; $x++) {
        $ch = $row.Substring($x, 1)
        if (-not $pal.ContainsKey($ch)) { continue }
        $col = $pal[$ch]
        $painted++
        for ($sy = 0; $sy -lt $SCALE; $sy++) {
            for ($sx = 0; $sx -lt $SCALE; $sx++) { $canvas.SetPixel($x * $SCALE + $sx, $y * $SCALE + $sy, $col) }
        }
    }
}
$out = Join-Path $outDir 'life_lamp_ring.png'
$canvas.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
Write-Host ("generated " + $out + " (" + ($W * $SCALE) + "x" + ($H * $SCALE) + ", " + $painted + " px painted)")
