# 把「Blockbench 里画好的法帽」转成模组能用的东西（一键）
#
# 输入：
#   -Json      Blockbench 导出的 Java Block/Item 模型（wizard_head.json ✓）
#   -Texture   你在 Blockbench 里画的那张贴图 PNG ✓
#   -Apply     直接落地：改写 grey.png + 同步 4 张底图 + 替换 WizardArmorModel.java 里两段生成区 ✓
#              （不带 -Apply 时只做"演练"：输出到 build\ 并打印将要写入的内容 ✓）
#
# 它做什么：
#   1. 解析模型：每个方块的大小 → 按 HAT_SCALE 换算成 Minecraft 实体模型的尺寸 ✓；
#   2. 按标准盒式 UV 布局，给每个方块**自动分配**一块不重叠的底图区域（texOffs ✓）——只占 x<96 那半边，
#      右边 x≥96 是法袍/袖子/护腿/靴子的区域，原样保留 ✓；
#   3. 把你画的每个面（Blockbench 的 per-face UV ✓）**重采样**贴进对应面（顶/底/左右/前后 ✓）；
#   4. 生成 Java：命名 `<组>_<序号>`（组只认 plating / maille / lace，`lance` 视为 `lace` ✓），
#      并把两段生成区（addBox 列表 + 三个 ModelPart 数组）替换进 WizardArmorModel.java ✓；
#   5. 打印饱和度提示（彩色贴图会和材料色相乘 ⇒ 想保留"按材料换色"请画灰度 ✓）。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\import-hat-blockbench.ps1 `
#       -Json C:\Users\ASUS\Desktop\wizard_head.json -Texture C:\Users\ASUS\Desktop\my_hat.png -Apply

param(
    [Parameter(Mandatory = $true)][string]$Json,
    [Parameter(Mandatory = $true)][string]$Texture,
    [switch]$Apply,
    [double]$ScaleOverride = 0,   # 0 = 从 WizardArmorModel.java 读 HAT_SCALE
    [double]$UvScale = 0,         # 0 = 自动推定（UV 空间 → 贴图像素 的缩放 ✓）
    [double]$DarkRatio = 0,       # >0 时：某面亮度 < 同方块亮面均值 × 该比例 ⇒ 平涂成亮面色（治"小岛落在深色底上" ✓）
    [switch]$FillHoles           # 默认关 ✓：补"透明缝"会把用户故意留的透明（孔洞）也填掉 ✗
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$javaPath = Join-Path $root 'src\main\java\com\mofengbaizhi\tinkersnewlife\client\model\WizardArmorModel.java'
$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard'
$greyPath = Join-Path $texDir 'grey.png'
$buildDir = Join-Path $root 'build'
if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir | Out-Null }

# ---------- 读 Java 里的常量 ----------
$javaSrc = [System.IO.File]::ReadAllText($javaPath, [System.Text.Encoding]::UTF8)
function Get-Const([string]$name, [double]$fallback) {
    $m = [regex]::Match($javaSrc, "$name\s*=\s*(-?[\d.]+)F")
    if ($m.Success) { return [double]$m.Groups[1].Value }
    return $fallback
}
$S = if ($ScaleOverride -gt 0) { $ScaleOverride } else { Get-Const 'HAT_SCALE' 0.8 }
$BASE = Get-Const 'HAT_BASE_Y' 11.04688
$ORIGIN = Get-Const 'HAT_ORIGIN_XZ' 8.0
$SINK = Get-Const 'HAT_SINK' 3.0
Write-Host "常量：HAT_SCALE=$S  HAT_BASE_Y=$BASE  HAT_ORIGIN_XZ=$ORIGIN  HAT_SINK=$SINK"

