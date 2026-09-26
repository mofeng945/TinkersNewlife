# Generate the ANIMATED gate texture for "The Great White Space" portal (section 663).
#
#   textures/block/white_space_portal_gate.png        16 x 256  = 8 frames of 16 x 32
#   textures/block/white_space_portal_gate.png.mcmeta  animation metadata
#
# Why 16 x 32 per frame: the portal is a 1-wide x 2-tall gate. Each of the two blocks
# renders half of ONE frame, so the two halves join into a single continuous oval swirl
# (see models/block/white_space_portal_lower.json / _upper.json).
#
# Why this animates without any Java: vanilla's AnimationMetadataSection.
# calculateFrameSize() returns FrameSize(frameWidth, frameHeight) when BOTH are given,
# and SpriteContents.createAnimatedTexture() then does
#     cols = imageWidth / frameWidth,  rows = imageHeight / frameHeight
# -> 16/16 = 1, 256/32 = 8  => 8 frames.  Field names verified in the decompiled
# AnimationMetadataSectionSerializer: frametime / width / height / interpolate / frames.
#
# This script ONLY writes the two NEW files above. It does not read or touch any other
# texture (the older white_space_portal.png is left exactly as it is).
#
# ASCII-only on purpose: the local shell is PowerShell 5.1, which reads a BOM-less .ps1
# as ANSI and mangles non-ASCII source.

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$blockDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\block'
New-Item -ItemType Directory -Force -Path $blockDir | Out-Null

$FRAMES = 8
$FW = 16
$FH = 32

function Col([int]$a, [int]$r, [int]$g, [int]$b) {
    if ($r -lt 0) { $r = 0 } elseif ($r -gt 255) { $r = 255 }
    if ($g -lt 0) { $g = 0 } elseif ($g -gt 255) { $g = 255 }
    if ($b -lt 0) { $b = 0 } elseif ($b -gt 255) { $b = 255 }
    if ($a -lt 0) { $a = 0 } elseif ($a -gt 255) { $a = 255 }
    return [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}

# deterministic value noise in [0,1)
function Noise([int]$x, [int]$y, [int]$seed) {
    [long]$h = ([long]$x * 374761393L) + ([long]$y * 668265263L) + ([long]$seed * 1442695041L)
    $h = $h -band 0x7fffffffL
    $h = $h -bxor ($h -shr 13)
    $h = ($h * 1274126177L) -band 0x7fffffffL
    $h = $h -bxor ($h -shr 16)
    return (($h -band 0xffffL)) / 65536.0
}

$bmp = New-Object System.Drawing.Bitmap($FW, ($FH * $FRAMES), [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

for ($f = 0; $f -lt $FRAMES; $f++) {
    $phase = 2.0 * [Math]::PI * $f / $FRAMES
    for ($y = 0; $y -lt $FH; $y++) {
        for ($x = 0; $x -lt $FW; $x++) {
            $nx = ($x - 7.5) / 7.5
            $ny = ($y - 15.5) / 15.0
            $d = [Math]::Sqrt($nx * $nx + $ny * $ny)
            if ($d -gt 1.0) {
                $bmp.SetPixel($x, ($f * $FH + $y), (Col 0 0 0 0))
                continue
            }
            $th = [Math]::Atan2($ny, $nx)
            $arm = [Math]::Sin(2.0 * $th + 4.5 * $d - $phase)
            $h = [Math]::Max(0.0, $arm)
            $h = $h * $h                                   # two bright spiral arms
            $core = [Math]::Max(0.0, 1.0 - $d * 1.7)       # bright centre
            $rimd = $d - 0.86
            $rim = [Math]::Exp(-($rimd * $rimd) / 0.0035)  # glowing ellipse rim
            $b = 0.26 + 0.42 * $h + 0.55 * $core + 0.80 * $rim
            if ($b -gt 1.0) { $b = 1.0 }
            $n = Noise $x $y (50 + $f)
            $r = [int](196 + 59 * $b - 4 * $n)
            $g = [int](228 + 27 * $b - 3 * $n)
            $bl = 255
            $edge = 1.0 - $d * $d                          # fade to nothing at the rim
            $a = [int](255 * $edge * (0.40 + 0.60 * $b))
            $bmp.SetPixel($x, ($f * $FH + $y), (Col $a $r $g $bl))
        }
    }
}

$dst = Join-Path $blockDir 'white_space_portal_gate.png'
$bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Host ("  wrote {0} ({1} bytes)  {2}x{3} = {4} frames of {5}x{6}" -f `
    $dst.Replace($root + '\', ''), (Get-Item $dst).Length, $FW, ($FH * $FRAMES), $FRAMES, $FW, $FH)

# preview strip: all 8 frames side by side, 8x nearest-neighbour
$src = [System.Drawing.Bitmap]::new($dst)
$scale = 8
$out = New-Object System.Drawing.Bitmap(($FRAMES * ($FW * $scale + 4) + 4), ($FH * $scale + 8), [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($out)
$g.Clear([System.Drawing.Color]::FromArgb(255, 40, 40, 55))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
for ($f = 0; $f -lt $FRAMES; $f++) {
    $rect = New-Object System.Drawing.Rectangle(0, ($f * $FH), $FW, $FH)
    $dest = New-Object System.Drawing.Rectangle((4 + $f * ($FW * $scale + 4)), 4, ($FW * $scale), ($FH * $scale))
    $g.DrawImage($src, $dest, $rect, [System.Drawing.GraphicsUnit]::Pixel)
}
$g.Dispose(); $src.Dispose()
$preview = Join-Path $root 'build\ws-gate-preview.png'
$out.Save($preview, [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose()
Write-Host ("  preview -> {0}" -f $preview.Replace($root + '\', ''))
