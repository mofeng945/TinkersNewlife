# 把用户在 Blockbench 里手绘的「法袍」贴图搬进模组底图
#
# 输入：
#   ① 桌面 wizard_robe.json —— 用户模型（11 个方块 ✓ 每面独立 uv ✓ 画布 UV 栅格 16 ⇒ uv*8 = 像素 ✓ 已实测覆盖率 1.000 ✓）
#   ② 桌面 wizard_robe.png  —— 用户画好的贴图（128x128 ✓）
#   ③ 我们的 grey.png       —— 目标底图（帽子手绘在里面 ✓ 只覆盖法袍 11 块 ✓）
# 输出：grey.png（+ 同步 hat/robe/mage_leggings/mage_boots 四张 ✓）、build\robe-texture-report.txt、build\robe-texture-preview-x6.png
#
# 映射规则（两套 UV 布局不同，必须逐面重采样 ✓）：
#   源：用户 JSON 的 faces.<面>.uv，取 min/max 得源矩形，×8 换成像素 ✓
#   目标：我们模型该方块的面矩形（MC 盒式 UV ✓ 由 w/h/d + texOffs(u,v) 算出 ✓）
#   逐目标像素反查源像素（最近邻 ✓ 连 alpha 一起搬 ⇒ 透明处保持透明 ✓）
#   ⚠ 用户的面若 uv 反向（u1>u2 / v1>v2）在 Blockbench 里是**镜像显示**的 ⇒ 搬过来要同样镜像 ✓，否则观感会左右/上下翻 ✗
#   ⚠ 用户的几何与我们的几何缩放不同（如袖子纵向 0.75×、横向 1.12×）⇒ 一定会有重采样，不是 1:1 ✗
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_robe.json",
    [string]$Png  = "$env:USERPROFILE\Desktop\wizard_robe.png",
    [double]$UvScale = 8.0,
    # 需要"按身体中线左右镜像"的方块名，逗号分隔 ✓：逐面把源的 u 反向再取样 ✓
    # 默认 = 裙摆"右半"（body_plating_1 ✓）：用户实测原方向左右反了 ✓，镜像后是他要的方向 ✓（§364 ✓）
    # ⚠ 声明成 [string] 而不是 [string[]] ✗ —— `powershell -File` 传数组参数会被塞成一整个字符串 ✗（踩过 ✓）
    [string]$MirrorX = 'body_plating_1',
    # 左右成对的方块：**由左边那块的成品反射生成右边** ✓（格式 源>派生，逗号分隔 ✓；传空串关掉 ✓）
    # 默认就是巫师法袍的四对 ✓：裙摆两半 + 两袖的镶板/系带/锁链基底 ✓（用户："缩放调整了纹理好歹给我左右对称一下" ✓）
    [string]$SymPairs = 'body_plating_1>body_plating_2,left_arm_plating_5>right_arm_plating_8,left_arm_lace_6>right_arm_lace_9,left_arm_maille_7>right_arm_maille_10',
    [switch]$Apply,
    [switch]$KeepUnpainted   # 源面整面透明时保留底图原有像素（不砸出洞 ✓）
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$texDir  = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard'
$mirrorList = @()
if ($MirrorX) { $mirrorList = @($MirrorX -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }
$derivedOf = @{}    # 派生方块 → 源方块 ✓
if ($SymPairs) {
    foreach ($pair in ($SymPairs -split ',')) {
        $pair = $pair.Trim(); if (-not $pair) { continue }
        $parts = $pair -split '>'
        if ($parts.Count -ne 2) { throw "对称对格式应为 源>派生 ✗：$pair" }
        $derivedOf[$parts[1].Trim()] = $parts[0].Trim()
    }
}
$greyPath = Join-Path $texDir 'grey.png'
$javaPath = Join-Path $root 'src\main\java\com\mofengbaizhi\tinkersnewlife\client\model\WizardArmorModel.java'
$outDir = Join-Path $root 'build'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

# ---------- ① 读用户模型：方块索引 → 六面 uv ----------
$j = [System.IO.File]::ReadAllText($Json, [System.Text.Encoding]::UTF8) | ConvertFrom-Json

# 组路径 → (骨骼, 槽)：与 tools\convert-robe-model.ps1 同一套规则 ✓
$map = @{}
function Walk($node, [string]$parentName) {
    $name = $node.name
    foreach ($c in $node.children) {
        if ($c -is [int] -or $c -is [long]) {
            $slot = switch ($name) { 'body' { 'plating' } 'trim' { 'maille' } 'lace' { 'lace' } default { $null } }
            if (-not $parentName -or -not $slot) { throw "方块 $c 的组层级无法识别（父=$parentName 名=$name）" }
            $map[[int]$c] = @{ Bone = $parentName; Slot = $slot }
        } else { Walk $c $name }
    }
}
foreach ($g in $j.groups) {
    foreach ($c in $g.children) {
        if ($c -is [int] -or $c -is [long]) { throw "顶层组 $($g.name) 直接挂了方块 ✗" }
        Walk $c $g.name
    }
}

# ---------- ② 读我们的模型：方块名 → (w,h,d,u,v) ----------
$srcLines = [System.IO.File]::ReadAllLines($javaPath, [System.Text.Encoding]::UTF8)
$ours = @{}
$rxLocal = 'addLocalBox\(\w+, "([^"]+)",\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F,\s*(\d+),\s*(\d+)\)'
foreach ($line in $srcLines) {
    $m = [regex]::Match($line, $rxLocal)
    if ($m.Success) {
        $ours[$m.Groups[1].Value] = @{
            W = [double]$m.Groups[5].Value; H = [double]$m.Groups[6].Value; D = [double]$m.Groups[7].Value
            U = [int]$m.Groups[8].Value; V = [int]$m.Groups[9].Value
        }
    }
}
Write-Host ("我们的法袍方块 {0} 个；用户模型 {1} 个" -f $ours.Count, $j.elements.Count)

# ---------- ③ 面名 ↔ MC 面矩形 ----------
# ⚠ 布局**必须照抄 MC 源码**（net/minecraft/client/model/geom/ModelPart$Cube 构造函数 ✓ 2026-09-19 实测核对 ✓）：
#   设 u0/v0 = texOffs、w = dimX、h = dimY、d = dimZ：
#     上排： [DOWN] x[u0+d,   u0+d+w]   y[v0, v0+d]        ← 注意：**先是 down（底）** ✓
#            [UP]   x[u0+d+w, u0+2w+d]  y[v0, v0+d]
#     中排： [WEST] x[u0,     u0+d]     y[v0+d, v0+d+h]    ← 注意：**第一列是 west（-x）** ✓
#            [NORTH] x[u0+d,  u0+d+w]   y[v0+d, v0+d+h]
#            [EAST] x[u0+d+w, u0+w+d]   y[v0+d, v0+d+h]    ← east 在第三列 ✓
#            [SOUTH] x[u0+w+d, u0+2w+d] y[v0+d, v0+d+h]
#   ⇒ 四列顺序是 **[west][north][east][south]** ✓（不是 east…west ✗），上排顺序是 **[down][up]** ✓。
#   我原先凭"常见说法"写成 [east][north][west][south] + [up][down] ✗ ⇒ **每个方块的东西面整体互换** ✗
#   （用户实测：裙摆"腿内侧两个面应该在外侧" ✓ —— 画在外侧面上的图被搬到了内侧面 ✓）。
function FaceRect($box, [string]$face) {
    $u = [double]$box.U; $v = [double]$box.V; $w = [double]$box.W; $h = [double]$box.H; $d = [double]$box.D
    # ⚠ PowerShell 里逗号比 + 结合更紧 ✗ ⇒ `$u + $d, $v` 会被解析成 `$u + @($d,$v)` ✗ 必须逐项加括号 ✓
    switch ($face) {
        'down'  { return @(($u + $d), $v, $w, $d) }
        'up'    { return @(($u + $d + $w), $v, $w, $d) }
        'west'  { return @($u, ($v + $d), $d, $h) }
        'north' { return @(($u + $d), ($v + $d), $w, $h) }
        'east'  { return @(($u + $d + $w), ($v + $d), $d, $h) }
        'south' { return @(($u + $d + $w + $d), ($v + $d), $w, $h) }
    }
    throw "未知面 $face"
}
# 注：MC 的 CubeListBuilder 面名与 Blockbench 一致（north=-z / south=+z / east=+x / west=-x ✓），
#     两边都用同一套面名 ⇒ 逐面直接对应 ✓ 不需要额外换向 ✓

$src = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($Png))
# ⚠ GDI+ 连踩三个坑（①②③ 全是实测 ✗）：
#   ① 从文件打开的图不能原地 Save ✗；
#   ② `New-Object Bitmap $源图` **不是**深拷贝 ✗（源 Dispose 后目标跟着废 ✗）；
#   ③ **`Image::FromFile` 的句柄在 Dispose 之后仍被 GDI+ 攥着** ✗ ⇒ 连 [IO.File]::WriteAllBytes 都报
#      "The process cannot access the file ... being used by another process" ✗（是**同一个进程自己**占的 ✗）。
#   ⇒ 根治：目标底图**完全不碰文件** —— 读成字节流、用 MemoryStream 解码 ✓（GDI+ 只读内存 ⇒ 不持有文件句柄 ✓），
#     再画进一张全新的 32bppArgb 位图 ✓；最后"存临时文件 + 按字节覆盖" ✓。
$bytes0 = [System.IO.File]::ReadAllBytes($greyPath)
$ms0 = New-Object System.IO.MemoryStream
$ms0.Write($bytes0, 0, $bytes0.Length)
$ms0.Position = 0
$loaded = New-Object System.Drawing.Bitmap $ms0
$dst = New-Object System.Drawing.Bitmap $loaded.Width, $loaded.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$gTmp = [System.Drawing.Graphics]::FromImage($dst)
$gTmp.DrawImageUnscaled($loaded, 0, 0)
$gTmp.Dispose()
$loaded.Dispose()
$ms0.Dispose()
$report = @()
$lostFaces = 0; $copiedFaces = 0; $blankFaces = @()
# 面名互换（左右反射时用 ✓）：绕身体中线翻过来 ⇒ 朝外的 west 对上另一侧朝外的 east ✓，其余面名不变 ✓
$swapFace = @{ 'west' = 'east'; 'east' = 'west'; 'north' = 'north'; 'south' = 'south'; 'up' = 'up'; 'down' = 'down' }