# ---------- 读模型 JSON ----------
$j = [System.IO.File]::ReadAllText((Resolve-Path $Json), [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$canvasW = 64; $canvasH = 64
if ($j.texture_size) { $canvasW = [int]$j.texture_size[0]; $canvasH = [int]$j.texture_size[1] }
Write-Host "模型：$((Resolve-Path $Json).Path)  画布 ${canvasW}x${canvasH}  方块数 $($j.elements.Count)"

# 组 → 方块索引（组名只认 plating / maille / lace；lance 视为 lace）
$groupOf = @{}
function Walk-Group($node, [string]$inherited) {
    $name = if ($node.name) { $node.name } else { $inherited }
    $key = switch ($name) {
        'plating' { 'plating' }
        'maille'  { 'maille' }
        'lace'    { 'lace' }
        'lance'   { 'lace' }
        default   { $null }
    }
    if ($key) { $inherited = $key }
    foreach ($ch in $node.children) {
        if ($ch -is [int] -or $ch -is [long]) {
            if (-not $inherited) { throw "方块 $ch 不在 plating/maille/lace 任何组里（组名必须是这三个）" }
            $groupOf[[int]$ch] = $inherited
        } else {
            Walk-Group $ch $inherited
        }
    }
}
foreach ($g in $j.groups) { Walk-Group $g $null }
# Dummy（未分组）的方块：Blockbench 会把"不在任何组"的方块放在 groups 的 "null" 之外，这里兜底
for ($i = 0; $i -lt $j.elements.Count; $i++) { if (-not $groupOf.ContainsKey($i)) { $groupOf[$i] = $null } }

# ---------- 每个方块：换算尺寸、检查旋转 ----------
$cubes = @()
for ($i = 0; $i -lt $j.elements.Count; $i++) {
    $e = $j.elements[$i]
    $rot = 0.0
    if ($e.rotation) { $rot = [double]$e.rotation.angle }
    $fx = [double]$e.from[0]; $fy = [double]$e.from[1]; $fz = [double]$e.from[2]
    $tx = [double]$e.to[0];   $ty = [double]$e.to[1];   $tz = [double]$e.to[2]
    $w = ($tx - $fx) * $S
    $h = ($ty - $fy) * $S
    $d = ($tz - $fz) * $S
    $cubes += [pscustomobject]@{
        Index = $i; Group = $groupOf[$i]; Rotation = $rot
        FromX = $fx; FromY = $fy; FromZ = $fz; ToX = $tx; ToY = $ty; ToZ = $tz
        W = $w; H = $h; D = $d
        LW = (2 * $w + 2 * $d); LH = ($h + $d)
        Faces = $e.faces
        U = 0; V = 0
    }
}
$rotated = $cubes | Where-Object { [Math]::Abs($_.Rotation) -gt 0.001 }
if ($rotated) {
    Write-Host ""
    Write-Host "⚠ 有 $($rotated.Count) 个方块带旋转，本脚本不支持（当前模型也没有旋转）——请把它们在 Blockbench 里应用到网格/清零："
    $rotated | ForEach-Object { Write-Host ("    #{0} 角度={1}" -f $_.Index, $_.Rotation) }
    exit 1
}
if (-not $j.elements[0].faces.north.uv) { Write-Host "⚠ 模型里没有 per-face UV，无法重排贴图"; exit 1 }

# ---------- 装箱：把每个方块的布局塞进 x<96 的半边（右边留给袍/袖/腿/靴） ----------
$LIMIT_X = 96
$ordered = $cubes | Sort-Object -Property @{Expression = { [Math]::Ceiling($_.LH) }} -Descending
$x = 0.0; $y = 0.0; $rowH = 0.0
foreach ($c in $ordered) {
    $bw = [Math]::Ceiling($c.LW) + 1   # +1 留 1 像素间隔，避免取整后重叠
    $bh = [Math]::Ceiling($c.LH) + 1
    if (($x + $bw) -gt $LIMIT_X) { $x = 0.0; $y += $rowH; $rowH = 0.0 }
    if (($y + $bh) -gt 128) { throw "装不下：底图 128 行不够（x<96 区域）。建议减少方块或改大画布方案" }
    $c.U = [int][Math]::Round($x); $c.V = [int][Math]::Round($y)
    $x += $bw
    if ($bh -gt $rowH) { $rowH = $bh }
}

# ---------- 采样你画的贴图 ----------
$srcImg = [System.Drawing.Image]::FromFile((Resolve-Path $Texture))
$paint = New-Object System.Drawing.Bitmap $srcImg
$srcImg.Dispose()
if ($paint.Width -ne $canvasW -or $paint.Height -ne $canvasH) {
    Write-Host "⚠ 贴图尺寸 $($paint.Width)x$($paint.Height) 与模型里登记的画布 ${canvasW}x${canvasH} 不一致（按下面的自动换算处理 ✓）"
}

# ---------- 自动对齐 UV 空间 ↔ 贴图像素 ----------
# Blockbench 导出的 uv 不一定就是"贴图像素"（可能落在 0~16 之类的空间里 ✗）。
# 做法：算"整个模型的 UV 包围盒" 与 "贴图里非透明内容的包围盒"，两者对齐求缩放/偏移 ✓。
$uvMinX = [double]::MaxValue; $uvMinY = [double]::MaxValue; $uvMaxX = [double]::MinValue; $uvMaxY = [double]::MinValue
foreach ($c in $cubes) {
    foreach ($f in 'up', 'down', 'east', 'north', 'west', 'south') {
        $face = $c.Faces.$f
        if (-not $face -or -not $face.uv) { continue }
        $uvMinX = [Math]::Min($uvMinX, [double]$face.uv[0]); $uvMinY = [Math]::Min($uvMinY, [double]$face.uv[1])
        $uvMaxX = [Math]::Max($uvMaxX, [double]$face.uv[2]); $uvMaxY = [Math]::Max($uvMaxY, [double]$face.uv[3])
    }
}
$uvW = $uvMaxX - $uvMinX; $uvH = $uvMaxY - $uvMinY
# 贴图内容包围盒
$cx0 = $paint.Width; $cy0 = $paint.Height; $cx1 = -1; $cy1 = -1
for ($yy = 0; $yy -lt $paint.Height; $yy++) {
    for ($xx = 0; $xx -lt $paint.Width; $xx++) {
        if ($paint.GetPixel($xx, $yy).A -eq 0) { continue }
        if ($xx -lt $cx0) { $cx0 = $xx }; if ($xx -gt $cx1) { $cx1 = $xx }
        if ($yy -lt $cy0) { $cy0 = $yy }; if ($yy -gt $cy1) { $cy1 = $yy }
    }
}
if ($cx1 -lt 0) { throw "贴图整张都是透明的：没画东西 ✗" }
$contentW = $cx1 - $cx0 + 1; $contentH = $cy1 - $cy0 + 1
$k = 1.0; $offX = 0.0; $offY = 0.0
if ($UvScale -gt 0) {
    $k = $UvScale; $offX = $cx0 - $uvMinX * $k; $offY = $cy0 - $uvMinY * $k
} elseif ($uvW -gt 0.01 -and $uvH -gt 0.01) {
    $k = [Math]::Min($contentW / $uvW, $contentH / $uvH)
    $offX = $cx0 - $uvMinX * $k; $offY = $cy0 - $uvMinY * $k
}
Write-Host ("UV 包围盒：{0:N2},{1:N2} ~ {2:N2},{3:N2}（{4:N2}x{5:N2}）  |  贴图内容：{6},{7} ~ {8},{9}（{10}x{11}）" -f `
    $uvMinX, $uvMinY, $uvMaxX, $uvMaxY, $uvW, $uvH, $cx0, $cy0, $cx1, $cy1, $contentW, $contentH)
Write-Host ("推定换算：uv × {0:N4} + ({1:N2},{2:N2})（可用 -UvScale 覆盖 ✓）" -f $k, $offX, $offY)


# 饱和度提示（彩色贴图 × 材料色 会发脏）
$sat = 0.0; $n = 0
for ($yy = 0; $yy -lt $paint.Height; $yy += 2) {
    for ($xx = 0; $xx -lt $paint.Width; $xx += 2) {
        $p = $paint.GetPixel($xx, $yy)
        if ($p.A -eq 0) { continue }
        $mx = [Math]::Max($p.R, [Math]::Max($p.G, $p.B)); $mn = [Math]::Min($p.R, [Math]::Min($p.G, $p.B))
        if ($mx -gt 0) { $sat += ($mx - $mn) / [double]$mx }
        $n++
    }
}
$avgSat = if ($n -gt 0) { $sat / $n } else { 0 }
Write-Host ("贴图平均饱和度：{0:N3}  {1}" -f $avgSat, $(if ($avgSat -gt 0.25) { '⇒ 看起来是**彩色**：会和材料色相乘发脏，除非你就是要固定配色（那样需要关掉这套的顶点着色）' } else { '⇒ 灰度 ✓ 与"按材料换色"兼容 ✓' }))

# ---------- 生成新底图（保留右边 x>=96 的袍/袖/腿/靴区域，清掉左边重画） ----------
$out = New-Object System.Drawing.Bitmap 128, 128
$g = [System.Drawing.Graphics]::FromImage($out)
$g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
# ⚠ 读老底图必须"复制进内存 + 放掉文件句柄"，否则稍后 Save 同一路径会 GDI+ 报错 ✗（同 patch 脚本的教训 ✓）
$oldSrc = [System.Drawing.Image]::FromFile($greyPath)
$old = New-Object System.Drawing.Bitmap $oldSrc
$oldSrc.Dispose()
$g.DrawImage($old, (New-Object System.Drawing.Rectangle 96, 0, 32, 128), (New-Object System.Drawing.Rectangle 96, 0, 32, 128), [System.Drawing.GraphicsUnit]::Pixel)
$old.Dispose()
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

# 面的翻转/旋转说明：
#   Blockbench 导出的 uv 是 [u1,v1,u2,v2]；**u1>u2 或 v1>v2 表示该面被镜像** ✓
#   （和 Minecraft 方块模型一个约定：uv 反着写就是翻转 ✓），另外还有 rotation 0/90/180/270 ✓。
#   所以复制时要：先按 uv 的"正矩形"取图 → 按 uv 顺序施加翻转 → 再施加 rotation ✓。
function CopyRect($srcBmp, [double]$sx, [double]$sy, [double]$sw, [double]$sh,
                  [double]$dx, [double]$dy, [double]$dw, [double]$dh,
                  [bool]$flipX = $false, [bool]$flipY = $false, [int]$rot = 0) {
    if ($sw -le 0 -or $sh -le 0 -or $dw -le 0 -or $dh -le 0) { return }
    $dr = New-Object System.Drawing.RectangleF ([single]$dx), ([single]$dy), ([single][Math]::Max(0.01, $dw)), ([single][Math]::Max(0.01, $dh))
    if (-not ($flipX -or $flipY -or $rot -ne 0)) {
        $sr = New-Object System.Drawing.RectangleF ([single]$sx), ([single]$sy), ([single][Math]::Max(0.01, $sw)), ([single][Math]::Max(0.01, $sh))
        $g.DrawImage($srcBmp, $dr, $sr, [System.Drawing.GraphicsUnit]::Pixel)
        return
    }
    # 有翻转/旋转 ⇒ 先取到临时图，摆正后再贴 ✓
    $tw = [int][Math]::Max(1, [Math]::Ceiling($sw)); $th = [int][Math]::Max(1, [Math]::Ceiling($sh))
    $tmp = New-Object System.Drawing.Bitmap $tw, $th
    $tg = [System.Drawing.Graphics]::FromImage($tmp)
    $tg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $tg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $tg.DrawImage($srcBmp,
        (New-Object System.Drawing.RectangleF 0, 0, ([single]$tw), ([single]$th)),
        (New-Object System.Drawing.RectangleF ([single]$sx), ([single]$sy), ([single][Math]::Max(0.01, $sw)), ([single][Math]::Max(0.01, $sh))),
        [System.Drawing.GraphicsUnit]::Pixel)
    $tg.Dispose()
    if ($flipX -and $flipY) { $tmp.RotateFlip([System.Drawing.RotateFlipType]::RotateNoneFlipXY) }
    elseif ($flipX) { $tmp.RotateFlip([System.Drawing.RotateFlipType]::RotateNoneFlipX) }
    elseif ($flipY) { $tmp.RotateFlip([System.Drawing.RotateFlipType]::RotateNoneFlipY) }
    switch ($rot) {
        90 { $tmp.RotateFlip([System.Drawing.RotateFlipType]::Rotate90FlipNone) }
        180 { $tmp.RotateFlip([System.Drawing.RotateFlipType]::Rotate180FlipNone) }
        270 { $tmp.RotateFlip([System.Drawing.RotateFlipType]::Rotate270FlipNone) }
    }
    $g.DrawImage($tmp, $dr)
    $tmp.Dispose()
}

$map0 = 'up', 'down', 'east', 'north', 'west', 'south'

# ---------- 逐像素最近邻搬运（替代 DrawImage ✓） ----------
# 为什么必须自己搬：① DrawImage 按整数像素取整 ⇒ 只有 1~2 像素高的小面会留下透明缝 ✗（面会"缺一块" ✗）；
#   ② 但**不能靠"补透明缝"来治** ✗ —— 用户贴图里**故意留的透明**（例如脑后垂布的孔洞 ✓）会被一起填掉 ✗。
#   ⇒ 正确做法：对**每一个会被面采样到的底图像素**，按 uv 反算回源图像素，连 alpha 一起搬 ✓
#     （透明处保持透明 ✓；取整缝也不存在了，因为每个采样像素都被写过 ✓）。
function BlitFaceNearest($srcBmp, [double]$sx, [double]$sy, [double]$sw, [double]$sh,
                         [double]$dx, [double]$dy, [double]$dw, [double]$dh,
                         [bool]$flipX, [bool]$flipY, [int]$rot) {
    if ($sw -le 0 -or $sh -le 0 -or $dw -le 0 -or $dh -le 0) { return 0 }
    $n = 0
    for ($y = [int][Math]::Floor($dy); $y -le ([int][Math]::Ceiling($dy + $dh) - 1); $y++) {
        for ($x = [int][Math]::Floor($dx); $x -le ([int][Math]::Ceiling($dx + $dw) - 1); $x++) {
            # ⚠ 写的是**被这个面碰到的所有底图像素**（不要求像素中心落在矩形内 ✓）：
            #   只有 0.2 像素高的薄面若按"中心判定"，会一个像素都轮不到 ⇒ 采样到邻居或透明 ⇒ 缺面 ✗。
            if ($x -lt 0 -or $y -lt 0 -or $x -ge $out.Width -or $y -ge $out.Height) { continue }
            $cx = $x + 0.5; $cy = $y + 0.5
            # ⚠ 必须夹到 [0, 1) 而不是 [0, 1]：夹到 1.0 会取到矩形**外面那一行/列** ✗
            #   （薄面源只有 1 行时 floor(sy + 1.0×1) = sy+1 ⇒ 取到隔壁内容 ⇒ 颜色错乱 ✗）
            $u = [Math]::Max(0.0, [Math]::Min(0.999999, ($cx - $dx) / $dw))
            $v = [Math]::Max(0.0, [Math]::Min(0.999999, ($cy - $dy) / $dh))
            # Blockbench 的面旋转 / 镜像 ⇒ 反算回源图坐标 ✓
            if ($rot -eq 90) { $t = $u; $u = $v; $v = 1 - $t }
            elseif ($rot -eq 180) { $u = 1 - $u; $v = 1 - $v }
            elseif ($rot -eq 270) { $t = $u; $u = 1 - $v; $v = $t }
            if ($flipX) { $u = 1 - $u }
            if ($flipY) { $v = 1 - $v }
            $px = [int][Math]::Floor($sx + $u * $sw)
            $py = [int][Math]::Floor($sy + $v * $sh)
            if ($px -lt 0 -or $py -lt 0 -or $px -ge $srcBmp.Width -or $py -ge $srcBmp.Height) { continue }
            $out.SetPixel($x, $y, $srcBmp.GetPixel($px, $py))
            $n++
        }
    }
    return $n
}

# ---------- 可选：过暗面自动跟随同方块的亮面（-DarkRatio，默认关 ✓） ----------
# 用途：手绘时常常只画了"看得见的大面"，那些细长/很小的 UV 岛仍留在铺的深色底上 ✓
#   ⇒ 上线后表现为"某个方块发黑 / 同一部件几块颜色对不上" ✗。
#   给一个阈值（如 0.6）：某面平均亮度 < 该方块最亮面 × 阈值 ⇒ 用"亮面的平均色"整块平涂 ✓。
function Mean-Opaque($bmp, [double]$x0, [double]$y0, [double]$x1, [double]$y1) {
    $s = 0.0; $n = 0
    for ($y = [Math]::Floor($y0); $y -lt [Math]::Ceiling($y1); $y++) {
        for ($x = [Math]::Floor($x0); $x -lt [Math]::Ceiling($x1); $x++) {
            if ($x -lt 0 -or $y -lt 0 -or $x -ge $bmp.Width -or $y -ge $bmp.Height) { continue }
            $p = $bmp.GetPixel($x, $y)
            if ($p.A -eq 0) { continue }
            $s += $p.R; $n++
        }
    }
    if ($n -eq 0) { return -1 }
    return $s / $n
}
$darkFill = @{}          # "cubeIndex.face" → 是否改成平涂
$brightMean = @{}        # cubeIndex → 亮面平均色
if ($DarkRatio -gt 0) {
    foreach ($c in $cubes) {
        $means = @{}
        foreach ($m in $map0) {
            $face = $c.Faces.($m)
            if (-not $face -or -not $face.uv) { continue }
            $uv = $face.uv
            $sx = [Math]::Min([double]$uv[0], [double]$uv[2]) * $k + $offX
            $sy = [Math]::Min([double]$uv[1], [double]$uv[3]) * $k + $offY
            $ex = [Math]::Max([double]$uv[0], [double]$uv[2]) * $k + $offX
            $ey = [Math]::Max([double]$uv[1], [double]$uv[3]) * $k + $offY
            $means[$m] = Mean-Opaque $paint $sx $sy $ex $ey
        }
        if ($means.Count -eq 0) { continue }
        $mx = ($means.Values | Measure-Object -Maximum).Maximum
        $bright = @($means.Values | Where-Object { $_ -ge ($mx * $DarkRatio) -and $_ -ge 0 })
        $bm = if ($bright.Count -gt 0) { ($bright | Measure-Object -Average).Average } else { $mx }
        $brightMean[$c.Index] = $bm
        foreach ($m in $means.Keys) {
            if ($means[$m] -ge 0 -and $means[$m] -lt ($bm * $DarkRatio)) { $darkFill["$($c.Index).$m"] = $true }
        }
    }
}

$flipCnt = 0; $rotCnt = 0; $darkCnt = 0
$faceFills = @()
$javaBoxes = @()
foreach ($c in $cubes) {
    $u = $c.U; $v = $c.V; $w = $c.W; $h = $c.H; $d = $c.D
    # 面 → (目的矩形, 源 uv 字段名)
    # ⚠ 布局照抄 MC 源码（ModelPart$Cube ✓ 2026-09-19 核对 ✓）：上排 [down][up]、中排 [west][north][east][south] ✓
    #   原先写成 [up][down] + [east]…[west] ✗ ⇒ 每个方块东西面整体互换 ✗（帽子也一样中招 ✗ 已修 ✓）
    $map = @(
        @{ f = 'down';  dx = $u + $d;           dy = $v;     dw = $w; dh = $d },
        @{ f = 'up';    dx = $u + $d + $w;      dy = $v;     dw = $w; dh = $d },
        @{ f = 'west';  dx = $u;                dy = $v + $d; dw = $d; dh = $h },
        @{ f = 'north'; dx = $u + $d;           dy = $v + $d; dw = $w; dh = $h },
        @{ f = 'east';  dx = $u + $d + $w;      dy = $v + $d; dw = $d; dh = $h },
        @{ f = 'south'; dx = $u + $d + $w + $d; dy = $v + $d; dw = $w; dh = $h }
    )
    foreach ($m in $map) {
        $face = $c.Faces.($m.f)
        if (-not $face -or -not $face.uv) { continue }
        $uv = $face.uv
        $u1 = [double]$uv[0]; $v1 = [double]$uv[1]; $u2 = [double]$uv[2]; $v2 = [double]$uv[3]
        $flipX = $u1 -gt $u2
        $flipY = $v1 -gt $v2
        $fr = 0
        if ($face.rotation) { $fr = [int]$face.rotation }
        if ($flipX -or $flipY) { $flipCnt++ }
        if ($fr -ne 0) { $rotCnt++ }
        $sx = [Math]::Min($u1, $u2) * $k + $offX
        $sy = [Math]::Min($v1, $v2) * $k + $offY
        $ex = [Math]::Max($u1, $u2) * $k + $offX
        $ey = [Math]::Max($v1, $v2) * $k + $offY
        # 过暗面 ⇒ 整块平涂成该方块的亮面平均色 ✓（不黑、且同部件各面颜色一致 ✓）
        if ($darkFill.ContainsKey("$($c.Index).$($m.f)")) {
            $vv = [int][Math]::Round($brightMean[$c.Index])
            $br = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, $vv, $vv, $vv))
            $g.FillRectangle($br, ([single]$m.dx), ([single]$m.dy), ([single][Math]::Max(0.5, $m.dw)), ([single][Math]::Max(0.5, $m.dh)))
            $br.Dispose()
            $darkCnt++
            $faceFills += [pscustomobject]@{ X0 = $m.dx; Y0 = $m.dy; X1 = ($m.dx + $m.dw); Y1 = ($m.dy + $m.dh); V = $vv }
            continue
        }
        $wrote = BlitFaceNearest $paint $sx $sy ($ex - $sx) ($ey - $sy) $m.dx $m.dy $m.dw $m.dh $flipX $flipY $fr
        if ($env:TN_HAT_DEBUG) { Write-Host ("  [dbg] #{0}.{1,-6} dest=({2},{3},{4},{5}) src=({6},{7},{8},{9}) 写入={10}" -f $c.Index, $m.f, $m.dx, $m.dy, $m.dw, $m.dh, $sx, $sy, ($ex - $sx), ($ey - $sy), $wrote) }
    }
    if ($env:TN_HAT_DEBUG) { Write-Host ("  [dbg] 循环中读回 #0.east 所在像素(5,20) = A{0} R{1}" -f $out.GetPixel(5, 20).A, $out.GetPixel(5, 20).R) }
    # Java：x/z 用 from，y 用 to（Y 轴方向与 Blockbench 相反 ✓，见模型类注释）
    $jx = [Math]::Round(($c.FromX - $ORIGIN) * $S, 5)
    $jz = [Math]::Round(($c.FromZ - $ORIGIN) * $S, 5)
    $jy = [Math]::Round(-8.0 - ($c.ToY - $BASE) * $S + $SINK, 5)
    $jw = [Math]::Round($c.W, 5); $jh = [Math]::Round($c.H, 5); $jd = [Math]::Round($c.D, 5)
    $name = "{0}_{1}" -f $c.Group, ($c.Index)
    $javaBoxes += ("        addBox(head, `"{0}`", {1}F, {2}F, {3}F, {4}F, {5}F, {6}F, {7}, {8});" -f `
        $name, $c.FromX, $c.FromY, $c.FromZ, $c.ToX, $c.ToY, $c.ToZ, $c.U, $c.V)
}
# ---------- 可选：补透明缝（默认**关** ✗ —— 会把用户故意留的透明一起填掉 ✗，正常情况不需要 ✓） ----------
$holeCnt = 0; $holeFaces = 0
if ($FillHoles) {
    foreach ($ff in $faceFills) {
        $hit = $false
        for ($y = [Math]::Floor($ff.Y0); $y -lt [Math]::Ceiling($ff.Y1); $y++) {
            for ($x = [Math]::Floor($ff.X0); $x -lt [Math]::Ceiling($ff.X1); $x++) {
                if ($x -lt 0 -or $y -lt 0 -or $x -ge $out.Width -or $y -ge $out.Height) { continue }
                if ($out.GetPixel($x, $y).A -ne 0) { continue }
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $ff.V, $ff.V, $ff.V))
                $holeCnt++; $hit = $true
            }
        }
        if ($hit) { $holeFaces++ }
    }
}
$g.Dispose()

