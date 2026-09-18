# 把 Blockbench 导出的「巫师法袍」模型转成模组用的 Java 模型 + 底图 UV 区域
#
# 输入：桌面 wizard_robe.json（用户的模型：x/z 中心 8、y 向上、旋转全 0 ✓）
# 输出：
#   ① 控制台打印可直接粘贴的 addBox(...) Java 代码（各骨骼的**局部坐标** ✓）
#   ② 把 11 个方块的底图 UV 区域画进 grey.png（默认明暗灰阶 ✓ 之后用户可自行绘制 ✓）
#
# 缩放（用户凭印象建的模型，由我们拟合人形骨骼 ✓）：
#   body      ：s = 1.00  —— 袍身 9.3 宽 ≈ 身体 8 + 松量 ✓；领口对齐身体局部 y = -0.5 ✓
#   arms      ：s = 1.12  —— 袖 4.3 宽 → 4.8 宽，套住 4 宽手臂留 0.4 余量 ✓；袖顶对齐手臂局部 y = -2 ✓
#   左右判定   ：按 x 正负（用户模型 x>8 那侧 = MC 的 left_arm ✓ 名字是镜像的 ✗）
#   槽映射     ：body→plating、trim→maille、lace→lace ✓（可改）

param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_robe.json",
    [switch]$PaintAtlas = $true,
    [switch]$NoPaint
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$atlasPath = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard\grey.png'
$j = [System.IO.File]::ReadAllText((Resolve-Path $Json), [System.Text.Encoding]::UTF8) | ConvertFrom-Json

# ---------- 组 → (骨骼, 槽) ----------
$map = @{}
function Walk($node, [string]$parentName) {
    $name = $node.name
    foreach ($c in $node.children) {
        if ($c -is [int] -or $c -is [long]) {
            $bone = $parentName
            $slot = switch ($name) { 'body' { 'plating' } 'trim' { 'maille' } 'lace' { 'lace' } default { $null } }
            if (-not $bone -or -not $slot) { throw "方块 $c 的组层级无法识别（父=$parentName 名=$name）" }
            $map[[int]$c] = @{ Bone = $bone; Slot = $slot }
        } else {
            Walk $c $name
        }
    }
}
foreach ($g in $j.groups) {
    # 顶层组名 = 部位
    foreach ($c in $g.children) {
        if ($c -is [int] -or $c -is [long]) { throw "顶层组 $($g.name) 直接挂了方块，无法判断槽" }
        Walk $c $g.name
    }
}

# ---------- 每根骨骼的换算参数 ----------
# 局部空间：MC 模型 y 向下 ✓；body 局部 y 0 = 颈肩；arm 局部 y -2 = 肩顶
# ⚠ 横向(SXZ)与纵向(SY)分开：袖子横向要留松量(1.12 ✓)，但纵向要压短 ✓
#   （用户反馈"两个胳膊太长了" ✓：原来 SY=SXZ=1.12 ⇒ 袖长 16.6 格 ✗，手臂只有 12 格 ⇒ 多出手腕 4.6 格 ✗；
#    现在 SY=0.75 ⇒ 袖长 11.1 格 ✓ 正好到手腕 ✓）
$bones = @{
    'body'      = @{ SXZ = 1.00; SY = 1.00; YRef = 18.70; YAt = -0.5; XCenter = 8.0;   XAt = 0.0  }
    'left_arm'  = @{ SXZ = 1.12; SY = 0.75; YRef = 18.51; YAt = -2.0; XCenter = 14.95; XAt = 1.0  }
    'right_arm' = @{ SXZ = 1.12; SY = 0.75; YRef = 18.51; YAt = -2.0; XCenter = 1.02;  XAt = -1.0 }
}

