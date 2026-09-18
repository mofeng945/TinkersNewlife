# 诊断 3：用户画的贴图到底是"他模型的 UV 布局"还是"我们模组的底图布局"？
# 做法：把两种候选矩形都叠到他的贴图上，各自算"矩形内被他画的像素填充的比例" ✓ 高的那个才是真的 ✓
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_robe.json",
    [string]$Png  = "$env:USERPROFILE\Desktop\wizard_robe.png",
    [string]$Java = 'src\main\java\com\mofengbaizhi\tinkersnewlife\client\model\WizardArmorModel.java'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
$img = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($Png))
$W = $img.Width; $H = $img.Height

function FillRate([double]$x0, [double]$y0, [double]$x1, [double]$y1) {
    $ix0 = [Math]::Max(0, [Math]::Floor($x0)); $ix1 = [Math]::Min($W, [Math]::Ceiling($x1))
    $iy0 = [Math]::Max(0, [Math]::Floor($y0)); $iy1 = [Math]::Min($H, [Math]::Ceiling($y1))
    $a = ($ix1 - $ix0) * ($iy1 - $iy0); if ($a -le 0) { return @{ Rate = 0; Area = 0 } }
    $hit = 0
    for ($y = $iy0; $y -lt $iy1; $y++) { for ($x = $ix0; $x -lt $ix1; $x++) { if ($img.GetPixel($x, $y).A -gt 0) { $hit++ } } }
    return @{ Rate = $hit / $a; Area = $a }
}

# ---------- 候选 A：我们模组底图布局（从 Java 解析 ✓ 面矩形 = 整块 2d+2w x d+h ✓） ----------
$src = [System.IO.File]::ReadAllLines((Join-Path $root $Java), [System.Text.Encoding]::UTF8)
$rects = @()
$rxHat = 'addBox\(\w+, "([^"]+)",\s*[-\d.]+F,\s*[-\d.]+F,\s*[-\d.]+F,\s*[-\d.]+F,\s*[-\d.]+F,\s*[-\d.]+F,\s*(\d+),\s*(\d+)\)'
foreach ($line in $src) {
    $m = [regex]::Match($line, $rxHat)
    if ($m.Success) { $rects += @{ Name = $m.Groups[1].Value; U = [int]$m.Groups[2].Value; V = [int]$m.Groups[3].Value; Kind = 'hat' } }
}
$rxLocal = 'addLocalBox\(\w+, "([^"]+)",\s*[-\d.]+F,\s*[-\d.]+F,\s*[-\d.]+F,\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F,\s*(\d+),\s*(\d+)\)'
foreach ($line in $src) {
    $m = [regex]::Match($line, $rxLocal)
    if ($m.Success) {
        $rects += @{ Name = $m.Groups[1].Value; U = [int]$m.Groups[5].Value; V = [int]$m.Groups[6].Value
                     W = 2 * [double]$m.Groups[4].Value + 2 * [double]$m.Groups[2].Value
                     H = [double]$m.Groups[4].Value + [double]$m.Groups[3].Value; Kind = 'robe' }
    }
}
# 法帽的尺寸要按 HAT_SCALE 0.8 算：这里只需要"整块"面积，直接借用面 uv 反推更麻烦 ⇒ 用另一个正则取 from/to
$hats = @()
foreach ($line in $src) {
    $m = [regex]::Match($line, 'addBox\(\w+, "([^"]+)",\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*(\d+),\s*(\d+)\)')
    if ($m.Success) {
        $w = ([double]$m.Groups[5].Value - [double]$m.Groups[2].Value) * 0.8
        $h = ([double]$m.Groups[6].Value - [double]$m.Groups[3].Value) * 0.8
        $d = ([double]$m.Groups[7].Value - [double]$m.Groups[4].Value) * 0.8
        $hats += @{ Name = $m.Groups[1].Value; U = [int]$m.Groups[8].Value; V = [int]$m.Groups[9].Value; W = 2 * $d + 2 * $w; H = $d + $h }
    }
}
$robeR = ($rects | Where-Object { $_.Kind -eq 'robe' })
Write-Host "== 候选 A：我们模组底图布局 =="
$sumA = 0; $nA = 0
foreach ($r in $robeR) {
    $f = FillRate $r.U $r.V ($r.U + $r.W) ($r.V + $r.H)
    $sumA += $f.Rate; $nA++
    Write-Host ("   {0,-20} rect({1},{2},{3:N1},{4:N1})  填充率 {5:N2}" -f $r.Name, $r.U, $r.V, $r.W, $r.H, $f.Rate)
}
Write-Host ("   法袍 11 块平均填充率 = {0:N3}" -f ($sumA / $nA))
$sumH = 0
foreach ($r in $hats) { $sumH += (FillRate $r.U $r.V ($r.U + $r.W) ($r.V + $r.H)).Rate }
Write-Host ("   法帽 13 块平均填充率 = {0:N3}  （帽子的手绘一直在这张图上 ✓ 若高 ⇒ 说明这张图就是我们的底图 ✓）" -f ($sumH / $hats.Count))

# ---------- 候选 B：他 JSON 里的每面 uv（scale 试 1..6 ✓ 取最好） ----------
$j = [System.IO.File]::ReadAllText($Json, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
Write-Host "== 候选 B：他模型 JSON 的每面 uv =="
foreach ($k in 1, 2, 3, 4, 5, 6) {
    $sum = 0; $n = 0
    foreach ($e in $j.elements) {
        foreach ($p in $e.faces.PSObject.Properties) {
            $u = $p.Value.uv
            $f = FillRate ([Math]::Min($u[0], $u[2]) * $k) ([Math]::Min($u[1], $u[3]) * $k) ([Math]::Max($u[0], $u[2]) * $k) ([Math]::Max($u[1], $u[3]) * $k)
            $sum += $f.Rate; $n++
        }
    }
    Write-Host ("   scale={0}: 平均填充率 {1:N3}" -f $k, ($sum / $n))
}
$img.Dispose()