# ---------- 汇总 ----------
Write-Host ""
Write-Host ("镜像面：{0} 个   带旋转面：{1} 个（都会按 Blockbench 的 uv 顺序 / rotation 摆正 ✓）" -f $flipCnt, $rotCnt)
if ($FillHoles) { Write-Host ("补透明缝：{0} 个像素（涉及 {1} 个面）—— ⚠ 会填掉用户故意留的透明 ✗" -f $holeCnt, $holeFaces) }
else { Write-Host "补透明缝：关 ✓（逐像素搬运已保证无缝隙，且用户故意留的透明原样保留 ✓）" }
if ($DarkRatio -gt 0) { Write-Host ("过暗面平涂：{0} 个面（-DarkRatio {1}）" -f $darkCnt, $DarkRatio) }
Write-Host ("{0,-16} {1,-8} {2,4} {3,4} {4,7} {5,7} {6,7} {7,9} {8,9}" -f '名字', '组', 'u', 'v', 'w', 'h', 'd', '布局宽', '布局高')
foreach ($c in $cubes) {
    Write-Host ("{0,-16} {1,-8} {2,4} {3,4} {4,7:N2} {5,7:N2} {6,7:N2} {7,9:N2} {8,9:N2}" -f `
        ("{0}_{1}" -f $c.Group, $c.Index), $c.Group, $c.U, $c.V, $c.W, $c.H, $c.D, $c.LW, $c.LH)
}

# ⚠ PowerShell 里逗号（数组构造）的优先级高于 + ⇒ 每个元素必须用括号包住，
#   否则三段字符串会被拼成一行（元素之间用空格连起来）✗
function New-PartArray([string]$field, [string]$group) {
    $names = ($cubes | Where-Object { $_.Group -eq $group } |
        ForEach-Object { "head.getChild(`"$($group)_$($_.Index)`")" }) -join ', '
    return "        this.$field = new ModelPart[]{ $names };"
}
$javaArrays = @(
    (New-PartArray 'hatPlating' 'plating'),
    (New-PartArray 'hatMaille' 'maille'),
    (New-PartArray 'hatLace' 'lace')
)

