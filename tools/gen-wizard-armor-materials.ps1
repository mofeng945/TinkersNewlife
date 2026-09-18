# 按材料生成巫师套装的盔甲贴图（给"每材料一张图"用）
#
# 输入：textures/tinker_armor/wizard_armor/all_grey.png（灰阶底图，128×128）
#       assets/tinkersnewlife/mantle/colors.json（材料色总表）
# 输出：textures/tinker_armor/wizard_armor/<材料>.png × 12（把灰阶图的亮度乘上该材料色）
#
# 说明：这些图与"顶点着色"效果等价（都只是换颜色 ✗ 不换纹样），
#       保留它们是给"将来要做每材料不同纹样/资源包"用；当前渲染仍走顶点着色 ✓。

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\tinker_armor\wizard_armor'
$greyPath = Join-Path $texDir 'all_grey.png'
$colorsPath = Join-Path $root 'src\main\resources\assets\tinkersnewlife\mantle\colors.json'

if (-not (Test-Path -LiteralPath $greyPath)) { Write-Host "缺灰阶底图：$greyPath"; exit 1 }
$grey = [System.Drawing.Bitmap]::FromFile($greyPath)
$colors = Get-Content -LiteralPath $colorsPath -Raw -Encoding UTF8

# 可做盔甲的 12 个材料（与 WizardArmorColors.java 保持一致）
$materials = @('arcane_iron','ashen_ink','cursed_metal','dark_metal','divine_gold',
               'dragonsteel_fire','dragonsteel_ice','dragonsteel_lightning','dreadsteel',
               'magic_gold','origin_alloy','pyrium')

$made = 0
foreach ($m in $materials) {
    $match = [regex]::Match($colors, '"' + $m + '"\s*:\s*"#([0-9a-fA-F]{6})"')
    if (-not $match.Success) { Write-Host "  跳过（无颜色）：$m"; continue }
    $hex = $match.Groups[1].Value
    $cr = [Convert]::ToInt32($hex.Substring(0,2),16)
    $cg = [Convert]::ToInt32($hex.Substring(2,2),16)
    $cb = [Convert]::ToInt32($hex.Substring(4,2),16)

    $out = New-Object System.Drawing.Bitmap 128,128
    for ($y = 0; $y -lt 128; $y++) {
        for ($x = 0; $x -lt 128; $x++) {
            $p = $grey.GetPixel($x, $y)
            if ($p.A -eq 0) { continue }                       # 透明处保持透明
            # 以灰阶亮度为系数乘材料色（0.72~0.98 之间，避免死黑/过曝）
            $k = 0.72 + ($p.R / 255.0) * 0.26
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(
                $p.A,
                [Math]::Min(255, [int]($cr * $k)),
                [Math]::Min(255, [int]($cg * $k)),
                [Math]::Min(255, [int]($cb * $k))))
        }
    }
    $target = Join-Path $texDir ($m + '.png')
    $out.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    $made++
    Write-Host ("  {0,-22} -> {1}" -f $m, (Split-Path $target -Leaf))
}
$grey.Dispose()
Write-Host "生成完成：$made 张（目录 $texDir）"
