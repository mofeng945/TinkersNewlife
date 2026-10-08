<#
  gen_long_short_blade_placeholders.ps1

  为匠魂工具「长短刃」(long_short_blade) 生成【程序化占位贴图】。
  ⚠ 这些是占位图，用户以后会手绘替换（见 AGENTS.md §1）。

  产出（16x16 PNG，透明底，无动画）：
    textures/item/tool/long_short_blade/head.png     宽刃     (tconstruct:broad_blade)  index 0
    textures/item/tool/long_short_blade/binding.png  坚韧套环 (tconstruct:tough_binding) index 1
    textures/item/tool/long_short_blade/handle.png   坚韧手柄 (tconstruct:tough_handle) index 2
    textures/item/tool/long_short_blade/blade.png    小型剑刃 (tconstruct:small_blade)  index 3
    textures/item/tool/long_short_blade/limb.png     弓臂     (tconstruct:bow_limb)     index 4
  外加 5 张破损版（同名 + _broken）：
    做法照本仓 whip 的先例 —— 整体压暗到 60%、中部两三点当裂纹、边缘崩掉一两个像素当缺口；
    ⚠ 只改【原图已存在】的像素：裂纹画在不透明像素上、缺口是删掉像素 ⇒ 绝不新增轮廓外的点。

  用法：
    powershell -ExecutionPolicy Bypass -File tools\gen_long_short_blade_placeholders.ps1
    powershell -ExecutionPolicy Bypass -File tools\gen_long_short_blade_placeholders.ps1 -Force

  ⚠ 默认【拒绝覆盖已存在的文件】—— 保护用户以后手绘的成品。要重生成占位图请加 -Force。

  ⚠⚠ 图例用【数字】而不是大小写字母：PowerShell 的哈希键**大小写不敏感** ✗
     ⇒ 'H' 与 'h'、'B' 与 'b' 会被判成重复键并直接语法报错（本脚本第一版就栽在这里 ✓）。
     0-9 无此问题 ✓，务必不要改回字母。
#>
[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# 仓库根 = 本脚本的上一级
$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item\tool\long_short_blade'

# ---------------------------------------------------------------- 调色板（键一律用数字）
$PALETTE = @{
    '#' = '#241A22'   # 描边（暗）
    '1' = '#C9D2E0'   # 刃·亮
    '2' = '#F0F5FF'   # 刃·高光
    '3' = '#97A2B4'   # 刃·中
    '4' = '#5C6675'   # 刃·暗
    '5' = '#D8B44A'   # 套环·亮（黄铜）
    '6' = '#8C6B18'   # 套环·暗
    '7' = '#B07A45'   # 手柄·亮（木）
    '8' = '#6E4522'   # 手柄·暗
    '9' = '#C79A63'   # 弓臂·亮
    '0' = '#7A5330'   # 弓臂·暗
}

# ---------------------------------------------------------------- 像素图（每行必须 16 字符）
$SPRITES = @{}

$SPRITES['head'] = @(          # 宽刃：宽大的叶形刃
    '................',
    '.......##.......',
    '......#11#......',
    '......#112#.....',
    '.....#1112#.....',
    '.....#11122#....',
    '....#111122#....',
    '....#111122#....',
    '...#1111122#....',
    '...#3333322#....',
    '..#33333322#....',
    '..#44444444#....',
    '...#444444#.....',
    '....######......',
    '................',
    '................'
)

$SPRITES['blade'] = @(         # 小型剑刃：细而短
    '................',
    '................',
    '.......##.......',
    '......#12#......',
    '......#12#......',
    '......#12#......',
    '......#12#......',
    '......#12#......',
    '......#12#......',
    '......#32#......',
    '......#42#......',
    '......#44#......',
    '.......##.......',
    '................',
    '................',
    '................'
)

$SPRITES['binding'] = @(       # 坚韧套环：一个环
    '................',
    '................',
    '................',
    '................',
    '....######......',
    '...#566665#.....',
    '..#5......6#....',
    '..#5......6#....',
    '..#5......6#....',
    '..#6......6#....',
    '...#666666#.....',
    '....######......',
    '................',
    '................',
    '................',
    '................'
)

$SPRITES['handle'] = @(        # 坚韧手柄：竖木柄（两端有箍）
    '................',
    '................',
    '......####......',
    '......#78#......',
    '......#78#......',
    '......#78#......',
    '......#78#......',
    '......#78#......',
    '......#78#......',
    '......#78#......',
    '......#78#......',
    '......#78#......',
    '......#78#......',
    '......####......',
    '................',
    '................'
)

$SPRITES['limb'] = @(          # 弓臂：一条弯臂
    '................',
    '................',
    '.........####...',
    '.......#99#.....',
    '......#99#......',
    '.....#99#.......',
    '....#99#........',
    '....#90#........',
    '....#90#........',
    '.....#90#.......',
    '......#90#......',
    '.......#90#.....',
    '........#90#....',
    '.........####...',
    '................',
    '................'
)

# ---------------------------------------------------------------- 工具函数
function Convert-HexToColor([string]$hex) {
    $h = $hex.TrimStart('#')
    return [System.Drawing.Color]::FromArgb(
        255,
        [Convert]::ToInt32($h.Substring(0, 2), 16),
        [Convert]::ToInt32($h.Substring(2, 2), 16),
        [Convert]::ToInt32($h.Substring(4, 2), 16))
}

function New-SpriteBitmap([string[]]$rows, [string]$name) {
    if ($rows.Count -ne 16) {
        throw ("[$name] 需要正好 16 行，实际 {0} 行" -f $rows.Count)
    }
    for ($y = 0; $y -lt 16; $y++) {
        if ($rows[$y].Length -ne 16) {
            throw ("[$name] 第 {0} 行宽度应为 16，实际 {1}：'{2}'" -f ($y + 1), $rows[$y].Length, $rows[$y])
        }
    }
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $ch = [string]$rows[$y][$x]
            if ($ch -eq '.') { continue }   # 透明底
            if (-not $PALETTE.ContainsKey($ch)) {
                throw ("[$name] 未知像素字符 '{0}'（行 {1} 列 {2}）" -f $ch, ($y + 1), ($x + 1))
            }
            $bmp.SetPixel($x, $y, (Convert-HexToColor $PALETTE[$ch]))
        }
    }
    return $bmp
}