$boxesBlock = [string]::Join("`r`n", $javaBoxes)
$partsBlock = [string]::Join("`r`n", $javaArrays)

if ($oddRot.Count -gt 0) {
    Write-Host ""
    Write-Host "⚠ 有 $($oddRot.Count) 个面带 90/270 度 UV 旋转，本脚本没有处理（会漏掉那些面）："
    $oddRot | ForEach-Object { Write-Host "    $_" }
    Write-Host "  处理办法：在 Blockbench 里选中这些面 → UV 编辑器 → 旋转回 0（或重新 Auto UV）后重新导出 ✓"
}

if (-not $Apply) {
    $dump = Join-Path $buildDir 'hat-import-preview.java.txt'
    [System.IO.File]::WriteAllText($dump, "// addBox 列表`r`n$boxesBlock`r`n`r`n// 三个数组`r`n$partsBlock`r`n", (New-Object Text.UTF8Encoding($false)))
    $previewPng = Join-Path $buildDir 'hat-import-preview.png'
    $out.Save($previewPng, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host ""
    Write-Host "演练模式（没加 -Apply）："
    Write-Host "  底图预览：$previewPng"
    Write-Host "  Java 片段：$dump"
    $out.Dispose(); $paint.Dispose()
    exit 0
}

# ---------- 落地 ----------
$out.Save($greyPath, [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose(); $paint.Dispose()
foreach ($t in @('hat.png', 'robe.png', 'mage_leggings.png', 'mage_boots.png')) {
    Copy-Item -LiteralPath $greyPath -Destination (Join-Path $texDir $t) -Force
}

# 替换 Java 里两段"生成区"
$new = [regex]::Replace($javaSrc,
    '(?s)(// <<< HAT_ADD_BOX \(generated\) >>>).*?(// <<< /HAT_ADD_BOX >>>)',
    "`$1`r`n$boxesBlock`r`n        `$2")
if ($new -notmatch '/HAT_ADD_BOX') { throw "Java 里没找到 // <<< HAT_ADD_BOX (generated) >>> 标记" }
$new = [regex]::Replace($new,
    '(?s)(// <<< HAT_PARTS \(generated\) >>>).*?(// <<< /HAT_PARTS >>>)',
    "`$1`r`n$partsBlock`r`n        `$2")
[System.IO.File]::WriteAllText($javaPath, $new, (New-Object Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "已落地："
Write-Host "  底图  grey.png + hat/robe/mage_leggings/mage_boots 已同步 ✓"
Write-Host "  Java  WizardArmorModel.java 的 HAT_ADD_BOX / HAT_PARTS 两段已替换 ✓"
Write-Host ""
Write-Host "接着：powershell -File tools\wizard-atlas-map.ps1（核对区域）→ .\gradlew build → tools\deploy.ps1"
