# Generates Minecraft-style 16x16 pixel art for the Elder Crystal family
# (item + block + ore), inspired by the user's reference image:
# a faceted violet crystal with a bright inner core / energy veins.
#
# v2: the item silhouette is a symmetric hexagonal shard with a bright core column
#     (v1 read as a round blob), and block facets get a dark rim so they pop.
#
# Rewrites ONLY the elder_crystal* placeholder textures that we generated ourselves.
# Never touches hand-painted art (momo portraits, etc.).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\GenElderCrystalPixelArt.ps1
#   powershell -ExecutionPolicy Bypass -File tools\GenElderCrystalPixelArt.ps1 -DryRun
param([switch]$DryRun)

Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot "..\src\main\resources\assets\tinkersnewlife\textures"
$itemDir  = Join-Path $root "item"
$blockDir = Join-Path $root "block"

# ---------------------------------------------------------------- palette
# '#' outline | 1..5 facet shades (dark -> light) | 6 core white | . transparent
$PAL = @{
  '#' = @(0x2A, 0x0B, 0x3A)
  '1' = @(0x4B, 0x1C, 0x6E)
  '2' = @(0x6D, 0x2B, 0xA8)
  '3' = @(0x90, 0x39, 0xD6)
  '4' = @(0xB4, 0x5C, 0xF0)
  '5' = @(0xD9, 0x8C, 0xFF)
  '6' = @(0xFF, 0xFF, 0xFF)
}

# ---------------------------------------------------------------- item: hexagonal shard (symmetric, 16x16)
$GEM = @(
  "................",
  "......####......",
  ".....######.....",
  "....##4554##....",
  "...#34555543#...",
  "...#34566543#...",
  "..#1234565321#..",
  "..#1234565321#..",
  "..#1234565321#..",
  "...#12355321#...",
  "...#11233211#...",
  "....##2112##....",
  ".....#1221#.....",
  "......#11#......",
  ".......##.......",
  "................"
)

function New-Bitmap([int]$w, [int]$h) {
  return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Save-Png($bmp, [string]$path, [string]$label) {
  if ($DryRun) { "DRY  $label -> $path"; $bmp.Dispose(); return }
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  "WROTE $label -> $path"
}

function SetPx($bmp, [int]$x, [int]$y, $rgb) {
  if ($x -lt 0 -or $y -lt 0 -or $x -ge 16 -or $y -ge 16) { return }
  $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2]))
}

function Draw-Gem {
  $bmp = New-Bitmap 16 16
  for ($y = 0; $y -lt 16; $y++) {
    $row = $GEM[$y]
    for ($x = 0; $x -lt 16; $x++) {
      $c = $row[$x]
      if ($c -eq '.') { continue }
      SetPx $bmp $x $y $PAL["$c"]
    }
  }
  return $bmp
}

# deterministic pseudo-random (no Get-Random => reproducible runs)
function Noise([int]$x, [int]$y, [int]$seed) {
  $v = ($x * 73856093) -bxor ($y * 19349663) -bxor ($seed * 83492791)
  return [Math]::Abs($v % 1000) / 1000.0
}

# ---------------------------------------------------------------- block: purple stone with crystal facets
function Draw-BlockFace([int]$seed) {
  $bmp = New-Bitmap 16 16
  # stone base
  for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
      $n = Noise $x $y $seed
      SetPx $bmp $x $y @((58 + [int]($n * 14)), (34 + [int]($n * 12)), (70 + [int]($n * 16)))
    }
  }
  # embedded facets: darker rim + shaded body + white glint
  $facets = @(
    @(2, 2, 5, 4), @(9, 1, 5, 4), @(5, 7, 6, 5), @(1, 11, 4, 4), @(11, 10, 4, 4)
  )
  foreach ($f in $facets) {
    $fx = $f[0]; $fy = $f[1]; $fw = $f[2]; $fh = $f[3]
    # rim first (one ring around the box)
    for ($x = -1; $x -le $fw; $x++) { SetPx $bmp ($fx + $x) ($fy - 1) $PAL['#']; SetPx $bmp ($fx + $x) ($fy + $fh) $PAL['#'] }
    for ($y = -1; $y -le $fh; $y++) { SetPx $bmp ($fx - 1) ($fy + $y) $PAL['#']; SetPx $bmp ($fx + $fw) ($fy + $y) $PAL['#'] }
    for ($y = 0; $y -lt $fh; $y++) {
      for ($x = 0; $x -lt $fw; $x++) {
        $edge = ($x -eq 0) -or ($y -eq 0) -or ($x -eq $fw - 1) -or ($y -eq $fh - 1)
        if ($edge) { $rgb = $PAL['1'] }
        elseif (($x + $y) -eq [int](($fw + $fh) / 2)) { $rgb = $PAL['5'] }
        else { $rgb = $PAL['3'] }
        SetPx $bmp ($fx + $x) ($fy + $y) $rgb
      }
    }
    SetPx $bmp ($fx + 1) ($fy + 1) $PAL['6']
  }
  return $bmp
}

# ---------------------------------------------------------------- ore: deepslate base + crystal specks
function Draw-Ore {
  $bmp = New-Bitmap 16 16
  for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
      $n = Noise $x $y 7
      $v = 66 + [int]($n * 16)
      SetPx $bmp $x $y @($v, $v, ($v + 6))
    }
  }
  $specks = @(@(2, 2), @(4, 3), @(9, 2), @(11, 4), @(3, 9), @(7, 10), @(12, 11), @(6, 5))
  foreach ($s in $specks) {
    $sx = $s[0]; $sy = $s[1]
    SetPx $bmp $sx ($sy - 1) $PAL['3']
    SetPx $bmp ($sx - 1) $sy $PAL['2']
    SetPx $bmp $sx $sy $PAL['5']
    SetPx $bmp ($sx + 1) $sy $PAL['2']
    SetPx $bmp $sx ($sy + 1) $PAL['1']
  }
  return $bmp
}

# ---------------------------------------------------------------- run
Save-Png (Draw-Gem) (Join-Path $itemDir "elder_crystal.png") "elder_crystal (item)"

$blockTargets = Get-ChildItem $blockDir -Filter "elder_crystal_block*.png" -ErrorAction SilentlyContinue
if (-not $blockTargets) { "NOTE: no elder_crystal_block*.png found in $blockDir" }
$i = 0
foreach ($t in $blockTargets) {
  Save-Png (Draw-BlockFace (10 + $i)) $t.FullName ("block face " + $t.Name)
  $i++
}

$orePath = Join-Path $blockDir "elder_crystal_ore.png"
if (Test-Path $orePath) { Save-Png (Draw-Ore) $orePath "elder_crystal_ore (block)" }
else { "NOTE: $orePath not found (ore may use an overlay texture instead)" }
