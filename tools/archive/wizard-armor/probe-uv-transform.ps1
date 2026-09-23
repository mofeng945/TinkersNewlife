# 诊断 4：确定"用户贴图"与"模型每面 uv"的换算关系（尺度 + 偏移 + 是否翻转 ✓）
# 判据：用户是在 UV 岛内画的 ⇒ **画上去的像素应几乎全部落在面矩形内** ✓（覆盖率接近 1 就是对的 ✓）
#   注意不能用"矩形填充率"当判据 ✗ —— 用户的画是带透明格纹的图案，面部本来就不是实心的 ✓
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_robe.json",
    [string]$Png  = "$env:USERPROFILE\Desktop\wizard_robe.png"
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$j = [System.IO.File]::ReadAllText($Json, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$img = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($Png))
$W = $img.Width; $H = $img.Height
$painted = New-Object 'bool[,]' $W, $H
$np = 0
for ($y = 0; $y -lt $H; $y++) { for ($x = 0; $x -lt $W; $x++) { if ($img.GetPixel($x, $y).A -gt 0) { $painted[$x, $y] = $true; $np++ } } }
$img.Dispose()
Write-Host ("画上去的像素 $np")

# 收集 66 个面（uv 浮点 ✓）
$faces = @()
$uxmin = 1e9; $uxmax = -1e9; $uymin = 1e9; $uymax = -1e9
foreach ($e in $j.elements) {
    foreach ($p in $e.faces.PSObject.Properties) {
        $u = $p.Value.uv
        $x0 = [double]$u[0]; $y0 = [double]$u[1]; $x1 = [double]$u[2]; $y1 = [double]$u[3]
        $faces += , @($x0, $y0, $x1, $y1)
        foreach ($v in @($x0, $x1)) { if ($v -lt $uxmin) { $uxmin = $v }; if ($v -gt $uxmax) { $uxmax = $v } }
        foreach ($v in @($y0, $y1)) { if ($v -lt $uymin) { $uymin = $v }; if ($v -gt $uymax) { $uymax = $v } }
    }
}
Write-Host ("UV 跨度 x[{0}..{1}] y[{2}..{3}]   面数 {4}" -f $uxmin, $uxmax, $uymin, $uymax, $faces.Count)

function Coverage([double]$k, [double]$dx, [double]$dy, [bool]$flipY) {
    $inside = 0
    $m = New-Object 'bool[,]' $W, $H
    foreach ($f in $faces) {
        $x0 = [Math]::Min($f[0], $f[2]) * $k + $dx; $x1 = [Math]::Max($f[0], $f[2]) * $k + $dx
        $ya = [Math]::Min($f[1], $f[3]) * $k; $yb = [Math]::Max($f[1], $f[3]) * $k
        if ($flipY) { $y0 = $H - $yb + $dy; $y1 = $H - $ya + $dy } else { $y0 = $ya + $dy; $y1 = $yb + $dy }
        $ix0 = [Math]::Max(0, [Math]::Floor($x0)); $ix1 = [Math]::Min($W, [Math]::Ceiling($x1))
        $iy0 = [Math]::Max(0, [Math]::Floor($y0)); $iy1 = [Math]::Min($H, [Math]::Ceiling($y1))
        for ($y = $iy0; $y -lt $iy1; $y++) { for ($x = $ix0; $x -lt $ix1; $x++) { $m[$x, $y] = $true } }
    }
    for ($y = 0; $y -lt $H; $y++) { for ($x = 0; $x -lt $W; $x++) { if ($painted[$x, $y] -and $m[$x, $y]) { $inside++ } } }
    return $inside / [double]$np
}

Write-Host "== 只搜尺度（无偏移、不翻转）=="
$best = @{ k = 0; c = -1 }
foreach ($k in 6.0, 6.5, 7.0, 7.5, 7.75, 8.0, 8.25, 8.5, 9.0, 9.5, 10.0) {
    $c = Coverage $k 0 0 $false
    Write-Host ("   k={0,5:N2}  覆盖率 {1:N3}" -f $k, $c)
    if ($c -gt $best.c) { $best = @{ k = $k; c = $c } }
}
Write-Host ("== 在最优尺度附近搜偏移与翻转（k={0} ✓）==" -f $best.k)
foreach ($flip in $false, $true) {
    $bk = 0; $bd = 0; $bc = -1
    foreach ($dx in -8..8) {
        foreach ($dy in -8..8) {
            $c = Coverage $best.k $dx $dy $flip
            if ($c -gt $bc) { $bc = $c; $bk = $dx; $bd = $dy }
        }
    }
    Write-Host ("   翻转={0,-5}  最佳偏移 dx={1,3} dy={2,3}  覆盖率 {3:N3}" -f $flip, $bk, $bd, $bc)
}
