# tools/make-conscience-shades.ps1
#
# Regenerates the 11 alignment shades of the "conscience" (heart) item icon.
#
# WHY: the item icon must change color with the player's alignment (same rule as the
# HUD overlay): alignment 0 => base texture as-is (grey), +50% => white, -50% => near black.
# Vanilla item tinting (ItemColor + tintindex) is a MULTIPLY, so it can only darken -
# it can never reach "white" from a grey base. Instead we ship 11 pre-shaded copies of the
# base texture and let a vanilla model "overrides" predicate pick the right one per stack
# (exactly how the compass/clock swap models). The predicate value comes from the item NBT
# mirror `tn_alignment` (0..100, 50 = neutral) - see ConscienceHandler / ConscienceItem.
#
# RE-RUN THIS whenever you repaint the base texture:
#     assets/tinkersnewlife/textures/item/conscience.png
# It overwrites the 11 generated shades + the 11 shade models + models/item/conscience.json.
# It NEVER touches the base texture itself (read-only) and no other model/texture.
#
# Usage:  powershell -ExecutionPolicy Bypass -File tools\make-conscience-shades.ps1

param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

Add-Type -AssemblyName System.Drawing

$texDir   = Join-Path $Root 'src\main\resources\assets\tinkersnewlife\textures\item'
$modelDir = Join-Path $Root 'src\main\resources\assets\tinkersnewlife\models\item'
$basePng  = Join-Path $texDir 'conscience.png'
$utf8     = New-Object System.Text.UTF8Encoding($false)

if (-not (Test-Path $basePng)) { throw "base texture not found: $basePng" }

$base = New-Object System.Drawing.Bitmap -ArgumentList $basePng
$w = $base.Width
$h = $base.Height

# average luma of the non-transparent pixels = the "neutral" anchor (alignment 0)
$sum = 0.0
$cnt = 0
for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $p = $base.GetPixel($x, $y)
        if ($p.A -lt 8) { continue }
        $sum += (0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B)
        $cnt++
    }
}
$L = if ($cnt -gt 0) { $sum / $cnt / 255.0 } else { 0.62 }
$darkLuma  = 0.08
$whiteLuma = 1.0

Write-Host ("base: {0}x{1}, average luma = {2:N3}" -f $w, $h, $L)

for ($i = 0; $i -le 10; $i++) {
    if ($i -eq 5)      { $target = $L }
    elseif ($i -lt 5)  { $target = $L + ($darkLuma  - $L) * ((5 - $i) / 5.0) }
    else               { $target = $L + ($whiteLuma - $L) * (($i - 5) / 5.0) }
    $k = if ($L -gt 0.001) { $target / $L } else { 1.0 }

    $out = New-Object System.Drawing.Bitmap -ArgumentList $w, $h
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $p = $base.GetPixel($x, $y)
            $r = [Math]::Min(255, [Math]::Round($p.R * $k))
            $g = [Math]::Min(255, [Math]::Round($p.G * $k))
            $b = [Math]::Min(255, [Math]::Round($p.B * $k))
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, $r, $g, $b))
        }
    }
    $png = Join-Path $texDir ("conscience_shade_{0}.png" -f $i)
    $out.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()

    $modelJson = @{
        parent   = 'minecraft:item/generated'
        textures = @{ layer0 = ("tinkersnewlife:item/conscience_shade_{0}" -f $i) }
    } | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText((Join-Path $modelDir ("conscience_shade_{0}.json" -f $i)), $modelJson, $utf8)

    Write-Host ("  shade {0,2}: target luma {1:N3}  (x{2:N3})" -f $i, $target, $k)
}

# base model: default = shade 0 (darkest) and one override per remaining shade.
# The predicate returns mirror/100 (0..1) => 0.5 lands on shade 5 = the untouched base.
$overrides = @()
for ($i = 1; $i -le 10; $i++) {
    $thr = if ($i -eq 10) { 0.95 } else { $i / 10.0 }
    $overrides += @{
        predicate = @{ 'tinkersnewlife:conscience' = $thr }
        model     = ("tinkersnewlife:item/conscience_shade_{0}" -f $i)
    }
}
$baseModel = @{
    parent    = 'minecraft:item/generated'
    textures  = @{ layer0 = 'tinkersnewlife:item/conscience' }
    overrides = $overrides
} | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText((Join-Path $modelDir 'conscience.json'), $baseModel, $utf8)

$base.Dispose()
Write-Host "done: 11 shades + 11 shade models + models/item/conscience.json rewritten"