$boxes = @()
for ($i = 0; $i -lt $j.elements.Count; $i++) {
    $e = $j.elements[$i]
    $info = $map[$i]
    $bone = $info.Bone
    # 用户的命名是镜像的：x > 8 的“right_arm”组其实是 MC 的 left_arm
    if ($bone -eq 'right_arm') { $bone = 'left_arm' } elseif ($bone -eq 'left_arm') { $bone = 'right_arm' }
    $b = $bones[$bone]
    $fx = [double]$e.from[0]; $fy = [double]$e.from[1]; $fz = [double]$e.from[2]
    $tx = [double]$e.to[0];   $ty = [double]$e.to[1];   $tz = [double]$e.to[2]
    $lw = ($tx - $fx) * $b.SXZ
    $lh = ($ty - $fy) * $b.SY
    $ld = ($tz - $fz) * $b.SXZ
    $lx = ($fx - $b.XCenter) * $b.SXZ + $b.XAt
    $lz = ($fz - 8.0) * $b.SXZ
    $ly = $b.YAt - ($ty - $b.YRef) * $b.SY      # 顶面（y 向下 ⇒ 用 toY 对齐 ✓）
    $boxes += [pscustomobject]@{
        Index = $i; Bone = $bone; Slot = $info.Slot
        X = $lx; Y = $ly; Z = $lz; W = $lw; H = $lh; D = $ld
        LW = (2 * $lw + 2 * $ld); LH = ($lh + $ld)
        U = 0; V = 0
    }
}

# ---------- UV 装箱（带碰撞检测 ✓） ----------
# ⚠ 不能用"最下面的不透明像素"判断空闲 ✗ —— 帽子的"细带"区域只画了 1~2 像素高的细条 ✓
#   但整块区域（v=73~99 ✓）是被**保留**的 ✗ ⇒ 必须把这些区域显式登记为"已用" ✓。
$atlas = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($atlasPath))
$used = New-Object 'bool[,]' 128, 128
# ① 底图上任何不透明像素都算已用（多留 1 像素边距 ✓）
for ($y = 0; $y -lt 128; $y++) {
    for ($x = 0; $x -lt 128; $x++) {
        if ($atlas.GetPixel($x, $y).A -gt 0) {
            for ($dy = -1; $dy -le 1; $dy++) {
                for ($dx = -1; $dx -le 1; $dx++) {
                    $nx = $x + $dx; $ny = $y + $dy
                    if ($nx -ge 0 -and $ny -ge 0 -and $nx -lt 128 -and $ny -lt 128) { $used[$nx, $ny] = $true }
                }
            }
        }
    }
}
# ② 帽子那 13 个方块 + 护腿/靴子占位区的**整块区域**一律保留 ✓（哪怕只画了细条 ✓）
$reserved = @(
    @(0, 0, 80, 21), @(62, 37, 17, 5), @(80, 37, 7, 4), @(0, 22, 42, 14), @(0, 37, 32, 12),
    @(33, 37, 17, 9), @(51, 37, 10, 6), @(23, 50, 6, 2), @(88, 37, 4, 2), @(30, 50, 22, 2),
    @(0, 50, 22, 2), @(43, 22, 22, 12), @(66, 22, 22, 12),
    @(96, 36, 32, 14), @(96, 52, 32, 11)     # 护腿 / 靴子占位（还没重做 ⇒ 保留 ✓）
)
foreach ($r in $reserved) {
    for ($yy = $r[1]; $yy -lt ($r[1] + $r[3]); $yy++) {
        for ($xx = $r[0]; $xx -lt ($r[0] + $r[2]); $xx++) {
            if ($xx -ge 0 -and $yy -ge 0 -and $xx -lt 128 -and $yy -lt 128) { $used[$xx, $yy] = $true }
        }
    }
}
function Test-Free([int]$px, [int]$py, [int]$pw, [int]$ph) {
    if ($px -lt 0 -or $py -lt 0 -or ($px + $pw) -gt 128 -or ($py + $ph) -gt 128) { return $false }
    for ($yy = $py; $yy -lt ($py + $ph); $yy++) {
        for ($xx = $px; $xx -lt ($px + $pw); $xx++) { if ($used[$xx, $yy]) { return $false } }
    }
    return $true
}
function Mark-Used([int]$px, [int]$py, [int]$pw, [int]$ph) {
    for ($yy = $py; $yy -lt ($py + $ph); $yy++) {
        for ($xx = $px; $xx -lt ($px + $pw); $xx++) { $used[$xx, $yy] = $true }
    }
}