# 破损版：压暗 60% + 裂纹（画在已有像素上）+ 缺口（删掉边缘像素）
function New-BrokenBitmap([System.Drawing.Bitmap]$src) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    # 1) 整体压暗到 60%（只动【已存在】的像素）
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $p = $src.GetPixel($x, $y)
            if ($p.A -eq 0) { continue }
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(
                255,
                [int]([Math]::Round($p.R * 0.6)),
                [int]([Math]::Round($p.G * 0.6)),
                [int]([Math]::Round($p.B * 0.6))))
        }
    }
    # 2) 找出不透明像素的包围盒，好在"中部"下刀
    $minX = 99; $maxX = -1; $minY = 99; $maxY = -1
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            if ($bmp.GetPixel($x, $y).A -ne 0) {
                if ($x -lt $minX) { $minX = $x }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    if ($maxX -lt 0) { return $bmp }   # 全透明（不该发生）
    $midY = [int](($minY + $maxY) / 2)
    $crack = Convert-HexToColor '#2A2320'
    # 3) 裂纹：只在【已经不透明】的像素上改色 ⇒ 绝不新增轮廓外的点
    $cracked = 0
    for ($d = -1; $d -le 1 -and $cracked -lt 3; $d++) {
        $yy = $midY + $d
        if ($yy -lt 0 -or $yy -gt 15) { continue }
        for ($x = $minX; $x -le $maxX -and $cracked -lt 3; $x++) {
            if ($bmp.GetPixel($x, $yy).A -ne 0) {
                $bmp.SetPixel($x, $yy, $crack)
                $cracked++
                break
            }
        }
    }
    # 4) 缺口：把包围盒右缘的像素删掉 2 个（删 ⇒ 崩口，颜色不会跑到轮廓外）
    $notched = 0
    for ($yy = $minY + 1; $yy -le $maxY - 1 -and $notched -lt 2; $yy++) {
        for ($x = $maxX; $x -ge $minX; $x--) {
            if ($bmp.GetPixel($x, $yy).A -ne 0) {
                $bmp.SetPixel($x, $yy, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                $notched++
                break
            }
        }
    }
    return $bmp
}

# ---------------------------------------------------------------- 主流程
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    Write-Host ("已建目录：{0}" -f $outDir)
}

$order = @('head', 'binding', 'handle', 'blade', 'limb')
$made = 0
$skipped = 0
foreach ($name in $order) {
    $base = New-SpriteBitmap $SPRITES[$name] $name
    $bro = New-BrokenBitmap $base
    foreach ($pair in @(@{ Bmp = $base; File = ($name + '.png') }, @{ Bmp = $bro; File = ($name + '_broken.png') })) {
        $dst = Join-Path $outDir $pair.File
        if ((Test-Path -LiteralPath $dst) -and -not $Force) {
            Write-Host ("  跳过（已存在，加 -Force 才覆盖）：{0}" -f $pair.File)
            $skipped++
            continue
        }
        $pair.Bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Host ("  写出：{0}  {1}B" -f $pair.File, (Get-Item -LiteralPath $dst).Length)
        $made++
    }
    $base.Dispose()
    $bro.Dispose()
}

Write-Host ("完成：写出 {0} 个文件，跳过 {1} 个（目录 {2}）" -f $made, $skipped, $outDir)
