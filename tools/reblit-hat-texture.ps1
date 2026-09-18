# 把用户在 Blockbench 里手绘的「法帽」贴图**按现有矩形原地重贴**（不重新装箱 ✓）
#
# 为什么需要它：法帽导入器 tools\import-hat-blockbench.ps1 里那张"面 → 矩形"表写错了 ✗
#   （写成 [up][down] + [east][north][west][south] ✗；MC 源码里是 [down][up] + [west][north][east][south] ✓）
#   ⇒ 法帽每个方块的**东西面、上下面整体互换**了 ✗（与法袍同一个 bug ✓ 见备忘录 §364/§365 ✓）。
#   ⚠ 不能直接重跑那个导入器 ✗ —— 它 `-Apply` 时会**清掉 x<96 的整片底图再重画** ✗✗（line 187 起 ✓）
#     那样会把用户刚画好的法袍全擦掉 ✗。所以这里只做"原地重贴"：矩形位置、Java 都不用动 ✓。
#
# 输入：桌面 wizard_head.json + wizard_head.png（用户第二次导出的帽子 ✓）
#       src\main\java\...\WizardArmorModel.java 里 HAT_ADD_BOX 段的 13 条 addBox（含 u/v ✓）
# 输出：grey.png（+ 同步 4 张 ✓）、build\hat-reblit-report.txt、build\hat-reblit-preview-x6.png
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_head.json",
    [string]$Texture = "$env:USERPROFILE\Desktop\wizard_head.png",
    [double]$UvScale = 0,        # 0 = 自动推定（按"画上去的像素落在面矩形内的比例"取最优 ✓）
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard'
$greyPath = Join-Path $texDir 'grey.png'
$javaPath = Join-Path $root 'src\main\java\com\mofengbaizhi\tinkersnewlife\client\model\WizardArmorModel.java'
$outDir = Join-Path $root 'build'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

# ---------- ① 我们模型里法帽的 13 个方块（名字 / 尺寸 / texOffs） ----------
$javaLines = [System.IO.File]::ReadAllLines($javaPath, [System.Text.Encoding]::UTF8)
$scale = 0.8
foreach ($l in $javaLines) {
    $m = [regex]::Match($l, 'HAT_SCALE\s*=\s*([\d.]+)F')
    if ($m.Success) { $scale = [double]$m.Groups[1].Value }
}
$hats = @{}
$rx = 'addBox\(\w+, "([^"]+)",\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*(\d+),\s*(\d+)\)'
foreach ($l in $javaLines) {
    $m = [regex]::Match($l, $rx)
    if ($m.Success) {
        $hats[$m.Groups[1].Value] = @{
            W = ([double]$m.Groups[5].Value - [double]$m.Groups[2].Value) * $scale
            H = ([double]$m.Groups[6].Value - [double]$m.Groups[3].Value) * $scale
            D = ([double]$m.Groups[7].Value - [double]$m.Groups[4].Value) * $scale
            U = [int]$m.Groups[8].Value; V = [int]$m.Groups[9].Value
        }
    }
}
Write-Host ("模型里法帽方块 {0} 个（HAT_SCALE={1} ✓）" -f $hats.Count, $scale)