$ordered = $boxes | Sort-Object -Property @{Expression = { [Math]::Ceiling($_.LH) }} -Descending
foreach ($c in $ordered) {
    $bw = [int][Math]::Ceiling($c.LW) + 1
    $bh = [int][Math]::Ceiling($c.LH) + 1
    $placed = $false
    for ($yy = 0; $yy -le (128 - $bh) -and -not $placed; $yy++) {
        for ($xx = 0; $xx -le (128 - $bw); $xx++) {
            if (Test-Free $xx $yy $bw $bh) {
                $c.U = $xx; $c.V = $yy
                Mark-Used $xx $yy $bw $bh
                $placed = $true
                break
            }
        }
    }
    if (-not $placed) { throw "底图装不下：方块 #$($c.Index) 需要 $bw x $bh 的空位" }
}
$freeCount = 0
for ($y = 0; $y -lt 128; $y++) { for ($x = 0; $x -lt 128; $x++) { if (-not $used[$x, $y]) { $freeCount++ } } }
Write-Host "装箱完成：11 个方块已避开所有已用区域 ✓（剩余空闲 $freeCount 像素 ✓）"

# ---------- 画默认明暗（顶 249 / 底 160 / 东 179 / 北 205 / 西 215 / 南 197 ✓） ----------
if (-not $NoPaint) {
    $root2 = Split-Path -Parent $PSScriptRoot
    $grey = Join-Path $root2 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard\grey.png'
    $srcImg = [System.Drawing.Image]::FromFile($grey)
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
        FillRect ($u + $d) $v $w ($v + $d) 249
        FillRect ($u + $d + $w) $v ($u + $d + $w + $w) ($v + $d) 160
        FillRect $u ($v + $d) ($u + $d) ($v + $d + $h) 179
        FillRect ($u + $d) ($v + $d) ($u + $d + $w) ($v + $d + $h) 205
        FillRect ($u + $d + $w) ($v + $d) ($u + $d + $w + $d) ($v + $d + $h) 215
        FillRect ($u + $d + $w + $d) ($v + $d) ($u + 2 * $d + 2 * $w) ($v + $d + $h) 197
    }
    $img.Save($grey, [System.Drawing.Imaging.ImageFormat]::Png)
    $img.Dispose()
    # 同步到另外 4 张底图
    foreach ($t in @('hat.png', 'robe.png', 'mage_leggings.png', 'mage_boots.png')) {
        Copy-Item -LiteralPath $grey -Destination (Join-Path (Split-Path $grey) $t) -Force
    }
    Write-Host "已画入底图并同步 5 张 ✓"
}
$atlas.Dispose()

# ---------- 打印 Java ----------
Write-Host ""
Write-Host ("{0,-22} {1,-10} {2,7} {3,7} {4,7} {5,7} {6,7} {7,7} {8,5} {9,5}" -f '名字', '骨骼/槽', 'x', 'y', 'z', 'w', 'h', 'd', 'u', 'v')
foreach ($c in ($boxes | Sort-Object Index)) {
    $name = "{0}_{1}_{2}" -f $c.Bone, $c.Slot, $c.Index
    Write-Host ("{0,-22} {1,-10} {2,7:N2} {3,7:N2} {4,7:N2} {5,7:N2} {6,7:N2} {7,7:N2} {8,5} {9,5}" -f `
        $name, "$($c.Bone)/$($c.Slot)", $c.X, $c.Y, $c.Z, $c.W, $c.H, $c.D, $c.U, $c.V)
    Write-Host ("        addBox({0}, `"{1}`", {2}F, {3}F, {4}F, {5}F, {6}F, {7}F, {8}, {9});" -f `
        $c.Bone, $name, [Math]::Round($c.X, 5), [Math]::Round($c.Y, 5), [Math]::Round($c.Z, 5), `
        [Math]::Round($c.W, 5), [Math]::Round($c.H, 5), [Math]::Round($c.D, 5), $c.U, $c.V)
}
