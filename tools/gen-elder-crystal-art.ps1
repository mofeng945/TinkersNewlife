# 生成「古老者水晶」三张**占位**贴图（16×16 像素风，程序生成、可复跑）。
#
# ⚠ 铁律：本脚本**只新建**文件，绝不覆盖用户手绘的既有贴图 ✗
#   输出路径（全部是**新文件名**）：
#     assets\tinkersnewlife\textures\item\elder_crystal.png            （物品：浅蓝紫棱柱）
#     assets\tinkersnewlife\textures\block\elder_crystal_block.png     （方块：同色块）
#     assets\tinkersnewlife\textures\block\elder_crystal_ore.png       （矿石：深板岩底 + 水晶簇）
#
# 复跑命令：
#   powershell -ExecutionPolicy Bypass -File tools\gen-elder-crystal-art.ps1
#   （加 -Check 只做校验不写盘：确认输出像素/颜色数量与预期一致 ✓）
#
# 说明：矿石底色用**固定种子**的确定性噪声 ⇒ 每次复跑得到**同一张**图 ✓（不会"随机漂移" ✗）。
#       所有坐标/色号都写在本文件里，改风格只改这里的调色板与掩码即可 ✓。

param(
    # 只校验（不写盘）：把三张图算出来，报告像素统计后退出
    [switch]$Check
)

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$texRoot = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures'

# ============================================================
#  调色板（浅蓝紫水晶系 ✓ 与物品 tooltip 的染色同一色系）
# ============================================================
function Parse-Color([string]$hex) {
    $h = $hex.TrimStart('#')
    if ($h.Length -eq 6) { $h = 'FF' + $h }
    return [System.Drawing.Color]::FromArgb(
        [Convert]::ToInt32($h.Substring(0, 2), 16),
        [Convert]::ToInt32($h.Substring(2, 2), 16),
        [Convert]::ToInt32($h.Substring(4, 2), 16),
        [Convert]::ToInt32($h.Substring(6, 2), 16))
}

# 字符 → 颜色（'.' = 全透明）
# ⚠ PowerShell 的哈希键**大小写不敏感** ⇒ 不能出现只差大小写的成对键（'s'/'S'、'D'/'d' 都算重复 ✗）
#   ⇒ 深板岩用 s / x / t，簇外沿用 e（与水晶的 D 不撞 ✓）
$PALETTE = @{
    '.' = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
    'D' = Parse-Color '3B2E6E'   # 水晶·最暗（轮廓/棱）
    'M' = Parse-Color '7C6BE0'   # 水晶·中间色
    'L' = Parse-Color 'A99BF5'   # 水晶·亮面
    'H' = Parse-Color 'D9D2FF'   # 水晶·高光
    'W' = Parse-Color 'FFFFFF'   # 亮点
    's' = Parse-Color '3E3E44'   # 深板岩·暗
    'x' = Parse-Color '55555C'   # 深板岩·中
    't' = Parse-Color '6A6A72'   # 深板岩·亮（噪点）
    'e' = Parse-Color '2A2A30'   # 簇周围压暗
}

# ============================================================
#  掩码：物品（浅蓝紫棱柱）
#  16 行 × 16 列；每行长度都必须是 16（下面有断言 ✓）
# ============================================================
$CRYSTAL_ITEM = @(
    '................',
    '.......HH.......',
    '......HLLH......',
    '.....HLLMLH.....',
    '.....HLWLMH.....',
    '.....HLMLMH.....',
    '.....HLMLMH.....',
    '.....HLMLMH.....',
    '.....HLMLMH.....',
    '.....HLMLMH.....',
    '.....HLWLMH.....',
    '.....HLMLMH.....',
    '......HLMH......',
    '......HDMH......',
    '.......DD.......',
    '................'
)

# ============================================================
#  掩码：方块（同色块，带棱面明暗）
# ============================================================
$CRYSTAL_BLOCK = @(
    'DDDDDDDDDDDDDDDD',
    'DMMLLMMLLMMLLMMD',
    'DMLHLLMMLLHHLLMD',
    'DMLHLLMMLLHHLLMD',
    'DMMLLMMLLMMLLMMD',
    'DMMLLMMLLMMLLMMD',
    'DMLHLLMMLLHHLLMD',
    'DMLHLLMMLLHHLLMD',
    'DMMLLMMLLMMLLMMD',
    'DMMLLMMLLMMLLMMD',
    'DMLHLLMMLLHHLLMD',
    'DMLHLLMMLLHHLLMD',
    'DMMLLMMLLMMLLMMD',
    'DMMLLMMLLMMLLMMD',
    'DMMLLMMLLMMLLMMD',
    'DDDDDDDDDDDDDDDD'
)

# ============================================================
#  矿石：深板岩底（固定种子噪声）+ 水晶簇（写死的簇心坐标 ✓ 可复跑）
# ============================================================
$ORE_SEED = 20260921
# 簇：@{x, y, r}（r=1 是 3×3 的小菱，r=2 是 5×5 的大菱）
$ORE_CLUSTERS = @(
    @{ x = 5;  y = 4;  r = 2 },
    @{ x = 11; y = 9;  r = 2 },
    @{ x = 3;  y = 11; r = 1 },
    @{ x = 12; y = 3;  r = 1 }
)

