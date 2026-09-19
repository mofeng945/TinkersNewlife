# 把 Blockbench 的「巫师护腿」模型换算成模组用的腿部几何（贴合原版腿 + 不超出袍子下摆 ✓）
#
# 用户 2026-09-19 导出的 wizard_leggings.json：顶层组 right_leg / left_leg ✓，
#   每组子组 body（→槽0 镶板）/ trim（→槽1 锁链基底）/ lace（→槽2 法袍系带）✓
#   ⚠ 两条腿**故意不一样**（用户确认 ✓）：右腿 = body+trim+lace ✓，左腿 = body+lace（**没有 trim** ✓）、
#     且左腿 lace 更短（3.0 vs 5.1 ✓）⇒ 本脚本**每条腿各按自己的元素来** ✓ 不做镜像、不补块 ✓
#
# 换算规则：
#   横向 SXZ=0.97：用户每条腿宽 ~4.5~4.7 ⇒ 4.4~4.6，罩住原版 4 宽的腿留余量 ✓；
#                  同时把**深度夹进袍摆**（袍摆 z −2.289~2.411 ✓；4.7×0.97 = 4.56 ⇒ ±2.28 ✓ 刚好不露 ✓）
#   纵向 SY = 12/19.52344 ≈ 0.6146：用户模型从脚(0)到胯(19.52) ⇒ 顶面对齐我们腿顶（局部 0 = 胯 ✓）、
#                  底面对齐脚踝（局部 12 ✓）—— 原版腿就是局部 0~12（世界 y 12~24 ✓）
#   水平居中：各腿按**自己的 x 中心**（实测右 5.45313 / 左 10.54688 ✓）→ 我们腿骨局部 0 ✓
#   截短    ：任何越过袍摆底边（世界 y=20.196 ⇒ 腿局部 8.196 ✓）的块，高度缩到刚好不越界 ✓
#              （只有右腿的 trim / 链甲内衬会碰到 ✓ 重复方格纹压扁看不出来 ✓）
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_leggings.json",
    [double]$SXZ = 0.97,
    [double]$LegTopUserY = 19.52344,
    [double]$OurLegHeight = 12.0,
    [switch]$NoPaint,
    [switch]$NoClamp
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
if (-not (Test-Path -LiteralPath $Json)) {
    $hit = Get-ChildItem "$env:USERPROFILE\Desktop" -Recurse -Depth 2 -Filter (Split-Path $Json -Leaf) -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($hit) { Write-Host ("（{0} 不在 ⇒ 改用 {1} ✓）" -f $Json, $hit.FullName); $Json = $hit.FullName } else { throw "找不到 $Json ✗" }
}
$atlasPath = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard\grey.png'
$j = [System.IO.File]::ReadAllText((Resolve-Path $Json), [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$SY = $OurLegHeight / $LegTopUserY

# ---------- 组 → (腿骨, 槽) ----------
$slotOf = @{ 'body' = 'plating'; 'trim' = 'maille'; 'lace' = 'lace' }
$picked = New-Object System.Collections.ArrayList
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
Write-Host ("顶层组：{0}；共取 {1} 块（两条腿各按自己的数据 ✓）" -f (($j.groups | ForEach-Object { $_.name }) -join '/'), $picked.Count)

# ---------- 每条腿各自的 x 中心 ----------
$legCenter = @{}
foreach ($grp in ($picked | Group-Object Top)) {
    $xs = @()
    foreach ($q in $grp.Group) {
        $xs += [double]$j.elements[$q.Index].from[0]
        $xs += [double]$j.elements[$q.Index].to[0]
    }
    $legCenter[$grp.Name] = ((($xs | Measure-Object -Minimum).Minimum) + (($xs | Measure-Object -Maximum).Maximum)) / 2.0
}
foreach ($k in ($legCenter.Keys | Sort-Object)) { Write-Host ("  " + $k + " 的 x 中心 = " + [Math]::Round($legCenter[$k], 5)) }

# ---------- 换算 ----------
$boxes = @()
foreach ($p in ($picked | Sort-Object Index)) {
    $e = $j.elements[$p.Index]
    $fx = [double]$e.from[0]; $fy = [double]$e.from[1]; $fz = [double]$e.from[2]
    $tx = [double]$e.to[0];   $ty = [double]$e.to[1];   $tz = [double]$e.to[2]
    $w = ($tx - $fx) * $SXZ
    $h = ($ty - $fy) * $SY
    $d = ($tz - $fz) * $SXZ
    # 局部坐标一律相对腿骨（pivot 世界 (±1.9,12,0) ✓，腿方块局部 x -2~2 ✓）
    $x = ($fx - $legCenter[$p.Top]) * $SXZ
    $z = ($fz - 8.0) * $SXZ
    $y = ($LegTopUserY - $ty) * $SY         # 顶面对齐局部 y=0（胯 ✓；y 向下 ✓）
    $boxes += [pscustomobject]@{
        Index = $p.Index; Slot = $p.Slot; Bone = $p.Top
        X = $x; Y = $y; Z = $z; W = $w; H = $h; D = $d
        U = 0; V = 0; LW = (2 * $w + 2 * $d); LH = ($h + $d)
    }
}
Write-Host ("换算：SXZ={0}  SY={1:N4}  方块 {2} 个" -f $SXZ, $SY, $boxes.Count)

# ---------- 截短：越过袍摆底边的一律缩到刚好不越界（腿局部 8.196 ✓）----------
$hemBottomLocal = 20.196 - 12.0
if (-not $NoClamp) {
    foreach ($b in $boxes) {
        if ($b.Y + $b.H -gt $hemBottomLocal + 0.0001) {
            $oldH = $b.H
            $b.H = [Math]::Max(0.25, $hemBottomLocal - $b.Y)
            Write-Host ("  截短 #{0} {1}/{2}：高 {3:N3} → {4:N3}（底对齐袍摆世界 y=20.196 ✓）" -f $b.Index, $b.Bone, $b.Slot, $oldH, $b.H)
        }
    }
}

# ---------- 下摆约束自检（世界坐标 ✓）----------
$hemX = 4.535; $hemZmin = -2.289; $hemZmax = 2.411; $hemBottom = 20.196
foreach ($b in $boxes) {
    $pivot = if ($b.Bone -eq 'left_leg') { 1.9 } else { -1.9 }
    $worldX0 = $pivot + $b.X; $worldX1 = $worldX0 + $b.W
    $worldY1 = 12 + $b.Y + $b.H
    $ok = ($worldX0 -ge -$hemX - 0.001) -and ($worldX1 -le $hemX + 0.001) -and `
          (($b.Z + 0.001) -ge $hemZmin) -and (($b.Z + $b.D - 0.001) -le $hemZmax) -and ($worldY1 -le $hemBottom + 0.001)
    Write-Host ("  #{0,-2} {1,-10} {2,-8} 世界 x[{3:N2}..{4:N2}] z[{5:N2}..{6:N2}] 底 y={7:N2}  {8}" -f `
        $b.Index, $b.Bone, $b.Slot, $worldX0, $worldX1, $b.Z, ($b.Z + $b.D), $worldY1, $(if ($ok) { '✓在下摆内' } else { '⚠ 超出下摆' }))
}

# ---------- UV 装箱（避开一切已用像素 ✓ 同法袍 ✓）----------
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
foreach ($c in ($boxes | Sort-Object -Property @{Expression = { [Math]::Ceiling($_.LH) }} -Descending)) {
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

# ---------- 画默认灰阶（面序按 §368 ✓：源 up 占第一格、源 down 占第二格 ✓）----------
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
        FillRect ($u + $d) $v ($u + $d + $w) ($v + $d) 249
        FillRect ($u + $d + $w) $v ($u + $d + $w + $w) ($v + $d) 160
        FillRect $u ($v + $d) ($u + $d) ($v + $d + $h) 215
        FillRect ($u + $d) ($v + $d) ($u + $d + $w) ($v + $d + $h) 205
        FillRect ($u + $d + $w) ($v + $d) ($u + $d + $w + $d) ($v + $d + $h) 179
        FillRect ($u + $d + $w + $d) ($v + $d) ($u + 2 * $d + 2 * $w) ($v + $d + $h) 197
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
    $parent = if ($c.Bone -eq 'left_leg') { 'legLPart' } else { 'legRPart' }
    $name = "{0}_{1}_{2}" -f $c.Bone, $c.Slot, $c.Index
    Write-Host ("addLocalBox({0}, ""{1}"", {2}F, {3}F, {4}F, {5}F, {6}F, {7}F, {8}, {9});" -f `
        $parent, $name, [Math]::Round($c.X, 5), [Math]::Round($c.Y, 5), [Math]::Round($c.Z, 5), `
        [Math]::Round($c.W, 5), [Math]::Round($c.H, 5), [Math]::Round($c.D, 5), $c.U, $c.V)
}
