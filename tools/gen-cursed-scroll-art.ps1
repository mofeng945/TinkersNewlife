# 古代咒术残卷 物品贴图（手绘像素图 + 生成脚本）
#
# 造型：灰黑纸张的**卷起来**的卷轴，斜放；左下是卷身（圆柱高光 + 端面暗环 + 一点咒力紫印），
#       右上是被撕破的纸尾；纸面有几道墨痕；左上受光、右下压暗；整体描一圈近黑边。
#
# 为什么改用"手画像素图"：程序化噪声版连改几轮都像"一串方块/香肠"；像素图能一次把形状定死。
#
# 输出：textures/item/ancient_cursed_scroll.png（16×16；$SCALE=2 可出 32×32 放大版）
# 用法：powershell -ExecutionPolicy Bypass -File tools\gen-cursed-scroll-art.ps1
#
# 注意：PowerShell 的哈希表键**不区分大小写**（'H' 与 'h' 会冲突报错），
#       所以调色板一律用符号做键（%、+、=、-、:）。

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
$SCALE = 1

# 图例： . 透明   # 描边   % 卷身高光   + 纸亮部   = 纸亮   - 纸中   : 纸暗   k 墨痕/端面   p 咒力紫印
$MAP = @(
    '................',
    '................',
    '...........##...',
    '..........#+#...',
    '.........#+-#...',
    '........#+k#....',
    '.......#+-#.....',
    '......#+k#......',
    '.....#+-#.......',
    '....#%+-#.......',
    '...#%+-#........',
    '..#%%+-#........',
    '..#%%:-#........',
    '..#kpk#.........',
    '...###..........',
    '................'
)

function C([string]$hex) {
    return [System.Drawing.Color]::FromArgb(255,
        [Convert]::ToInt32($hex.Substring(1, 2), 16),
        [Convert]::ToInt32($hex.Substring(3, 2), 16),
        [Convert]::ToInt32($hex.Substring(5, 2), 16))
}
$pal = @{}
$pal['#'] = C '#0A0A0D'
$pal['%'] = C '#74747F'
$pal['+'] = C '#5A5A65'
$pal['='] = C '#45454F'
$pal['-'] = C '#34343C'
$pal[':'] = C '#26262C'
$pal['k'] = C '#16161A'
$pal['p'] = C '#3A2650'

$H = $MAP.Count
$W = $MAP[0].Length
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
$out = Join-Path $outDir 'ancient_cursed_scroll.png'
$canvas.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
Write-Host ("generated " + $out + " (" + ($W * $SCALE) + "x" + ($H * $SCALE) + ", " + $painted + " px painted)")
