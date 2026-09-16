# 程序化生成 16×16「锭」贴图（按材料色带上色）。
#
# 为什么程序化：魔金 / 圣灵是铁魔法联动材料，原版与各模组都没有对应的锭物品 ——
# 直接搬原版/别的模组的锭贴图来重上色会牵扯到别人的美术资源 ✗，所以这里用
# 一张自己写的像素掩码 + 材料色带生成，轮廓与明暗都是本模组自己的 ✓。
# （属于**占位级**美术，随时可以用手绘替换 —— 替换时保持文件名与 16×16 即可 ✓）
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

# 掩码：'.' 透明 / 'o' 描边(最暗) / 'f' 前面(中) / 't' 顶面(亮) / 'h' 高光(最亮)
$mask = @(
    '................',
    '................',
    '................',
    '.....oooooo.....',
    '....ohhhhhto....',
    '...ottttttto....',
    '..ottttttttto...',
    '..ottttttttto...',
    '..offfffffffo...',
    '..offfffffffo...',
    '..offfffffffo...',
    '..ooffffffoo....',
    '...oooooooo.....',
    '................',
    '................',
    '................'
)
if ($mask.Count -ne 16) { throw "掩码必须是 16 行（当前 $($mask.Count) 行）" }

$map = @{
    'o' = $c[0]
    'f' = $c[2]
    't' = $c[4]
    'h' = $c[6]
}

$bmp = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt 16; $y++) {
    $row = $mask[$y]
    if ($row.Length -ne 16) { throw "掩码第 $($y + 1) 行长度不是 16（当前 $($row.Length)）" }
    for ($x = 0; $x -lt 16; $x++) {
        $ch = [string]$row[$x]
        if ($ch -eq '.') { $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0)); continue }
        $bmp.SetPixel($x, $y, $map[$ch])
    }
}
$dst = Join-Path $outDir "$Name.png"
$bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Host "  生成 $Name.png（16x16，色带基底 $($Ramp[4])）"
