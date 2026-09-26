# Generate the placeholder texture for "The Great White Space" floor block (section 660).
#
#   textures/block/white_space_block.png   16x16  near-white floor block
#
# SECTION 665: this script used to also emit two more files; both are gone on purpose:
#   * textures/block/white_space_portal.png -- the old solid-cube gate texture; the gate is
#     now a 2-tall animated plane (tools/gen-white-space-gate.ps1) and the old file was
#     deleted at the user's request.
#   * textures/item/dimension_pass.png      -- the USER REDREW this one by hand. Running a
#     generator over it would destroy the user's own art (AGENTS.md section 1 forbids that
#     outright), so it must never be generated again.
#
# Everything is painted procedurally with System.Drawing, so the script is fully
# self-contained (it does not need a vanilla jar to borrow a template from).
#
# NOTE: this file is deliberately ASCII-only. The local shell is PowerShell 5.1,
# which reads a BOM-less .ps1 as ANSI and mangles non-ASCII source.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen-white-space-art.ps1

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$blockDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\block'
$itemDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
New-Item -ItemType Directory -Force -Path $blockDir | Out-Null
New-Item -ItemType Directory -Force -Path $itemDir | Out-Null

function New-Bmp([int]$w, [int]$h) {
    return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Col([int]$a, [int]$r, [int]$g, [int]$b) {
    if ($r -lt 0) { $r = 0 } elseif ($r -gt 255) { $r = 255 }
    if ($g -lt 0) { $g = 0 } elseif ($g -gt 255) { $g = 255 }
    if ($b -lt 0) { $b = 0 } elseif ($b -gt 255) { $b = 255 }
    return [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}

function Clamp01([double]$v) {
    if ($v -lt 0) { return 0.0 } elseif ($v -gt 1) { return 1.0 }
    return $v
}

# deterministic value noise in [0,1) -- keeps regenerating byte-identical output
function Noise([int]$x, [int]$y, [int]$seed) {
    [long]$h = ([long]$x * 374761393L) + ([long]$y * 668265263L) + ([long]$seed * 1442695041L)
    $h = $h -band 0x7fffffffL
    $h = $h -bxor ($h -shr 13)
    $h = ($h * 1274126177L) -band 0x7fffffffL
    $h = $h -bxor ($h -shr 16)
    return (($h -band 0xffffL)) / 65536.0
}

function Save-Png($bmp, [string]$name) {
    $dst = Join-Path $script:outDir $name
    $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host ("  wrote {0} ({1} bytes)" -f $dst.Replace($root + '\', ''), (Get-Item $dst).Length)
}

# ============================================================
#  1) white_space_block : a sea of white, but the grid stays readable
# ============================================================
$script:outDir = $blockDir
$bmp = New-Bmp 16 16
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = Noise $x $y 7
        $v = 255 - [int][Math]::Round($n * 6.0)      # 249..255 fine grain
        $v = $v - [int][Math]::Round($y * 0.35)      # very slight vertical shade
        $r = $v
        $g = $v
        $b = $v + 2                                  # a hair of cool blue
        # faint 1px inner border -- without it a full screen of white is unreadable
        if ($x -eq 0 -or $y -eq 0 -or $x -eq 15 -or $y -eq 15) {
            $r = $r - 12; $g = $g - 9; $b = $b - 3
        }
        # a few brighter specks so it is not a dead flat colour
        if ((Noise $x $y 31) -gt 0.94) { $r = 255; $g = 255; $b = 255 }
        $bmp.SetPixel($x, $y, (Col 255 $r $g $b))
    }
}
Save-Png $bmp 'white_space_block.png'

# ============================================================
#  REMOVED (section 665) -- do NOT add these back:
#
#  * white_space_portal.png  -- the old solid-cube gate texture. The gate is now a
#    2-block-tall animated plane (textures/block/white_space_portal_gate.png, built by
#    tools/gen-white-space-gate.ps1) and the old file was deleted on the user's request.
#  * dimension_pass.png      -- the user REDREW this item texture by hand (committed in
#    section 665). Generating it here would silently overwrite the user's own art, which
#    is the one thing AGENTS.md section 1 forbids outright. Never regenerate it.
# ============================================================

Write-Host 'done: white_space_block.png regenerated (portal + pass textures are NOT touched)'
