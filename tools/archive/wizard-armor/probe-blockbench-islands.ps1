# 诊断 2：把贴图上的"色块岛"（连通域）抠出来，跟模型各面的尺寸/UV 数量对照
# 目的：判断用户到底按哪套 UV 布局画的（JSON 里的 uv 与画面位置对不上 ✓）
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_robe.json",
    [string]$Png  = "$env:USERPROFILE\Desktop\wizard_robe.png",
    [int]$MinArea = 6
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$j = [System.IO.File]::ReadAllText($Json, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$img = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($Png))
$W = $img.Width; $H = $img.Height

# ---- 连通域（4 邻域 ✓） ----
$op = New-Object 'bool[,]' $W, $H
for ($y = 0; $y -lt $H; $y++) { for ($x = 0; $x -lt $W; $x++) { $op[$x, $y] = ($img.GetPixel($x, $y).A -gt 0) } }
$seen = New-Object 'bool[,]' $W, $H
$islands = @()
for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
        if (-not $op[$x, $y] -or $seen[$x, $y]) { continue }
        $stack = New-Object System.Collections.Stack
        $stack.Push(@($x, $y)); $seen[$x, $y] = $true
        $n = 0; $x0 = $x; $x1 = $x; $y0 = $y; $y1 = $y
        while ($stack.Count -gt 0) {
            $p = $stack.Pop(); $px = $p[0]; $py = $p[1]; $n++
            if ($px -lt $x0) { $x0 = $px }; if ($px -gt $x1) { $x1 = $px }
            if ($py -lt $y0) { $y0 = $py }; if ($py -gt $y1) { $y1 = $py }
            foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
                $nx = $px + $d[0]; $ny = $py + $d[1]
                if ($nx -ge 0 -and $ny -ge 0 -and $nx -lt $W -and $ny -lt $H -and $op[$nx, $ny] -and -not $seen[$nx, $ny]) {
                    $seen[$nx, $ny] = $true; $stack.Push(@($nx, $ny))
                }
            }
        }
        if ($n -ge $MinArea) {
            $islands += [pscustomobject]@{ X0 = $x0; Y0 = $y0; X1 = $x1; Y1 = $y1; W = ($x1 - $x0 + 1); H = ($y1 - $y0 + 1); N = $n }
        }
    }
}
$img.Dispose()
Write-Host ("色块岛 {0} 个（面积 >= {1} ✓），按宽度降序：" -f $islands.Count, $MinArea)
$islands | Sort-Object W -Descending | Select-Object -First 30 | ForEach-Object {
    Write-Host ("  x[{0,3}..{1,3}] y[{2,3}..{3,3}]  {4,3}x{5,3}  面积{6}" -f $_.X0, $_.X1, $_.Y0, $_.Y1, $_.W, $_.H, $_.N)
}

Write-Host ""
Write-Host "模型各面的尺寸（模型单位 ✓ 由 from/to 算）与 uv 尺寸："
for ($i = 0; $i -lt $j.elements.Count; $i++) {
    $e = $j.elements[$i]
    $mw = [double]$e.to[0] - [double]$e.from[0]
    $mh = [double]$e.to[1] - [double]$e.from[1]
    $md = [double]$e.to[2] - [double]$e.from[2]
    $line = "  #{0,-2} 模型 {1,6:N2} x {2,6:N2} x {3,6:N2}   " -f $i, $mw, $mh, $md
    foreach ($fn in 'north', 'east', 'up') {
        $u = $e.faces.$fn.uv
        if ($u) {
            $uw = [Math]::Abs([double]$u[2] - [double]$u[0]); $uh = [Math]::Abs([double]$u[3] - [double]$u[1])
            $line += ("{0}({1:N3}x{2:N3}) " -f $fn.Substring(0, 1), $uw, $uh)
        }
    }
    Write-Host $line
}
