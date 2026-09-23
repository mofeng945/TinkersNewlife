# 巫师靴子换算（用户 2026-09-19 第五份模型 model.json ✓ 顶层组 boot → body/lace/trim ✓）
#   ⚠ 用户要求："到中段太长了，适当缩放" ✓ ⇒ 纵向 SY=0.6（4.69 → 2.81 格 = 脚踝高 ✓）
#      横向 SXZ=0.93（4.5 → 4.19 ✓ 仍罩得住 4 宽的脚 ✓ 鞋尖保留 ✓）
#   锚点：他们的 y=0 = 地面 ⇒ 我们腿局部 y=12（= 世界 y 24 = 脚底 ✓）
#   一双一样 ⇒ 只做右脚，左脚由导入的 -SymPairs 反射生成（各自独立 UV 槽 ✓）
param(
    [string]$Json = "$env:USERPROFILE\Desktop\model.json",
    [double]$SXZ = 0.93,
    [double]$SY = 0.60,
    [switch]$NoPaint
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
if (-not (Test-Path -LiteralPath $Json)) { throw "找不到 $Json ✗" }
$atlasPath = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard\grey.png'
$j = [System.IO.File]::ReadAllText((Resolve-Path $Json), [System.Text.Encoding]::UTF8) | ConvertFrom-Json
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
$boxes = @()
foreach ($p in ($picked | Sort-Object Index)) {
    $e = $j.elements[$p.Index]
    $fx = [double]$e.from[0]; $fy = [double]$e.from[1]; $fz = [double]$e.from[2]
    $tx = [double]$e.to[0];   $ty = [double]$e.to[1];   $tz = [double]$e.to[2]
    $w = ($tx - $fx) * $SXZ; $h = ($ty - $fy) * $SY; $d = ($tz - $fz) * $SXZ
    $x = ($fx - 8.0) * $SXZ                     # 用户模型以 x=8 为中线 ✓ ⇒ 局部 x 居中到腿骨 ✓
    $z = ($fz - 8.0) * $SXZ                     # z 同理；他们鞋尖在 -z（= 我们的正面 ✓）
    $y = 12.0 - $ty * $SY                       # 顶面对齐（y 向下 ✓）；ty 越大越靠上
    $boxes += [pscustomobject]@{ Index = $p.Index; Slot = $p.Slot; Bone = 'right_leg'
        X = $x; Y = $y; Z = $z; W = $w; H = $h; D = $d; U = 0; V = 0
        LW = (2 * $w + 2 * $d); LH = ($h + $d) }
}
Write-Host ("靴子换算：SXZ={0} SY={1}  方块 {2} 个" -f $SXZ, $SY, $boxes.Count)
foreach ($b in ($boxes | Sort-Object Index)) {
    $w0 = -1.9 + $b.X; $w1 = $w0 + $b.W
    Write-Host ("  #{0} {1,-8} 世界 x[{2:N2}..{3:N2}] z[{4:N2}..{5:N2}] y[{6:N2}..{7:N2}]（脚底=24 ✓）" -f `
        $b.Index, $b.Slot, $w0, $w1, $b.Z, ($b.Z + $b.D), (12 + $b.Y), (12 + $b.Y + $b.H))
}
# ---- UV 装箱（避开已用像素 ✓）----
$atlas = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($atlasPath))
$used = New-Object 'bool[,]' 128, 128
for ($y = 0; $y -lt 128; $y++) { for ($x = 0; $x -lt 128; $x++) {
    if ($atlas.GetPixel($x, $y).A -gt 0) { for ($dy = -1; $dy -le 1; $dy++) { for ($dx = -1; $dx -le 1; $dx++) {
        $nx = $x + $dx; $ny = $y + $dy
        if ($nx -ge 0 -and $ny -ge 0 -and $nx -lt 128 -and $ny -lt 128) { $used[$nx, $ny] = $true } } } } } }
function Test-Free([int]$px, [int]$py, [int]$pw, [int]$ph) {
    if ($px -lt 0 -or $py -lt 0 -or ($px + $pw) -gt 128 -or ($py + $ph) -gt 128) { return $false }
    for ($yy = $py; $yy -lt ($py + $ph); $yy++) { for ($xx = $px; $xx -lt ($px + $pw); $xx++) { if ($used[$xx, $yy]) { return $false } } }
    return $true }
function Mark-Used([int]$px, [int]$py, [int]$pw, [int]$ph) {
    for ($yy = $py; $yy -lt ($py + $ph); $yy++) { for ($xx = $px; $xx -lt ($px + $pw); $xx++) { $used[$xx, $yy] = $true } } }
foreach ($c in ($boxes | Sort-Object -Property @{Expression = { [Math]::Ceiling($_.LH) }} -Descending)) {
    $bw = [int][Math]::Ceiling($c.LW) + 1; $bh = [int][Math]::Ceiling($c.LH) + 1; $placed = $false
    for ($yy = 0; $yy -le (128 - $bh) -and -not $placed; $yy++) { for ($xx = 0; $xx -le (128 - $bw); $xx++) {
        if (Test-Free $xx $yy $bw $bh) { $c.U = $xx; $c.V = $yy; Mark-Used $xx $yy $bw $bh; $placed = $true; break } } }
    if (-not $placed) { throw "底图装不下 #$($c.Index) ✗" } }
$free = 0; for ($y = 0; $y -lt 128; $y++) { for ($x = 0; $x -lt 128; $x++) { if (-not $used[$x, $y]) { $free++ } } }
Write-Host "装箱完成 ✓ 剩余空闲 $free 像素"
if (-not $NoPaint) {
    $srcImg = [System.Drawing.Image]::FromFile($atlasPath); $img = New-Object System.Drawing.Bitmap $srcImg; $srcImg.Dispose()
    function FillRect($px0, $py0, $px1, $py1, $val) {
        for ($yy = [Math]::Floor($py0); $yy -lt [Math]::Ceiling($py1); $yy++) { for ($xx = [Math]::Floor($px0); $xx -lt [Math]::Ceiling($px1); $xx++) {
            if ($xx -lt 0 -or $yy -lt 0 -or $xx -ge 128 -or $yy -ge 128) { continue }
            $img.SetPixel($xx, $yy, [System.Drawing.Color]::FromArgb(255, $val, $val, $val)) } } }
    foreach ($c in $boxes) { $u = $c.U; $v = $c.V; $w = $c.W; $h = $c.H; $d = $c.D
        FillRect ($u + $d) $v ($u + $d + $w) ($v + $d) 249
        FillRect ($u + $d + $w) $v ($u + $d + $w + $w) ($v + $d) 160
        FillRect $u ($v + $d) ($u + $d) ($v + $d + $h) 215
        FillRect ($u + $d) ($v + $d) ($u + $d + $w) ($v + $d + $h) 205
        FillRect ($u + $d + $w) ($v + $d) ($u + $d + $w + $d) ($v + $d + $h) 179
        FillRect ($u + $d + $w + $d) ($v + $d) ($u + 2 * $d + 2 * $w) ($v + $d + $h) 197 }
    $img.Save($atlasPath, [System.Drawing.Imaging.ImageFormat]::Png); $img.Dispose()
    $texDir = Split-Path $atlasPath
    foreach ($t in @('hat.png', 'robe.png', 'mage_leggings.png', 'mage_boots.png')) { Copy-Item -LiteralPath $atlasPath -Destination (Join-Path $texDir $t) -Force }
    Write-Host "已画入底图并同步 5 张 ✓" }
$atlas.Dispose()
Write-Host ""
foreach ($c in ($boxes | Sort-Object Index)) {
    Write-Host ("addLocalBox(legRPart, ""right_leg_{0}_{1}"", {2}F, {3}F, {4}F, {5}F, {6}F, {7}F, {8}, {9});" -f `
        $c.Slot, $c.Index, [Math]::Round($c.X,5), [Math]::Round($c.Y,5), [Math]::Round($c.Z,5), `
        [Math]::Round($c.W,5), [Math]::Round($c.H,5), [Math]::Round($c.D,5), $c.U, $c.V)
}