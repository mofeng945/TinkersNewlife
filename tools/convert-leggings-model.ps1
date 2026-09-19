# 把 Blockbench 的「巫师护腿」模型换算成模组用的腿部几何（贴合原版腿 + 不超出袍子下摆 ✓）
#
# 用户 2026-09-19 导出的 wizard_leggings.json：2 个顶层组 right_leg / left_leg ✓，
#   每组子组 body（→槽0 镶板）/ trim（→槽1 锁链基底）/ lace（→槽2 法袍系带）✓
#   ⚠ 用户这版**左腿缺 trim 块**、左腿 lace 高度也和右腿不一样（5.1 vs 3.0 ✓ 实测 ✓）
#     ⇒ 默认以**右腿为模板**生成对称的一对（左腿由右腿反射 ✓ 见 import 的 -SymPairs ✓）
#
# 换算规则：
#   横向 SXZ：用户每条腿宽 ~4.5~4.7 ⇒ x0.97 ⇒ 4.4~4.6，正好罩住原版 4 宽的腿 ✓，
#             且**深度被夹在下摆内**（下摆 z -2.289~2.411 ✓；用户模型深 4.7 ⇒ x0.97 = 4.56 ⇒ ±2.28 ✓ 刚好不露 ✓）
#   纵向 SY ：用户模型从脚(0)到胯(19.52) ⇒ x(12/19.52=0.6147) ⇒ 顶面对齐腿顶(局部 y=0 ✓)、
#             底面对齐脚踝(局部 y=12 ✓) —— 原版腿就是局部 0~12（世界 y 12~24 ✓）
#   水平居中：用户腿中心 x=5.45313（右腿 ✓）→ 我们 right_leg 的中心 x=-1.9 ✓（left_leg 为 +1.9 ✓）
#   z 居中   ：用户 z 中心 8 → 我们 z 0 ✓
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_leggings.json",
    [double]$SXZ = 0.97,
    [double]$LegCenterUserX = 5.45313,   # 用户模型里右腿的 x 中心
    [double]$LegTopUserY = 19.52344,     # 用户模型里腿的最高点（= 胯 ✓）
    [double]$OurLegX = 1.9,              # 原版腿骨中心 |x|（pivot ±1.9 ✓）
    [double]$OurLegHeight = 12.0,        # 原版腿长（局部 0~12 ✓）
    [switch]$NoPaint,
    [switch]$NoClamp   # 默认把超出袍摆底边的那块截短（本件是链甲内衬 ✓ 压扁看不出来 ✓）
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$atlasPath = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard\grey.png'
$j = [System.IO.File]::ReadAllText((Resolve-Path $Json), [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$SY = $OurLegHeight / $LegTopUserY

# ---------- 只取"右腿"组的方块（左腿由 import 反射生成 ✓）----------
$slotOf = @{ 'body' = 'plating'; 'trim' = 'maille'; 'lace' = 'lace' }
$picked = New-Object System.Collections.ArrayList   # 函数里改数组要用 ArrayList ✗（`+=` 会变成局部变量 ✓）
function Walk-G($node, [string]$top) {
    foreach ($c in $node.children) {
        if ($c -is [int] -or $c -is [long]) {
            $slot = $slotOf[$node.name]
            if (-not $slot) { throw "子组名 $($node.name) 不在 body/trim/lace 里 ✗" }
            [void]$picked.Add([pscustomobject]@{ Index = [int]$c; Top = $top; Slot = $slot })
        } else { Walk-G $c $top }
    }
}
foreach ($g in $j.groups) { Walk-G $g $g.name }
Write-Host ("用户模型顶层组：{0}；本次只用【{1}】那组（{2} 块 ✓）" -f `
    (($j.groups | ForEach-Object { $_.name }) -join '/'), 'right_leg', ($picked | Where-Object Top -eq 'right_leg').Count)

$boxes = @()
foreach ($p in ($picked | Where-Object Top -eq 'right_leg' | Sort-Object Index)) {
    $e = $j.elements[$p.Index]
    $fx = [double]$e.from[0]; $fy = [double]$e.from[1]; $fz = [double]$e.from[2]
    $tx = [double]$e.to[0];   $ty = [double]$e.to[1];   $tz = [double]$e.to[2]
    $w = ($tx - $fx) * $SXZ
    $h = ($ty - $fy) * $SY
    $d = ($tz - $fz) * $SXZ
    # 坐标一律写成**腿骨局部**（原版 right_leg 的 pivot 在世界 x=-1.9, y=12 ✓，腿方块局部 x -2..2 ✓）
    #   横向：相对"用户腿中心"的偏移照搬 ✓（x 方向不翻 ✓ 与法袍同约定 ✓）
    #   纵向：顶面对齐我们腿的局部 y=0（= 胯 ✓），y 向下 ✓
    $x = ($fx - $LegCenterUserX) * $SXZ
    $z = ($fz - 8.0) * $SXZ
    $y = ($LegTopUserY - $ty) * $SY
    $boxes += [pscustomobject]@{
        Index = $p.Index; Slot = $p.Slot; Bone = 'right_leg'
        X = $x; Y = $y; Z = $z; W = $w; H = $h; D = $d
        U = 0; V = 0
        LW = (2 * $w + 2 * $d); LH = ($h + $d)
    }
}
Write-Host ("换算：SXZ={0}  SY={1:N4}（12/{2} ✓）  方块 {3} 个" -f $SXZ, $SY, $LegTopUserY, $boxes.Count)

# ---------- 截短：任何超出袍摆底边（世界 y=20.196 ✓）的方块，把**高度**缩到刚好不越界 ✓ ----------
# 为什么只截不整体缩放：body/lace 是外层装饰（要保住比例 ✓ 单独穿护腿时看得见 ✓）；
# 而越界的只有 maille（链甲内衬 ✓ 重复方格纹 ✓）⇒ 压扁也看不出 ✓，且藏在袍下 ✓。
$hemBottomLocal = 20.196 - 12.0     # 腿骨 pivot 在世界 y=12 ✓
if (-not $NoClamp) {
    foreach ($b in $boxes) {
        if ($b.Y + $b.H -gt $hemBottomLocal + 0.0001) {
            $old = $b.H
            $b.H = [Math]::Max(0.25, $hemBottomLocal - $b.Y)
            Write-Host ("  截短 #{0} {1}：高 {2:N3} → {3:N3}（底对齐袍摆世界 y=20.196 ✓）" -f $b.Index, $b.Slot, $old, $b.H)
        }
    }
}
# ---------- 下摆约束自检（世界坐标 ✓）----------
# 下摆：body_plating_1/2 两块，x[-4.535..-0.335] 与 [0.335..4.535]、z[-2.289..2.411]、底 y=20.196 ✓
$hemX = 4.535; $hemZmin = -2.289; $hemZmax = 2.411; $hemBottom = 20.196
foreach ($b in $boxes) {
    $worldX0 = (-$OurLegX) + $b.X          # 腿骨 pivot 在世界 x=-1.9（right_leg ✓）⇒ 世界 x = -1.9 + 局部 x
    $worldX1 = $worldX0 + $b.W
    $worldY1 = 12 + $b.Y + $b.H            # 腿骨 pivot 在世界 y=12 ✓（局部 y 向下 ✓）
    $ok = ($worldX0 -ge -$hemX - 0.001) -and ($worldX1 -le $hemX + 0.001) -and ($worldY1 -le $hemBottom + 0.001)
    Write-Host ("  #{0,-2} {1,-8} 世界 x[{2:N2}..{3:N2}] z[{4:N2}..{5:N2}] 底 y={6:N2}  {7}" -f `
        $b.Index, $b.Slot, $worldX0, $worldX1, $b.Z, ($b.Z + $b.D), $worldY1, $(if ($ok) { '✓在下摆内' } else { '⚠ 超出下摆' }))
}

# ---------- 左腿 = 右腿关于 x=0 的镜像（各自**独立 UV 槽** ✓ 这样贴图才能左右镜像 ✓）----------
$mirrorBoxes = @()
foreach ($b in $boxes) {
    $mirrorBoxes += [pscustomobject]@{
        Index = $b.Index; Slot = $b.Slot; Bone = 'left_leg'
        X = (-$b.X - $b.W); Y = $b.Y; Z = $b.Z; W = $b.W; H = $b.H; D = $b.D
        U = 0; V = 0; LW = $b.LW; LH = $b.LH
    }
}
$boxes = @($boxes) + @($mirrorBoxes)
Write-Host ("左腿镜像块 {0} 个已加入（共 {1} 块 ✓）" -f $mirrorBoxes.Count, $boxes.Count)
# ---------- UV 装箱（避开一切已用像素 ✓ 与袍同法 ✓）----------
$atlas = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($atlasPath))
$used = New-Object 'bool[,]' 128, 128
for ($y = 0; $y -lt 128; $y++) {
    for ($x = 0; $x -lt 128; $x++) {
        if ($atlas.GetPixel($x, $y).A -gt 0) {
            for ($dy = -1; $dy -le 1; $dy++) { for ($dx = -1; $dx -le 1; $dx++) {
                $nx = $x + $dx; $ny = $y + $dy
                if ($nx -ge 0 -and $ny -ge 0 -and $nx -lt 128 -and $ny -lt 128) { $used[$nx, $ny] = $true }
            } }
        }
    }
}
function Test-Free([int]$px, [int]$py, [int]$pw, [int]$ph) {
    if ($px -lt 0 -or $py -lt 0 -or ($px + $pw) -gt 128 -or ($py + $ph) -gt 128) { return $false }
    for ($yy = $py; $yy -lt ($py + $ph); $yy++) { for ($xx = $px; $xx -lt ($px + $pw); $xx++) { if ($used[$xx, $yy]) { return $false } } }
    return $true
}
function Mark-Used([int]$px, [int]$py, [int]$pw, [int]$ph) {
    for ($yy = $py; $yy -lt ($py + $ph); $yy++) { for ($xx = $px; $xx -lt ($px + $pw); $xx++) { $used[$xx, $yy] = $true } }
}
$ordered = $boxes | Sort-Object -Property @{Expression = { [Math]::Ceiling($_.LH) }} -Descending
foreach ($c in $ordered) {
    $bw = [int][Math]::Ceiling($c.LW) + 1
    $bh = [int][Math]::Ceiling($c.LH) + 1
    $placed = $false
    for ($yy = 0; $yy -le (128 - $bh) -and -not $placed; $yy++) {
        for ($xx = 0; $xx -le (128 - $bw); $xx++) {
            if (Test-Free $xx $yy $bw $bh) { $c.U = $xx; $c.V = $yy; Mark-Used $xx $yy $bw $bh; $placed = $true; break }
        }
    }
    if (-not $placed) { throw "底图装不下：方块 #$($c.Index) 需要 $bw x $bh ✗" }
}
$free = 0
for ($y = 0; $y -lt 128; $y++) { for ($x = 0; $x -lt 128; $x++) { if (-not $used[$x, $y]) { $free++ } } }
Write-Host ("装箱完成 ✓ 剩余空闲 $free 像素")