for ($i = 0; $i -lt $j.elements.Count; $i++) {
    $e = $j.elements[$i]
    $info = $map[$i]
    $bone = $info.Bone
    # 用户命名是镜像的：x > 8 的 right_arm 组其实是 MC 的 left_arm ✓（与换算脚本同规则 ✓）
    if ($bone -eq 'right_arm') { $bone = 'left_arm' } elseif ($bone -eq 'left_arm') { $bone = 'right_arm' }
    $name = "{0}_{1}_{2}" -f $bone, $info.Slot, $i
    if (-not $ours.ContainsKey($name)) { throw "我们模型里找不到方块 $name ✗" }
    $box = $ours[$name]
    # -MirrorX：把这个方块的整块外观按身体中线左右镜像一次 ✓（用户实测"下摆左右反了" ✓ → 见备忘录 §364 ✓）
    #   只翻 u ✓（不翻 v、不换面名 ✓）⇒ 前后面仍是前后面 ✓，朝外的侧面各自镜像 ✓ = 整块绕身体中线翻过来 ✓
    $mirrored = ($mirrorList -contains $name)
    # -SymPairs：这一块是"由另一块反射生成"的 ✓（源块必须先处理，索引天然递增 ✓）
    $symFrom = $null
    if ($derivedOf.ContainsKey($name)) { $symFrom = $derivedOf[$name] }
    if ($symFrom -and -not $ours.ContainsKey($symFrom)) { throw "对称源方块 $symFrom 不在模型里 ✗" }

    $tag = ''
    if ($mirrored) { $tag += ' [镜像]' }
    if ($symFrom) { $tag += " [对称←$symFrom]" }
    $line = "  #{0,-2} {1,-22}{2}" -f $i, $name, $tag
    foreach ($fn in 'up', 'down', 'east', 'north', 'west', 'south') {
        $fd = $e.faces.$fn
        if (-not $fd -or -not $fd.uv) { $line += "  $fn:无UV ✗"; continue }
        $uv = $fd.uv
        $sx0 = [Math]::Min([double]$uv[0], [double]$uv[2]) * $UvScale
        $sx1 = [Math]::Max([double]$uv[0], [double]$uv[2]) * $UvScale
        $sy0 = [Math]::Min([double]$uv[1], [double]$uv[3]) * $UvScale
        $sy1 = [Math]::Max([double]$uv[1], [double]$uv[3]) * $UvScale
        $flipX = ([double]$uv[0] -gt [double]$uv[2])
        $flipY = ([double]$uv[1] -gt [double]$uv[3])
        if ($mirrored) { $flipX = -not $flipX }
        $rot = 0
        if ($fd.rotation) { $rot = [int]$fd.rotation }

        $fr = FaceRect $box $fn
        $dx0 = $fr[0]; $dy0 = $fr[1]; $dw = $fr[2]; $dh = $fr[3]

        # ---------- 对称派生：直接抄"源方块已被处理好的目标像素"，横向翻一下 ✓ ----------
        # 为什么要这么做 ✗→✓：左右两块即使画得一模一样（镜像 ✓），各自对源图**重采样**时相位不同 ✗
        #   ⇒ 下降采样（用户 2 像素/格 vs 底图 1 像素/格 ✓）会取到不同的源像素 ⇒ 两侧图案对不上 ✗
        #   （用户实测："你缩放调整了纹理好歹给我左右对称一下" ✓）。抄一份再翻 ⇒ 像素级完全对称 ✓✓。
        #   左右反射的对应关系：几何上 west(-x) ↔ 另一侧的 east(+x) ✓，且每个面的 u 轴都要反 ✓
        #   （这就是"整块绕身体中线翻过来"✓ 与 -MirrorX 同一几何含义 ✓）。
        if ($symFrom) {
            $sbox = $ours[$symFrom]
            $sf = $swapFace[$fn]
            $srect = FaceRect $sbox $sf
            $rx0 = $srect[0]; $ry0 = $srect[1]; $rw = $srect[2]; $rh = $srect[3]
            $n = 0
            for ($py = [Math]::Floor($dy0); $py -lt [Math]::Ceiling($dy0 + $dh); $py++) {
                for ($px = [Math]::Floor($dx0); $px -lt [Math]::Ceiling($dx0 + $dw); $px++) {
                    $tu = (($px + 0.5) - $dx0) / $dw
                    $tv = (($py + 0.5) - $dy0) / $dh
                    $tu = [Math]::Max(0.0, [Math]::Min(1.0, $tu)); $tv = [Math]::Max(0.0, [Math]::Min(1.0, $tv))
                    $su = 1.0 - $tu                       # 横向翻 ✓
                    $sv = $tv
                    $spx = [int][Math]::Floor($rx0 + $su * $rw)
                    $spy = [int][Math]::Floor($ry0 + $sv * $rh)
                    $spx = [Math]::Max([Math]::Floor($rx0), [Math]::Min([Math]::Ceiling($rx0 + $rw) - 1, $spx))
                    $spy = [Math]::Max([Math]::Floor($ry0), [Math]::Min([Math]::Ceiling($ry0 + $rh) - 1, $spy))
                    # ⚠ 读的是 dst（源方块已经写好了 ✓），写的是本方块 ⇒ 两块矩形不相交 ⇒ 无别名问题 ✓
                    $dst.SetPixel([int]$px, [int]$py, $dst.GetPixel([int]$spx, [int]$spy))
                    $n++
                }
            }
            $copiedFaces++
            $line += ("  {0}<-{1}:{2}" -f $fn, $sf, $n)
            continue
        }

        # 逐目标像素反查源像素（最近邻 ✓ 保留 alpha ✓）
        $painted = 0; $total = 0
        $px0 = [Math]::Floor($dx0); $px1 = [Math]::Ceiling($dx0 + $dw)
        $py0 = [Math]::Floor($dy0); $py1 = [Math]::Ceiling($dy0 + $dh)
        for ($py = $py0; $py -lt $py1; $py++) {
            for ($px = $px0; $px -lt $px1; $px++) {
                $tu = (($px + 0.5) - $dx0) / $dw
                $tv = (($py + 0.5) - $dy0) / $dh
                # ⚠ 夹住而不是跳过 ✓：矩形起点是小数时，边界像素中心会落在矩形外一点点 ✗
                #   跳过的话那圈像素会留着**上一次的旧内容** ✗ ⇒ 重跑不再是"完全重写" ✗（与帽子工具同一处理 ✓）
                $tu = [Math]::Max(0.0, [Math]::Min(1.0, $tu))
                $tv = [Math]::Max(0.0, [Math]::Min(1.0, $tv))
                $total++
                $su = $tu; $sv = $tv
                if ($flipX) { $su = 1 - $su }
                if ($flipY) { $sv = 1 - $sv }
                # 旋转（Blockbench 的 rotation 是顺时针 ✓，把采样点反向转回去 ✓）
                switch ($rot) {
                    90  { $t = $su; $su = $sv; $sv = 1 - $t }
                    180 { $su = 1 - $su; $sv = 1 - $sv }
                    270 { $t = $su; $su = 1 - $sv; $sv = $t }
                }
                $px_s = [Math]::Floor($sx0 + $su * ($sx1 - $sx0))
                $py_s = [Math]::Floor($sy0 + $sv * ($sy1 - $sy0))
                if ($px_s -lt $sx0) { $px_s = [Math]::Floor($sx0) }
                if ($px_s -ge $sx1) { $px_s = [Math]::Ceiling($sx1) - 1 }
                if ($py_s -lt $sy0) { $py_s = [Math]::Floor($sy0) }
                if ($py_s -ge $sy1) { $py_s = [Math]::Ceiling($sy1) - 1 }
                $col = $src.GetPixel([int]$px_s, [int]$py_s)
                if ($col.A -gt 0) { $painted++ }
                if ($col.A -eq 0 -and $KeepUnpainted) { continue }
                $dst.SetPixel([int]$px, [int]$py, $col)
            }
        }
        $copiedFaces++
        if ($painted -eq 0) {
            $lostFaces++
            $blankFaces += ("{0}/{1}" -f $name, $fn)
        }
        $line += ("  {0}:{1}/{2}" -f $fn.Substring(0, 2), $painted, $total)
    }
    $report += $line
}
$src.Dispose()

