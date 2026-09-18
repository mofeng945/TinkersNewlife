# 把「正本底图」grey.png 同步到另外 4 张底图（hat / robe / mage_leggings / mage_boots）
#
# 为什么需要：巫师套装的自绘模型按装备槽选"槽底图"（头=hat、胸=robe、腿=mage_leggings、脚=mage_boots），
# 而这 4 张 + 兜底的 grey.png **内容必须完全一致**（同一份灰阶 UV 图）✓
# —— 只改 grey.png 就会出现"帽子对了、袍子还是旧的" ✗。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\sync-wizard-base-textures.ps1
#   （可选 -Source hat.png ：以某一张为准反推，默认以 grey.png 为准 ✓）
#
# 详见 docs\巫师套装-底图修改指南.md

param(
    [string]$Source = 'grey.png'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard'
$srcPath = Join-Path $texDir $Source
if (-not (Test-Path -LiteralPath $srcPath)) { Write-Host "找不到源文件：$srcPath"; exit 1 }

# 尺寸校验（必须是 128x128 ✓）
$im = [System.Drawing.Image]::FromFile($srcPath)
$w = $im.Width; $h = $im.Height
$im.Dispose()
if ($w -ne 128 -or $h -ne 128) {
    Write-Host "⚠ 底图尺寸应为 128x128，实际 ${w}x${h}（WizardArmorModel.TEX_W/TEX_H = 128）"; exit 1
}

$targets = @('hat.png', 'robe.png', 'mage_leggings.png', 'mage_boots.png')
foreach ($t in $targets) {
    if ($t -eq $Source) { continue }
    Copy-Item -LiteralPath $srcPath -Destination (Join-Path $texDir $t) -Force
}

Write-Host "已同步（源 = $Source）：$($targets -join ', ')"
Write-Host ""
Write-Host "当前 5 张底图的哈希（应全部一致）："
Get-ChildItem (Join-Path $texDir '*.png') |
    Where-Object { $_.Name -ne 'transparent.png' } |
    Sort-Object Name |
    ForEach-Object { Write-Host ("  {0,-20} {1}" -f $_.Name, (Get-FileHash $_.FullName).Hash.Substring(0, 16)) }
Write-Host ""
Write-Host "接着：.\\gradlew build → 关掉游戏 → powershell -File tools\\deploy.ps1"
