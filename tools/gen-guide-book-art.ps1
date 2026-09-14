# 新生神秘学编年史（guide_book）物品贴图（手绘像素图 + 生成脚本）
#
# 造型：一本合起来的**方册**——深紫黑封面 + 左侧书脊高光 + 右侧/底部的米色书页堆 +
#       四角金属护角 + 封面中央一枚紫色符印；整体描一圈近黑边，左上受光、右下压暗。
#
# 输出：textures/item/guide_book.png（16×16；$SCALE=2 可出 32×32）
# 用法：powershell -ExecutionPolicy Bypass -File tools\gen-guide-book-art.ps1
#
# 注意：PowerShell 哈希表键**不区分大小写**，调色板一律用符号键，避免 'H'/'h' 之类冲突。

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
$SCALE = 1

# 图例： . 透明   # 描边   = 封面亮（左上受光/书脊）   - 封面中   : 封面暗
#        o 书页亮   n 书页中   % 金属护角   P 符印亮紫   p 符印暗紫
$MAP = @(
    '................',
    '................',
    '..#==========#..',
    '..%=-------on#..',
    '..#=-------on#..',
    '..#=-------on#..',
    '..#=-------on#..',
    '..#=---p---on#..',
    '..#=--PPP--on#..',
    '..#=---p---on#..',
    '..#=-------on#..',
    '..#=-------on#..',
    '..%=-------on#..',
    '..#nnnnnnnnnn#..',
    '..############..',
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
$pal['='] = C '#413A52'   # 封面亮（书脊 / 受光边）
$pal['-'] = C '#2C2738'   # 封面中
$pal[':'] = C '#1C1826'   # 封面暗
$pal['o'] = C '#D9D3B8'   # 书页亮
$pal['n'] = C '#B0AA90'   # 书页中
$pal['%'] = C '#8E8EA0'   # 金属护角
$pal['P'] = C '#9A6FE0'   # 符印亮紫
$pal['p'] = C '#5B3A96'   # 符印暗紫

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
$out = Join-Path $outDir 'guide_book.png'
$canvas.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
Write-Host ("generated " + $out + " (" + ($W * $SCALE) + "x" + ($H * $SCALE) + ", " + $painted + " px painted)")