Write-Host ""
$report | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host ("面数 {0}；源面整面透明（搬过去会变洞 ✗）的 {1} 个：{2}" -f $copiedFaces, $lostFaces, ($blankFaces -join ', '))

# ---------- ④ 报告 + 预览图 ----------
$reportPath = Join-Path $outDir 'robe-texture-report.txt'
[System.IO.File]::WriteAllLines($reportPath, $report, (New-Object Text.UTF8Encoding($false)))
$zoom = 6
$big = New-Object System.Drawing.Bitmap (128 * $zoom), (128 * $zoom)
$g = [System.Drawing.Graphics]::FromImage($big)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.DrawImage($dst, 0, 0, 128 * $zoom, 128 * $zoom)
$g.Dispose()
$preview = Join-Path $outDir 'robe-texture-preview-x6.png'
$big.Save($preview, [System.Drawing.Imaging.ImageFormat]::Png)
$big.Dispose()

if ($Apply) {
    # ⚠ 坑③：同一个位图 Save 到 build\ 成功 ✓，Save 回 grey.png 却报 "GDI+ 中发生一般性错误" ✗
    #   （grey.png 正是本次"从文件读进来"的那张 ⇒ GDI+ 还攥着它的句柄 ✗，Dispose 也不保证立刻放 ✗）
    #   ⇒ 绕开：先存到 build\ 的临时文件 ✓，再**按字节**写过去 ✓（[IO.File]::WriteAllBytes 实测可覆盖 ✓）
    $tmpOut = Join-Path $outDir 'robe-atlas-new.png'
    $dst.Save($tmpOut, [System.Drawing.Imaging.ImageFormat]::Png)
    $bytes = [System.IO.File]::ReadAllBytes($tmpOut)
    foreach ($t in @('grey.png', 'hat.png', 'robe.png', 'mage_leggings.png', 'mage_boots.png')) {
        [System.IO.File]::WriteAllBytes((Join-Path $texDir $t), $bytes)
    }
    Write-Host "已写入 grey.png 并同步 4 张 ✓（临时文件 $tmpOut ✓）"
} else {
    Write-Host "（演练模式 ✓ 加 -Apply 才写入 ✓）"
}
$dst.Dispose()
Write-Host "报告：$reportPath"
Write-Host "预览：$preview"