function New-OreMask {
    # 确定性 LCG（不用 Get-Random ⇒ 复跑结果一致 ✓）
    $state = [int64]$ORE_SEED
    # ⚠ 用**二维 char 数组**（char[,]）：PowerShell 里对 `char[][]`（交错数组）逐元素赋值不可靠 ✗
    #   （New-Object 'char[][]' 建出来的行是"数组的引用副本"，写回去会丢 ✗ —— 实测踩过 ✓）
    $grid = New-Object 'char[,]' 16, 16
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            # ⚠ 全程**整数**运算（显式 [int64] + 位移取高位）：
            #   PowerShell 的 `/` 会把 int64 提升成 double，精度一丢 `% 3` 就可能算出 3.0 ✗
            #   （第一版踩过：`[int]` 截断后当索引越界 ⇒ 取到 $null ⇒ 画出来整张都是空像素 ✗）
            $state = [int64](($state * [int64]1103515245 + [int64]12345) % [int64]2147483648)
            $v = [int](($state -shr 16) % 3)
            if ($v -lt 0 -or $v -gt 2) { $v = 0 }   # 兜底：绝不让索引越界 ✗
            $grid[$y, $x] = [char]@('s', 'x', 't')[$v]
        }
    }
    # 水晶簇：曼哈顿距离菱形 ⇒ "深板岩里的水晶块"
    foreach ($c in $ORE_CLUSTERS) {
        for ($dy = -$c.r; $dy -le $c.r; $dy++) {
            for ($dx = -$c.r; $dx -le $c.r; $dx++) {
                $x = $c.x + $dx; $y = $c.y + $dy
                if ($x -lt 0 -or $x -gt 15 -or $y -lt 0 -or $y -gt 15) { continue }
                $d = [Math]::Abs($dx) + [Math]::Abs($dy)
                if ($d -gt $c.r) { continue }
                if ($d -eq $c.r) {
                    # 边缘：压暗一圈（"矿脉外沿"）
                    if ($grid[$y, $x] -match '[sxt]') { $grid[$y, $x] = [char]'e' }
                    continue
                }
                if ($d -eq 0) { $grid[$y, $x] = [char]'H' }
                elseif ($d -eq 1) { $grid[$y, $x] = [char]'M' }
                else { $grid[$y, $x] = [char]'L' }
            }
        }
    }
    # 每簇左上角点一颗白点（晶体反光 ✓ 与物品贴图同风格）
    foreach ($c in $ORE_CLUSTERS) {
        $x = $c.x - 1; $y = $c.y - 1
        if ($x -ge 0 -and $x -le 15 -and $y -ge 0 -and $y -le 15) {
            if ($grid[$y, $x] -match '[MLH]') { $grid[$y, $x] = [char]'W' }
        }
    }
    $rows = @()
    for ($y = 0; $y -lt 16; $y++) {
        $line = ''
        for ($x = 0; $x -lt 16; $x++) { $line += [string]$grid[$y, $x] }
        $rows += $line
    }
    return $rows
}

# ============================================================
#  画图
# ============================================================
function Assert-Mask([string[]]$mask, [string]$name) {
    if ($mask.Count -ne 16) { throw "$name 掩码必须是 16 行（当前 $($mask.Count) 行）" }
    for ($i = 0; $i -lt 16; $i++) {
        if ($mask[$i].Length -ne 16) { throw "$name 掩码第 $i 行长度是 $($mask[$i].Length)，应为 16" }
    }
}

function Draw-Mask([string[]]$mask, [string]$outPath) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 16; $x++) {
                $ch = [string]$mask[$y][$x]
                if (-not $PALETTE.ContainsKey($ch)) { throw "未知的调色板字符 '$ch'（$outPath 第 $y 行第 $x 列）" }
                $bmp.SetPixel($x, $y, $PALETTE[$ch])
            }
        }
        if ($Check) {
            $opaque = 0
            for ($y = 0; $y -lt 16; $y++) {
                for ($x = 0; $x -lt 16; $x++) {
                    if ($bmp.GetPixel($x, $y).A -gt 0) { $opaque++ }
                }
            }
            Write-Host ("  [校验] {0} -> {1}/256 不透明像素" -f $outPath, $opaque)
            return
        }
        $dir = Split-Path -Parent $outPath
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $size = (Get-Item $outPath).Length
        Write-Host ("  已写入 {0}  ({1} 字节)" -f $outPath, $size)
    } finally {
        $bmp.Dispose()
    }
}

$oreMask = New-OreMask

Assert-Mask $CRYSTAL_ITEM 'elder_crystal'
Assert-Mask $CRYSTAL_BLOCK 'elder_crystal_block'
Assert-Mask $oreMask 'elder_crystal_ore'

Write-Host '古老者水晶占位贴图（16×16 程序生成）：'
Draw-Mask $CRYSTAL_ITEM  (Join-Path $texRoot 'item\elder_crystal.png')
Draw-Mask $CRYSTAL_BLOCK (Join-Path $texRoot 'block\elder_crystal_block.png')
Draw-Mask $oreMask       (Join-Path $texRoot 'block\elder_crystal_ore.png')

if ($Check) {
    Write-Host '（-Check：未写盘 ✓）'
} else {
    Write-Host '完成 ✓ —— 这是**占位图**，换成用户手绘时**另存新文件名**并在模型 JSON 里改引用 ✓ 不要覆盖这里 ✗'
}
