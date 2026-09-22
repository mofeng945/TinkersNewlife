# Generates the SS558 GUI panel for the EE Extractor:
#   assets/tinkersnewlife/textures/gui/ee_extractor.png   (176 x 166)
#
# ASCII ONLY (a .ps1 with non-ASCII must be UTF-8 *with BOM* or Windows PowerShell 5.1
# reads it as ANSI and the script fails to parse).
#
# SAFETY: never overwrites an existing file unless -Force is passed. This path is brand new
# (the repo's other screens draw their panels with GuiGraphics#fill instead of a texture),
# so nothing hand-painted is touched either way.
#
# Layout must match EeExtractorMenu: slot at (80,33), info line at y=64,
# player inventory rows at y=84/102/120, hotbar at y=142, image 176x166.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\GenEeExtractorGui.ps1
#   powershell -ExecutionPolicy Bypass -File tools\GenEeExtractorGui.ps1 -DryRun
param([switch]$DryRun, [switch]$Force)

Add-Type -AssemblyName System.Drawing

$W = 176
$H = 166
$out = Join-Path $PSScriptRoot "..\src\main\resources\assets\tinkersnewlife\textures\gui\ee_extractor.png"

# Vanilla-ish container palette
$C_FACE   = @(0xC6, 0xC6, 0xC6)   # panel face
$C_LIGHT  = @(0xFF, 0xFF, 0xFF)   # top/left bevel
$C_DARK   = @(0x55, 0x55, 0x55)   # bottom/right bevel
$C_SHADOW = @(0x8B, 0x8B, 0x8B)   # outer border
$C_SLOT   = @(0x37, 0x37, 0x37)   # slot floor
$C_TITLE  = @(0x40, 0x40, 0x40)   # title strip
$C_ACCENT = @(0x6D, 0x2B, 0xA8)   # the mod's crystal violet, used for a thin accent line

function New-Bitmap([int]$w, [int]$h) {
  return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function SetPx($bmp, [int]$x, [int]$y, $rgb) {
  if ($x -lt 0 -or $y -lt 0 -or $x -ge $W -or $y -ge $H) { return }
  $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2]))
}

function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $rgb) {
  for ($y = $y0; $y -lt $y1; $y++) {
    for ($x = $x0; $x -lt $x1; $x++) { SetPx $bmp $x $y $rgb }
  }
}

# A vanilla-style recessed 18x18 slot at (x,y)
function Draw-Slot($bmp, [int]$x, [int]$y) {
  Fill-Rect $bmp $x $y ($x + 16) ($y + 16) $C_SLOT
  Fill-Rect $bmp $x $y ($x + 16) ($y + 1) @(0x00, 0x00, 0x00)
  Fill-Rect $bmp $x $y ($x + 1) ($y + 16) @(0x00, 0x00, 0x00)
}

$bmp = New-Bitmap $W $H

# 1) base face
Fill-Rect $bmp 0 0 $W $H $C_FACE

# 2) outer border + bevel
Fill-Rect $bmp 0 0 $W 1 $C_LIGHT
Fill-Rect $bmp 0 0 1 $H $C_LIGHT
Fill-Rect $bmp 0 ($H - 1) $W $H $C_DARK
Fill-Rect $bmp ($W - 1) 0 $W $H $C_DARK
Fill-Rect $bmp 1 1 ($W - 1) 2 $C_LIGHT
Fill-Rect $bmp 1 1 2 ($H - 1) $C_LIGHT

# 3) title strip (rows 0..16) - dark, then a violet accent line under it
Fill-Rect $bmp 2 2 ($W - 2) 17 $C_TITLE
Fill-Rect $bmp 2 17 ($W - 2) 18 $C_ACCENT

# 4) the single extractor slot at (80,33) - slot frame occupies (79,32)-(97,50)
Draw-Slot $bmp 79 32

# 5) separator above the player inventory (like vanilla)
Fill-Rect $bmp 7 76 ($W - 7) 77 $C_DARK

# 6) player inventory 3x9 at (8,84) and hotbar at (8,142)
for ($row = 0; $row -lt 3; $row++) {
  for ($col = 0; $col -lt 9; $col++) {
    Draw-Slot $bmp (8 + $col * 18) (84 + $row * 18)
  }
}
for ($col = 0; $col -lt 9; $col++) {
  Draw-Slot $bmp (8 + $col * 18) 142
}

if ($DryRun) { "DRY  would write $W x $H panel -> $out"; $bmp.Dispose(); return }

if ((Test-Path $out) -and -not $Force) {
  "SKIP (exists, use -Force to overwrite) -> $out"
  $bmp.Dispose()
  return
}

$dir = Split-Path $out -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
"WROTE $W x $H panel -> $out"
