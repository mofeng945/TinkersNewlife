# 诊断：Blockbench 导出的"每面 UV"到底对应贴图上的哪些像素
#
# 背景：用户在 Blockbench 里给法袍画了贴图（128x128），模型 JSON 里 faces.*.uv 是**每面** UV ✓。
# 要把画好的图搬进模组底图，必须先确认 uv → 像素 的换算关系（scale）✓。
# 本工具对 scale=1..4 各出一张叠加图（贴图放大 4 倍 + 面矩形描边 + 编号），并打印填充率统计 ✓。
param(
    [string]$Json = "$env:USERPROFILE\Desktop\wizard_robe.json",
    [string]$Png  = "$env:USERPROFILE\Desktop\wizard_robe.png"
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

$j = [System.IO.File]::ReadAllText($Json, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$src = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($Png))
Write-Host ("贴图 {0}x{1}   texture_size={2}   方块 {3}" -f $src.Width, $src.Height, ($j.texture_size -join 'x'), $j.elements.Count)

$outDir = Join-Path $root 'build'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$font = New-Object System.Drawing.Font('Consolas', 7)

foreach ($k in 3,4,5,6,7,8,9,10) {
    $zoom = 4
    $big = New-Object System.Drawing.Bitmap ($src.Width * $zoom), ($src.Height * $zoom)
    $g = [System.Drawing.Graphics]::FromImage($big)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.Clear([System.Drawing.Color]::FromArgb(255, 28, 28, 34))
    $g.DrawImage($src, 0, 0, $src.Width * $zoom, $src.Height * $zoom)

    $rates = @()
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(220, 255, 60, 60), 1)
    $br = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 120, 230, 255))
    for ($ei = 0; $ei -lt $j.elements.Count; $ei++) {
        $e = $j.elements[$ei]
        foreach ($p in $e.faces.PSObject.Properties) {
            $u = $p.Value.uv
            if (-not $u) { continue }
            $x0 = [Math]::Min($u[0], $u[2]) * $k; $x1 = [Math]::Max($u[0], $u[2]) * $k
            $y0 = [Math]::Min($u[1], $u[3]) * $k; $y1 = [Math]::Max($u[1], $u[3]) * $k
            $g.DrawRectangle($pen, [single]($x0 * $zoom), [single]($y0 * $zoom), [single](($x1 - $x0) * $zoom), [single](($y1 - $y0) * $zoom))
            $g.DrawString(("{0}{1}" -f $ei, $p.Name.Substring(0, 1)), $font, $br, [single]($x0 * $zoom + 1), [single]($y0 * $zoom + 1))
            # 填充率
            $ix0 = [Math]::Floor($x0); $ix1 = [Math]::Ceiling($x1); $iy0 = [Math]::Floor($y0); $iy1 = [Math]::Ceiling($y1)
            $area = ($ix1 - $ix0) * ($iy1 - $iy0); $hit = 0
            for ($y = $iy0; $y -lt $iy1; $y++) {
                for ($x = $ix0; $x -lt $ix1; $x++) {
                    if ($x -ge 0 -and $y -ge 0 -and $x -lt $src.Width -and $y -lt $src.Height) {
                        if ($src.GetPixel($x, $y).A -gt 0) { $hit++ }
                    }
                }
            }
            if ($area -gt 0) { $rates += ($hit / $area) }
        }
    }
    $g.Dispose()
    $big.Save((Join-Path $outDir ("probe-uv-k{0}.png" -f $k)), [System.Drawing.Imaging.ImageFormat]::Png)
    $big.Dispose()
    $avg = [Math]::Round((($rates | Measure-Object -Average).Average), 3)
    $high = ($rates | Where-Object { $_ -gt 0.5 }).Count
    Write-Host ("scale={0}: 平均填充率 {1}   填充率>0.5 的面 {2}/{3}   -> build\probe-uv-k{0}.png" -f $k, $avg, $high, $rates.Count)
}
$src.Dispose()
