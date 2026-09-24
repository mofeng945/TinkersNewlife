# 星象仪（planetarium）月相占位贴图生成器
# ⚠ 铁律：只写**全新路径** textures/item/planetarium/moon_<0..7>.png，绝不覆盖任何既有贴图
#    （用户之后可以直接在这 8 个路径上手绘替换 ✓）
# 月相顺序 = 我的世界 getMoonPhase()：0 满月 / 1 亏凸月 / 2 下弦月 / 3 残月 / 4 新月 / 5 娥眉月 / 6 上弦月 / 7 盈凸月
param([switch]$Force)

Add-Type -AssemblyName System.Drawing

$outDir = Join-Path $PSScriptRoot '..\src\main\resources\assets\tinkersnewlife\textures\item\planetarium'
$outDir = [System.IO.Path]::GetFullPath($outDir)
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$W = 16; $H = 16
# 调色板（原版风格：深紫夜空 + 冷白月光 + 暗面）
function Col($r, $g, $b, $a) { return [System.Drawing.Color]::FromArgb($a, $r, $g, $b) }
$SKY     = Col 24 20 44 235      # 夜空底
$SKY2    = Col 38 32 66 235      # 夜空高光格
$MOON_ON = Col 236 238 224 255   # 受光面
$MOON_MID= Col 176 182 176 255   # 受光面边缘
$MOON_OFF= Col 54 56 76 255      # 暗面（仍可见轮廓）
$RIM     = Col 120 126 140 255   # 月盘描边
$STAR    = Col 200 200 235 235

# 预计算：月盘（半径 5.6，圆心 7.5,7.5）+ 每格的亮度档
$luma = @{}
for ($y = 0; $y -lt $H; $y++) {
  for ($x = 0; $x -lt $W; $x++) {
    $dx = ($x + 0.5) - 7.5; $dy = ($y + 0.5) - 7.5
    $dist = [Math]::Sqrt($dx * $dx + $dy * $dy)
    $a = [Math]::Abs($dx) + [Math]::Abs($dy) * 0.85
    if ($dist -ge 5.6 -or $a -ge 6.0) { continue }          # 不在月盘内
    $luma["$x,$y"] = @{ dx = $dx; oa = $a; dist = $dist }
  }
}

# 明亮度阶梯（按 dx 给 3 档，做出球面感）
function MoonColor($dx, $lit) {
  if (-not $lit) { return $MOON_OFF }
  if ($dx -lt -2.6) { return $MOON_MID }
  return $MOON_ON
}

$phaseNames = @('满月', '亏凸月', '下弦月', '残月', '新月', '娥眉月', '上弦月', '盈凸月')
$written = 0
for ($phase = 0; $phase -lt 8; $phase++) {
  $phi = 2.0 * [Math]::PI * $phase / 8.0
  $cos = [Math]::Cos($phi)
  $out = Join-Path $outDir ("moon_{0}.png" -f $phase)
  if ((Test-Path $out) -and (-not $Force)) { Write-Host ("SKIP (已存在，加 -Force 才覆盖) " + $out); continue }

  $bmp = New-Object System.Drawing.Bitmap($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  # 夜空底 + 棋盘微差（避免纯平）
  for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
      $c = if ((($x + $y) % 4) -eq 0) { $SKY2 } else { $SKY }
      $bmp.SetPixel($x, $y, $c)
    }
  }
  # 星点（固定位置，按 phase 错开一点点，避免 8 张完全一样）
  $stars = @(@(1, 3), @(14, 2), @(2, 13), @(13, 12), @(8, 1), @(1, 8), @(15, 7), @(7, 14))
  for ($i = 0; $i -lt $stars.Count; $i++) {
    if ((($i + $phase) % 3) -eq 0) { continue }
    $sx = $stars[$i][0]; $sy = $stars[$i][1]
    if ($luma.ContainsKey("$sx,$sy")) { continue }
    $bmp.SetPixel($sx, $sy, $STAR)
  }
  # 月盘
  foreach ($k in $luma.Keys) {
    $p = $luma[$k]
    $parts = $k -split ','
    $x = [int]$parts[0]; $y = [int]$parts[1]
    $dx = $p.dx; $oa = $p.oa; $dist = $p.dist
    # 受光判定：真实月相几何
    $inner = $dx * $cos
    $outer = $dx * $dx + $dx * $cos + 0.25
    if ($inner -ge 0) {
      $lit = ($dx -ge $cos)
    } else {
      $lit = (($oa * $oa) -le $outer)
    }
    if ($dist -ge 5.15) { $lit = $false }        # 靠近描边 ⇒ 压暗，让轮廓清晰
    $c = MoonColor $dx $lit
    $bmp.SetPixel($x, $y, $c)
  }
  # 月盘描边（半径 5.15~5.6 的一圈亮边，只在"受光侧"亮）
  foreach ($k in $luma.Keys) {
    $p = $luma[$k]
    $parts = $k -split ','
    $x = [int]$parts[0]; $y = [int]$parts[1]
    if ($p.dist -lt 4.9) { continue }
    $dx = $p.dx
    $inner = $dx * $cos
    $outer = $dx * $dx + $dx * $cos + 0.25
    $lit = if ($inner -ge 0) { ($dx -ge $cos) } else { (($p.oa * $p.oa) -le $outer) }
    if ($lit) { $bmp.SetPixel($x, $y, $RIM) }
  }
  $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  Write-Host ("写出 moon_$phase.png  (" + $phaseNames[$phase] + ")")
  $written++
}
Write-Host ("完成：写出 $written 张 ⇒ " + $outDir)
Write-Host "⚠ 这 8 张是**占位图**，你可以直接在同样路径手绘替换（不用改任何 JSON）"
