# Generate the three NEW placeholder textures for "The Great White Space" (section 660).
#
#   textures/block/white_space_block.png   16x16  near-white floor block
#   textures/block/white_space_portal.png  16x16  pale glowing gate (translucent)
#   textures/item/dimension_pass.png       16x16  ticket/card item
#
# These are brand-new files. NO existing hand-drawn texture is read or overwritten.
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
#  2) white_space_portal : pale gate, translucent, bright core
# ============================================================
$bmp = New-Bmp 16 16
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $edge = ($x -eq 0 -or $y -eq 0 -or $x -eq 15 -or $y -eq 15)
        if ($edge) {
            $bmp.SetPixel($x, $y, (Col 255 127 212 255))
            continue
        }
        $dx = $x - 7.5
        $dy = $y - 7.5
        $d = [Math]::Sqrt($dx * $dx + $dy * $dy) / 10.6
        $t = Clamp01 (1.0 - $d)
        $n = Noise $x $y 23
        $r = 255 - [int][Math]::Round(46.0 * (1.0 - $t)) - [int][Math]::Round($n * 6.0)
        $g = 255 - [int][Math]::Round(16.0 * (1.0 - $t)) - [int][Math]::Round($n * 5.0)
        $b = 255
        $a = 205 + [int][Math]::Round(50.0 * $t)
        $bmp.SetPixel($x, $y, (Col $a $r $g $b))
    }
}
Save-Png $bmp 'white_space_portal.png'

# ============================================================
#  3) dimension_pass : a card/ticket with a small gate glyph
# ============================================================
$script:outDir = $itemDir
$bmp = New-Bmp 16 16
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $bmp.SetPixel($x, $y, (Col 0 0 0 0))
    }
}
# card body: x 2..13, y 1..14
for ($y = 1; $y -le 14; $y++) {
    for ($x = 2; $x -le 13; $x++) {
        $edge = ($x -eq 2 -or $x -eq 13 -or $y -eq 1 -or $y -eq 14)
        if ($edge) {
            $bmp.SetPixel($x, $y, (Col 255 92 134 184))
        } else {
            $n = Noise $x $y 41
            $bmp.SetPixel($x, $y, (Col 255 (16 + [int][Math]::Round($n * 8.0)) (28 + [int][Math]::Round($n * 8.0)) (52 + [int][Math]::Round($n * 10.0))))
        }
    }
}
# gate glyph: x 5..10, y 4..11
for ($y = 4; $y -le 11; $y++) {
    for ($x = 5; $x -le 10; $x++) {
        $edge = ($x -eq 5 -or $x -eq 10 -or $y -eq 4 -or $y -eq 11)
        if ($edge) {
            $bmp.SetPixel($x, $y, (Col 255 234 246 255))
        } else {
            $v = 232 - [int][Math]::Round(($y - 4) * 5.0)
            $bmp.SetPixel($x, $y, (Col 255 143 $v 255))
        }
    }
}
# a warm spark in the top-right corner, and a punched hole on the left edge
$bmp.SetPixel(11, 3, (Col 255 255 217 138))
$bmp.SetPixel(11, 12, (Col 255 255 217 138))
$bmp.SetPixel(3, 7, (Col 255 22 35 58))
$bmp.SetPixel(3, 8, (Col 255 22 35 58))
Save-Png $bmp 'dimension_pass.png'

Write-Host 'done: 3 textures generated (all new files)'
