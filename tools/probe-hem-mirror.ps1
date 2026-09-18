# 诊断：查"哪些面 uv 反向"以及"两半裙摆的画是否互为镜像"
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_robe.json",
    [string]$Png  = "$env:USERPROFILE\Desktop\wizard_robe.png",
    [double]$UvScale = 8.0
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$j = [System.IO.File]::ReadAllText($Json, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$img = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($Png))

Write-Host "每个方块的反向面（u1>u2 = 水平翻 / v1>v2 = 垂直翻）："
for ($i = 0; $i -lt $j.elements.Count; $i++) {
    $e = $j.elements[$i]
    $fx = @(); $fy = @()
    foreach ($fn in 'north', 'south', 'east', 'west', 'up', 'down') {
        $uv = $e.faces.$fn.uv
        if (-not $uv) { continue }
        if ([double]$uv[0] -gt [double]$uv[2]) { $fx += $fn }
        if ([double]$uv[1] -gt [double]$uv[3]) { $fy += $fn }
    }
    Write-Host ("  #{0,-2} 水平翻: {1,-34} 垂直翻: {2}" -f $i, (($fx -join ',')), (($fy -join ',')))
}

# 取某元素某面的像素块（按 min/max ✓ 不应用翻转 ✓）
function Grab($e, [string]$fn) {
    $uv = $e.faces.$fn.uv
    $x0 = [int]([Math]::Min([double]$uv[0], [double]$uv[2]) * $UvScale)
    $y0 = [int]([Math]::Min([double]$uv[1], [double]$uv[3]) * $UvScale)
    $x1 = [int]([Math]::Max([double]$uv[0], [double]$uv[2]) * $UvScale)
    $y1 = [int]([Math]::Max([double]$uv[1], [double]$uv[3]) * $UvScale)
    $w = $x1 - $x0; $h = $y1 - $y0
    if ($w -le 0 -or $h -le 0) { return $null }
    $block = New-Object 'int[,]' $w, $h
    for ($y = 0; $y -lt $h; $y++) { for ($x = 0; $x -lt $w; $x++) { $block[$x, $y] = $img.GetPixel($x0 + $x, $y0 + $y).ToArgb() } }
    return @{ W = $w; H = $h; P = $block }
}
function CmpBlock($a, $b, [bool]$mirror) {
    if (-not $a -or -not $b) { return "跳过" }
    if ($a.W -ne $b.W -or $a.H -ne $b.H) { return ("尺寸不同 {0}x{1} vs {2}x{3}" -f $a.W, $a.H, $b.W, $b.H) }
    $same = 0; $n = $a.W * $a.H
    for ($y = 0; $y -lt $a.H; $y++) {
        for ($x = 0; $x -lt $a.W; $x++) {
            $xx = if ($mirror) { $a.W - 1 - $x } else { $x }
            if ($a.P[$xx, $y] -eq $b.P[$x, $y]) { $same++ }
        }
    }
    return ("{0:P0}" -f ($same / [double]$n))
}

Write-Host ""
Write-Host "裙摆 #1 与 #2 各面对照（原样相同率 / 镜像相同率 ✓ 高者说明关系）："
foreach ($fn in 'north', 'south', 'east', 'west', 'up', 'down') {
    $a = Grab $j.elements[1] $fn
    $b = Grab $j.elements[2] $fn
    Write-Host ("  {0,-6} 原样 {1,-8} 镜像 {2}" -f $fn, (CmpBlock $a $b $false), (CmpBlock $a $b $true))
}
Write-Host ""
Write-Host "裙摆 #1 的外侧面 west 与 #2 的外侧面 east（这一对才是朝外的 ✓）对照："
$wa = Grab $j.elements[1] 'west'
$eb = Grab $j.elements[2] 'east'
Write-Host ("  #1.west vs #2.east   原样 {0,-8} 镜像 {1}" -f (CmpBlock $wa $eb $false), (CmpBlock $wa $eb $true))
$ea = Grab $j.elements[1] 'east'
$wb = Grab $j.elements[2] 'west'
Write-Host ("  #1.east vs #2.west（内侧面，应为空 ✓） 原样 {0,-8} 镜像 {1}" -f (CmpBlock $ea $wb $false), (CmpBlock $ea $wb $true))
Write-Host ""
Write-Host "躯干 #0 与内衬 #4 对照（单件，看有没有整体镜像关系）："
foreach ($fn in 'north', 'south') {
    $a = Grab $j.elements[0] $fn
    $b = Grab $j.elements[4] $fn
    Write-Host ("  {0,-6} 原样 {1,-8} 镜像 {2}" -f $fn, (CmpBlock $a $b $false), (CmpBlock $a $b $true))
}
$img.Dispose()