# ---------- ② 用户的帽子模型：索引 → 组名（Java 里名字是 <组名>_<索引> ✓） ----------
$j = [System.IO.File]::ReadAllText($Json, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$groupOf = @{}
function Walk-G($node, [string]$inherited) {
    $name = if ($node.name) { $node.name } else { $inherited }
    foreach ($c in $node.children) {
        if ($c -is [int] -or $c -is [long]) { $groupOf[[int]$c] = $name } else { Walk-G $c $name }
    }
}
foreach ($g in $j.groups) { Walk-G $g $null }
for ($i = 0; $i -lt $j.elements.Count; $i++) { if (-not $groupOf.ContainsKey($i)) { $groupOf[$i] = $null } }

# ---------- ③ 面矩形（**照抄 MC 源码** ✓ 与 import-robe-texture.ps1 同一套 ✓） ----------
function FaceRect($box, [string]$face) {
    $u = [double]$box.U; $v = [double]$box.V; $w = [double]$box.W; $h = [double]$box.H; $d = [double]$box.D
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

$src = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($Texture))
$bytes0 = [System.IO.File]::ReadAllBytes($greyPath)
$ms0 = New-Object System.IO.MemoryStream
$ms0.Write($bytes0, 0, $bytes0.Length); $ms0.Position = 0
$loaded = New-Object System.Drawing.Bitmap $ms0
$dst = New-Object System.Drawing.Bitmap $loaded.Width, $loaded.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$gTmp = [System.Drawing.Graphics]::FromImage($dst); $gTmp.DrawImageUnscaled($loaded, 0, 0); $gTmp.Dispose()
$loaded.Dispose(); $ms0.Dispose()

# ---------- ④ 自动推定 uv→像素 缩放（覆盖率最大者 ✓） ----------
$faces = @()
foreach ($e in $j.elements) {
    foreach ($fn in 'down', 'up', 'west', 'north', 'east', 'south') {
        $fd = $e.faces.$fn
        if ($fd -and $fd.uv) { $faces += , @([double]$fd.uv[0], [double]$fd.uv[1], [double]$fd.uv[2], [double]$fd.uv[3]) }
    }
}
$painted = 0
for ($y = 0; $y -lt $src.Height; $y++) { for ($x = 0; $x -lt $src.Width; $x++) { if ($src.GetPixel($x, $y).A -gt 0) { $painted++ } } }
$bestK = 1.0; $bestC = -1.0
foreach ($kk in 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 16.0) {
    if ($UvScale -gt 0) { $bestK = $UvScale; break }
    $mask = New-Object 'bool[,]' $src.Width, $src.Height
    foreach ($f in $faces) {
        $x0 = [Math]::Max(0, [Math]::Floor([Math]::Min($f[0], $f[2]) * $kk)); $x1 = [Math]::Min($src.Width, [Math]::Ceiling([Math]::Max($f[0], $f[2]) * $kk))
        $y0 = [Math]::Max(0, [Math]::Floor([Math]::Min($f[1], $f[3]) * $kk)); $y1 = [Math]::Min($src.Height, [Math]::Ceiling([Math]::Max($f[1], $f[3]) * $kk))
        for ($y = $y0; $y -lt $y1; $y++) { for ($x = $x0; $x -lt $x1; $x++) { $mask[$x, $y] = $true } }
    }
    $in = 0
    for ($y = 0; $y -lt $src.Height; $y++) { for ($x = 0; $x -lt $src.Width; $x++) { if ($mask[$x, $y] -and $src.GetPixel($x, $y).A -gt 0) { $in++ } } }
    $c = if ($painted -gt 0) { $in / [double]$painted } else { 0 }
    Write-Host ("  uv x {0,-4}: 画上去的像素落在面矩形内的比例 {1:N3}" -f $kk, $c)
    if ($c -gt $bestC) { $bestC = $c; $bestK = $kk }
}
Write-Host ("⇒ 采用 uv x {0}（覆盖率 {1:N3} ✓）" -f $bestK, $bestC)

# ---------- ⑤ 逐面原地重贴 ----------
$report = @(); $gone = 0
for ($i = 0; $i -lt $j.elements.Count; $i++) {
    $g = $groupOf[$i]
    if (-not $g) { continue }
    # ⚠ 用户在 Blockbench 里的组名是 `lance`（`lace` 的笔误 ✓），Java 生成出来的却是 `lace_*` ✓
    #   ⇒ 必须跟着改，否则 6 个系带方块全被跳过 ✗（实测：lance_7~12 "模型里没有" ✓）
    if ($g -eq 'lance') { $g = 'lace' }
    $name = "{0}_{1}" -f $g, $i
    if (-not $hats.ContainsKey($name)) { Write-Host "  ⚠ 模型里没有 $name，跳过"; continue }
    $box = $hats[$name]
    $line = "  #{0,-2} {1,-14}" -f $i, $name
    foreach ($fn in 'down', 'up', 'west', 'north', 'east', 'south') {
        $fd = $j.elements[$i].faces.$fn
        if (-not $fd -or -not $fd.uv) { $line += "  $fn:无UV"; continue }
        $uv = $fd.uv
        $sx0 = [Math]::Min([double]$uv[0], [double]$uv[2]) * $bestK; $sx1 = [Math]::Max([double]$uv[0], [double]$uv[2]) * $bestK
        $sy0 = [Math]::Min([double]$uv[1], [double]$uv[3]) * $bestK; $sy1 = [Math]::Max([double]$uv[1], [double]$uv[3]) * $bestK
        $flipX = ([double]$uv[0] -gt [double]$uv[2]); $flipY = ([double]$uv[1] -gt [double]$uv[3])
        $rot = 0; if ($fd.rotation) { $rot = [int]$fd.rotation }
        $fr = FaceRect $box $fn
        $dx0 = $fr[0]; $dy0 = $fr[1]; $dw = $fr[2]; $dh = $fr[3]
        $n = 0
        for ($py = [Math]::Floor($dy0); $py -lt [Math]::Ceiling($dy0 + $dh); $py++) {
            for ($px = [Math]::Floor($dx0); $px -lt [Math]::Ceiling($dx0 + $dw); $px++) {
                $tu = (($px + 0.5) - $dx0) / $dw; $tv = (($py + 0.5) - $dy0) / $dh
                # ⚠ 夹住而不是跳过 ✗→✓：帽檐那种**薄面**（高 0.2 格 ⇒ 目标不足 1 像素 ✓）会一个像素都写不到 ✗
                #   ⇒ 旧内容（错位的）就留在底图上 ✗（实测 maille_0 四个侧面 n=0 ✓）。夹到 [0,1] 保证"碰到的像素都写" ✓
                $tu = [Math]::Max(0.0, [Math]::Min(1.0, $tu)); $tv = [Math]::Max(0.0, [Math]::Min(1.0, $tv))
                $su = $tu; $sv = $tv
                if ($flipX) { $su = 1 - $su }
                if ($flipY) { $sv = 1 - $sv }
                switch ($rot) {
                    90 { $t = $su; $su = $sv; $sv = 1 - $t }
                    180 { $su = 1 - $su; $sv = 1 - $sv }
                    270 { $t = $su; $su = 1 - $sv; $sv = $t }
                }
                $pxs = [Math]::Floor($sx0 + $su * ($sx1 - $sx0)); $pys = [Math]::Floor($sy0 + $sv * ($sy1 - $sy0))
                $pxs = [Math]::Max([Math]::Floor($sx0), [Math]::Min([Math]::Ceiling($sx1) - 1, $pxs))
                $pys = [Math]::Max([Math]::Floor($sy0), [Math]::Min([Math]::Ceiling($sy1) - 1, $pys))
                $dst.SetPixel([int]$px, [int]$py, $src.GetPixel([int]$pxs, [int]$pys))
                $n++
            }
        }
        $line += ("  {0}:{1}" -f $fn, $n)
    }
    $report += $line
}
$src.Dispose()
$report | ForEach-Object { Write-Host $_ }
$reportPath = Join-Path $outDir 'hat-reblit-report.txt'
[System.IO.File]::WriteAllLines($reportPath, $report, (New-Object Text.UTF8Encoding($false)))

$zoom = 6
$big = New-Object System.Drawing.Bitmap (128 * $zoom), (128 * $zoom)
$gg = [System.Drawing.Graphics]::FromImage($big)
$gg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$gg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$gg.DrawImage($dst, 0, 0, 128 * $zoom, 128 * $zoom); $gg.Dispose()
$preview = Join-Path $outDir 'hat-reblit-preview-x6.png'
$big.Save($preview, [System.Drawing.Imaging.ImageFormat]::Png); $big.Dispose()

if ($Apply) {
    $tmpOut = Join-Path $outDir 'hat-atlas-new.png'
    $dst.Save($tmpOut, [System.Drawing.Imaging.ImageFormat]::Png)
    $bytes = [System.IO.File]::ReadAllBytes($tmpOut)
    foreach ($t in @('grey.png', 'hat.png', 'robe.png', 'mage_leggings.png', 'mage_boots.png')) {
        [System.IO.File]::WriteAllBytes((Join-Path $texDir $t), $bytes)
    }
    Write-Host "已原地重贴帽子的 6 个面并同步 5 张 ✓（矩形没动 ✓ Java 没动 ✓）"
} else {
    Write-Host "（演练模式 ✓ 加 -Apply 才写入 ✓）"
}
$dst.Dispose()
Write-Host "报告：$reportPath"
Write-Host "预览：$preview"