# ---------- 画默认灰阶（面序按 §368 的表 ✓：up 占第一格、down 占第二格 ✓）----------
if (-not $NoPaint) {
    $srcImg = [System.Drawing.Image]::FromFile($atlasPath)
    $img = New-Object System.Drawing.Bitmap $srcImg
    $srcImg.Dispose()
    function FillRect($px0, $py0, $px1, $py1, $val) {
        for ($yy = [Math]::Floor($py0); $yy -lt [Math]::Ceiling($py1); $yy++) {
            for ($xx = [Math]::Floor($px0); $xx -lt [Math]::Ceiling($px1); $xx++) {
                if ($xx -lt 0 -or $yy -lt 0 -or $xx -ge 128 -or $yy -ge 128) { continue }
                $img.SetPixel($xx, $yy, [System.Drawing.Color]::FromArgb(255, $val, $val, $val))
            }
        }
    }
    foreach ($c in $boxes) {
        $u = $c.U; $v = $c.V; $w = $c.W; $h = $c.H; $d = $c.D
        FillRect ($u + $d) $v ($u + $d + $w) ($v + $d) 249                      # 源 up（视觉上）→ MC 的 DOWN 槽 ✓
        FillRect ($u + $d + $w) $v ($u + $d + $w + $w) ($v + $d) 160            # 源 down → MC 的 UP 槽 ✓
        FillRect $u ($v + $d) ($u + $d) ($v + $d + $h) 215                      # 西 west ✓
        FillRect ($u + $d) ($v + $d) ($u + $d + $w) ($v + $d + $h) 205          # 北 north ✓
        FillRect ($u + $d + $w) ($v + $d) ($u + $d + $w + $d) ($v + $d + $h) 179 # 东 east ✓
        FillRect ($u + $d + $w + $d) ($v + $d) ($u + 2 * $d + 2 * $w) ($v + $d + $h) 197 # 南 south ✓
    }
    $img.Save($atlasPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $img.Dispose()
    $texDir = Split-Path $atlasPath
    foreach ($t in @('hat.png', 'robe.png', 'mage_leggings.png', 'mage_boots.png')) {
        Copy-Item -LiteralPath $atlasPath -Destination (Join-Path $texDir $t) -Force
    }
    Write-Host "已画入底图并同步 5 张 ✓"
}
$atlas.Dispose()

# ---------- 打印 Java ----------
Write-Host ""
foreach ($c in ($boxes | Sort-Object Index)) {
    $name = "{0}_{1}_{2}" -f $c.Bone, $c.Slot, $c.Index
    $line = "addLocalBox({9}, `"{0}`", {1}F, {2}F, {3}F, {4}F, {5}F, {6}F, {7}, {8});" -f `
        $name, [Math]::Round($c.X, 5), [Math]::Round($c.Y, 5), [Math]::Round($c.Z, 5), `
        [Math]::Round($c.W, 5), [Math]::Round($c.H, 5), [Math]::Round($c.D, 5), $c.U, $c.V, $(if ($c.Bone -eq 'left_leg') { 'legLPart' } else { 'legRPart' })
    Write-Host $line
}
