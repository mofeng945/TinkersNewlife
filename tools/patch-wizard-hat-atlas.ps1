# 把「法帽新加的两个方块」所需的 UV 区域画进灰阶底图
# （grey.png / hat.png / robe.png / mage_leggings.png / mage_boots.png）
#
# 为什么需要它：
#   巫师套装的自绘模型（WizardArmorModel）把贴图当"每面光照灰阶底图"用 ——
#   每个方块按 Minecraft 标准盒式 UV 布局采样底图（u,v,w,h,d 见模型里的 texOffs）：
#       up    = (u+d,      v  )  尺寸 w×d      down  = (u+d+w,    v  )  尺寸 w×d
#       east  = (u,        v+d)  尺寸 d×h      north = (u+d,      v+d)  尺寸 w×h
#       west  = (u+d+w,    v+d)  尺寸 d×h      south = (u+d+w+d,  v+d)  尺寸 w×h
#   底图里 0..64 行是原有 11 个方块的区域，**100 行以下整片透明** ✓
#   ⇒ 新方块的布局必须画在 100 行以下（透明区域采样出来 = 隐形 ✗）。
#
# 调色板（从原有区域实测）：顶 249 / 底 160 / 东 179 / 北 205 / 西 215 / 南 197
#
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-wizard-hat-atlas.ps1
# 之后如需每材料贴图，再跑 tools\gen-wizard-armor-materials.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard'
$greyPath = Join-Path $texDir 'grey.png'
if (-not (Test-Path -LiteralPath $greyPath)) { Write-Host "缺灰阶底图：$greyPath"; exit 1 }

$TOP = 249; $BOTTOM = 160; $EAST = 179; $NORTH = 205; $WEST = 215; $SOUTH = 197

# ⚠ 不能直接 [Bitmap]::FromFile 后就地 Save（GDI+ 会因文件句柄仍被自己占着而报 generic error ✗）
#   ⇒ 先复制进内存、放掉原句柄，再写回同名文件 ✓
$src = [System.Drawing.Image]::FromFile($greyPath)
$img = New-Object System.Drawing.Bitmap $src
$src.Dispose()
if ($img.Width -ne 128 -or $img.Height -ne 128) {
    Write-Host "底图尺寸应为 128x128，实际 $($img.Width)x$($img.Height)"; exit 1
}

function Fill($x, $y, $w, $h, $v) {
    for ($yy = $y; $yy -lt ($y + $h); $yy++) {
        for ($xx = $x; $xx -lt ($x + $w); $xx++) {
            $img.SetPixel($xx, $yy, [System.Drawing.Color]::FromArgb(255, $v, $v, $v))
        }
    }
}

# ---------- 方块 A：背后垂布（w=8, h=4.8, d=0.08）texOffs(0, 100) ----------
# 可见的是 north / south 两个 8×4.8 的大面；d 只有 0.08 ⇒ 侧边四舍五入成 1 像素
Fill 1  101 7 5 $NORTH      # north 大面（x 0.08~8.08）
Fill 9  101 8 5 $SOUTH      # south 大面（x 8.16~16.16）
Fill 0  101 1 5 $EAST       # east 细边
Fill 8  101 1 5 $WEST       # west 细边
Fill 0  100 8 1 $TOP        # up（0.08 像素高，占一行）
Fill 8  100 9 1 $BOTTOM     # down（0.08 像素高，占一行）

# ---------- 方块 B：尖顶小球（w=d=h=1.6）texOffs(24, 100) ----------
Fill 24 100 8 5 $NORTH      # 先铺底，再用六个面的 2×2 像素格子覆盖
Fill 26 100 2 1 $TOP        # up    （x 25.6~27.2, y 100~101.6）
Fill 28 100 2 1 $BOTTOM     # down  （x 27.2~28.8）
Fill 24 101 2 2 $EAST       # east  （y 101.6~103.2）
Fill 26 101 2 2 $NORTH      # north
Fill 28 101 2 2 $WEST       # west
Fill 30 101 2 2 $SOUTH      # south

$img.Save($greyPath, [System.Drawing.Imaging.ImageFormat]::Png)
$img.Dispose()

# 其余 4 张底图与 grey.png 内容完全一致（同一份灰阶图，分别给生成器当"底图"用）⇒ 直接复制
foreach ($name in @('hat.png', 'robe.png', 'mage_leggings.png', 'mage_boots.png')) {
    Copy-Item -LiteralPath $greyPath -Destination (Join-Path $texDir $name) -Force
}
Write-Host "已更新灰阶底图并同步到 hat/robe/mage_leggings/mage_boots ✓"
