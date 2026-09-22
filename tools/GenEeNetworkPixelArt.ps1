# Generates Minecraft-style 16x16 pixel art for the SS557 EE-network blocks:
#   block/ee_extractor_side.png       - the EE Extractor's metal body
#   block/ee_extractor_top.png        - its top plate (a violet crystal core)
#   block/energy_converter_side.png   - the Universal Energy Converter's casing
#   block/energy_converter_front.png  - its front face (FE output port)
#   block/energy_converter_top.png    - its top plate (a cyan energy coil)
#
# ASCII ONLY on purpose: a .ps1 with non-ASCII text must be saved as UTF-8 *with BOM*
# or Windows PowerShell 5.1 reads it as ANSI and the script fails to parse.
#
# SAFETY: this script NEVER overwrites an existing file unless -Force is passed.
# The repo rule is "hand-painted art is untouchable" - every file this script writes
# is a brand new path (the two new blocks only).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\GenEeNetworkPixelArt.ps1
#   powershell -ExecutionPolicy Bypass -File tools\GenEeNetworkPixelArt.ps1 -DryRun
param([switch]$DryRun, [switch]$Force)

Add-Type -AssemblyName System.Drawing

$blockDir = Join-Path $PSScriptRoot "..\src\main\resources\assets\tinkersnewlife\textures\block"

# ---------------------------------------------------------------- palettes
# Extractor: darkened amethyst / violet metal with a pale violet core.
$EX = @{
  '#' = @(0x1B, 0x0C, 0x24)   # outline
  'a' = @(0x33, 0x2A, 0x45)   # deep metal
  'b' = @(0x4A, 0x3E, 0x63)   # metal
  'c' = @(0x63, 0x53, 0x84)   # metal highlight
  'd' = @(0x2B, 0x0E, 0x3E)   # crystal socket
  'e' = @(0x6D, 0x2B, 0xA8)   # crystal
  'f' = @(0xB4, 0x5C, 0xF0)   # crystal light
  'g' = @(0xE8, 0xC8, 0xFF)   # glint
}

# Converter: steel / cyan machine with a turquoise coil.
$CV = @{
  '#' = @(0x0D, 0x1C, 0x22)   # outline
  'a' = @(0x2A, 0x35, 0x3B)   # deep steel
  'b' = @(0x40, 0x51, 0x59)   # steel
  'c' = @(0x5A, 0x6E, 0x78)   # steel highlight
  'd' = @(0x0E, 0x3A, 0x44)   # coil socket
  'e' = @(0x14, 0x8C, 0xA6)   # coil
  'f' = @(0x3D, 0xD6, 0xE8)   # coil light
  'g' = @(0xD8, 0xFB, 0xFF)   # glint
}

# ---------------------------------------------------------------- art
# Each entry is a 16-char string per row ('.' = transparent).
$EXTRACTOR_SIDE = @(
  "................",
  "..############..",
  "..#aabbbbaabb#..",
  "..#abccccccba#..",
  "..#abcddddcba#..",
  "..#abcdeedcba#..",
  "..#abcdeedcba#..",
  "..#abcddddcba#..",
  "..#abccccccba#..",
  "..#aabbbbaabb#..",
  "..#abbbbbbbba#..",
  "..#a#######a#...",
  "..#a#.....#a#...",
  "..#a#.....#a#...",
  "..###.....###...",
  "................"
)

$EXTRACTOR_TOP = @(
  "................",
  "..############..",
  "..#cccccccccc#..",
  "..#cbdddddbbc#..",
  "..#bddeeeeddb#..",
  "..#bdeeffeedb#..",
  "..#bdefggfedb#..",
  "..#bdefggfedb#..",
  "..#bdeeffeedb#..",
  "..#bddeeeeddb#..",
  "..#cbdddddbbc#..",
  "..#cccccccccc#..",
  "..#aaaaaaaaaa#..",
  "..############..",
  "................",
  "................"
)

$CONVERTER_SIDE = @(
  "................",
  "..#..........#..",
  "..#.########.#..",
  "..#.#aabbaa#.#..",
  "..#.#bccccb#.#..",
  "..#.#bcddcb#.#..",
  "..#.#bcggcb#.#..",
  "..#.#bcddcb#.#..",
  "..#.#bccccb#.#..",
  "..#.#aabbaa#.#..",
  "..#.########.#..",
  "..#..........#..",
  "..############..",
  "..#aabbbbbbaa#..",
  "..############..",
  "................"
)

$CONVERTER_FRONT = @(
  "................",
  "..############..",
  "..#aabbbbbbaa#..",
  "..#abccccccba#..",
  "..#abcddddcba#..",
  "..#bcdeeeedcb#..",
  "..#bcdeffedcb#..",
  "..#bcdeffedcb#..",
  "..#bcdeeeedcb#..",
  "..#abcddddcba#..",
  "..#abccccccba#..",
  "..#aabbbbbbaa#..",
  "..#acbcbcbcba#..",
  "..#aabbbbbbaa#..",
  "..############..",
  "................"
)

$CONVERTER_TOP = @(
  "................",
  "..############..",
  "..#cccccccccc#..",
  "..#cbdddddbbc#..",
  "..#bddeeeeddb#..",
  "..#bdeffffedb#..",
  "..#bdefggfedb#..",
  "..#bdefggfedb#..",
  "..#bdeffffedb#..",
  "..#bddeeeeddb#..",
  "..#cbdddddbbc#..",
  "..#cccccccccc#..",
  "..#aaaaaaaaaa#..",
  "..############..",
  "................",
  "................"
)

function New-Bitmap([int]$w, [int]$h) {
  return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Save-Png($bmp, [string]$path, [string]$label) {
  if ($DryRun) { "DRY  $label -> $path"; $bmp.Dispose(); return }
  if ((Test-Path $path) -and -not $Force) {
    "SKIP (exists, use -Force to overwrite) $label -> $path"
    $bmp.Dispose()
    return
  }
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  "WROTE $label -> $path"
}

function Draw-Art($art, $pal) {
  $bmp = New-Bitmap 16 16
  for ($y = 0; $y -lt 16; $y++) {
    $row = $art[$y]
    for ($x = 0; $x -lt 16; $x++) {
      $ch = $row[$x]
      if ($ch -eq '.') { continue }
      $rgb = $pal["$ch"]
      if ($null -eq $rgb) { continue }
      $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2]))
    }
  }
  return $bmp
}

# ---------------------------------------------------------------- run
Save-Png (Draw-Art $EXTRACTOR_SIDE $EX) (Join-Path $blockDir "ee_extractor_side.png") "ee_extractor_side"
Save-Png (Draw-Art $EXTRACTOR_TOP  $EX) (Join-Path $blockDir "ee_extractor_top.png")  "ee_extractor_top"
Save-Png (Draw-Art $CONVERTER_SIDE $CV) (Join-Path $blockDir "energy_converter_side.png") "energy_converter_side"
Save-Png (Draw-Art $CONVERTER_FRONT $CV) (Join-Path $blockDir "energy_converter_front.png") "energy_converter_front"
Save-Png (Draw-Art $CONVERTER_TOP  $CV) (Join-Path $blockDir "energy_converter_top.png")  "energy_converter_top"
