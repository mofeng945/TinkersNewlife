# Rebuilds the Elder Crystal block textures from VANILLA textures (recoloured to our palette),
# which is exactly what the user asked for: "crystal block modelled on the amethyst block,
# ore in the vanilla deepslate-ore style".
#
#   elder_crystal_block.png  <- minecraft:block/amethyst_block.png        (luminance -> our purple ramp)
#   elder_crystal_ore.png    <- minecraft:block/deepslate_diamond_ore.png (deepslate greys kept as-is,
#                                                                          coloured specks -> our ramp)
#
# Safety: existing target files are COPIED to tools/backup-block-textures/ before being overwritten
#         (that backup folder is outside assets/ so it never ships in the jar).
#         Pass -Restore to copy the backups back instead of generating.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\GenElderCrystalFromVanilla.ps1
#   powershell -ExecutionPolicy Bypass -File tools\GenElderCrystalFromVanilla.ps1 -DryRun
#   powershell -ExecutionPolicy Bypass -File tools\GenElderCrystalFromVanilla.ps1 -Restore
param([switch]$DryRun, [switch]$Restore)

Add-Type -AssemblyName System.Drawing

$mcJar   = "G:\tex\.minecraft\versions\1.20.1-Forge_47.4.22\1.20.1-Forge_47.4.22.jar"
$blockDir = Join-Path $PSScriptRoot "..\src\main\resources\assets\tinkersnewlife\textures\block"
$itemDir  = Join-Path $PSScriptRoot "..\src\main\resources\assets\tinkersnewlife\textures\item"
$backupDir = Join-Path $PSScriptRoot "backup-block-textures"

# 4-stop ramp of our crystal palette (dark -> light), sampled by luminance
$RAMP = @(
  @(0.00, 0x2A, 0x0B, 0x3A),
  @(0.35, 0x4B, 0x1C, 0x6E),
  @(0.62, 0x90, 0x39, 0xD6),
  @(0.85, 0xD9, 0x8C, 0xFF),
  @(1.00, 0xFF, 0xFF, 0xFF)
)

function Ramp([double]$t) {
  if ($t -lt 0) { $t = 0 } elseif ($t -gt 1) { $t = 1 }
  for ($i = 0; $i -lt $RAMP.Count - 1; $i++) {
    $a = $RAMP[$i]; $b = $RAMP[$i + 1]
    if ($t -le $b[0]) {
      $span = $b[0] - $a[0]
      $k = if ($span -le 0) { 0.0 } else { ($t - $a[0]) / $span }
      return @(
        [int][Math]::Round($a[1] + ($b[1] - $a[1]) * $k),
        [int][Math]::Round($a[2] + ($b[2] - $a[2]) * $k),
        [int][Math]::Round($a[3] + ($b[3] - $a[3]) * $k)
      )
    }
  }
  return @(255, 255, 255)
}

# Reads one PNG straight out of the merged client jar (no extraction to disk)
function ReadJarPng([string]$entryName) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $z = [System.IO.Compression.ZipFile]::OpenRead($mcJar)
  $e = $z.Entries | Where-Object { $_.FullName -eq $entryName } | Select-Object -First 1
  if (-not $e) { $z.Dispose(); throw "jar entry not found: $entryName" }
  $ms = New-Object System.IO.MemoryStream
  $s = $e.Open(); $s.CopyTo($ms); $s.Close(); $z.Dispose()
  $ms.Position = 0
  $img = [System.Drawing.Image]::FromStream($ms)
  return $img
}

function Backup([string]$path) {
  if (-not (Test-Path $path)) { return }
  if (-not (Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir | Out-Null }
  $dest = Join-Path $backupDir (Split-Path $path -Leaf)
  Copy-Item $path $dest -Force
  "BACKUP $(Split-Path $path -Leaf) -> $dest"
}

function SavePng($bmp, [string]$path, [string]$label) {
  if ($DryRun) { "DRY  $label -> $path"; $bmp.Dispose(); return }
  Backup $path
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  "WROTE $label -> $path"
}

# ---------------- restore mode ----------------
if ($Restore) {
  if (-not (Test-Path $backupDir)) { "no backup dir: $backupDir"; return }
  foreach ($b in Get-ChildItem $backupDir -Filter *.png) {
    Copy-Item $b.FullName (Join-Path $blockDir $b.Name) -Force
    "RESTORED $($b.Name)"
  }
  return
}

# ---------------- 1) block: recolour vanilla amethyst_block ----------------
$srcBlock = ReadJarPng "assets/minecraft/textures/block/amethyst_block.png"
$w = $srcBlock.Width; $h = $srcBlock.Height
$outBlock = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $h; $y++) {
  for ($x = 0; $x -lt $w; $x++) {
    $p = $srcBlock.GetPixel($x, $y)
    # luminance (vanilla amethyst is already purple: keep its pattern, take our hues)
    $l = (0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B) / 255.0
    $rgb = Ramp $l
    $outBlock.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2]))
  }
}
$srcBlock.Dispose()
SavePng $outBlock (Join-Path $blockDir "elder_crystal_block.png") "elder_crystal_block (from amethyst_block)"

# ---------------- 2) ore: recolour vanilla deepslate_diamond_ore ----------------
$srcOre = ReadJarPng "assets/minecraft/textures/block/deepslate_diamond_ore.png"
$w2 = $srcOre.Width; $h2 = $srcOre.Height
$outOre = New-Object System.Drawing.Bitmap($w2, $h2, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $h2; $y++) {
  for ($x = 0; $x -lt $w2; $x++) {
    $p = $srcOre.GetPixel($x, $y)
    $mx = [Math]::Max($p.R, [Math]::Max($p.G, $p.B))
    $mn = [Math]::Min($p.R, [Math]::Min($p.G, $p.B))
    $sat = $mx - $mn
    if ($sat -lt 26) {
      # deepslate body: keep vanilla grey EXACTLY (that is the style the user asked for)
      $outOre.SetPixel($x, $y, $p)
    } else {
      # mineral speck: vanilla diamond specks are cyan/white (luminance ~0.75-0.95)
      # => COMPRESS the luminance into the purple band, otherwise everything turns white.
      $l = (0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B) / 255.0
      $l = 0.22 + 0.55 * $l
      $rgb = Ramp $l
      $outOre.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2]))
    }
  }
}
$srcOre.Dispose()
SavePng $outOre (Join-Path $blockDir "elder_crystal_ore.png") "elder_crystal_ore (from deepslate_diamond_ore)"

# ---------------- 3) item: recolour vanilla diamond (keep alpha!) ----------------
$srcItem = ReadJarPng "assets/minecraft/textures/item/diamond.png"
$w3 = $srcItem.Width; $h3 = $srcItem.Height
$outItem = New-Object System.Drawing.Bitmap($w3, $h3, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $h3; $y++) {
  for ($x = 0; $x -lt $w3; $x++) {
    $p = $srcItem.GetPixel($x, $y)
    if ($p.A -lt 8) { continue }                     # transparent stays transparent
    # diamond is light cyan/white (luminance ~0.75-1.0) => COMPRESS into the purple band,
    # otherwise the whole gem turns white. Highlights still reach light violet.
    $l = (0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B) / 255.0
    $l = 0.25 + 0.58 * $l
    $rgb = Ramp $l
    $outItem.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, $rgb[0], $rgb[1], $rgb[2]))
  }
}
$srcItem.Dispose()
SavePng $outItem (Join-Path $itemDir "elder_crystal.png") "elder_crystal (item, from diamond)"
# ---------------- 4) pedestal: overlay VANILLA sculk veins on the current texture ----------------
# (user: "台座纹理加一点幽匿脉络元素") - kept subtle: vein pixels are drawn at ~78% alpha.
$veinPath = "assets/minecraft/textures/block/sculk_vein.png"
$vein = $null
try { $vein = ReadJarPng $veinPath } catch { "NOTE: $veinPath not in jar -> pedestal left untouched" }
if ($vein -ne $null) {
  foreach ($name in @("elder_mana_pedestal_side.png", "elder_mana_pedestal_top.png")) {
    $path = Join-Path $blockDir $name
    if (-not (Test-Path $path)) { "SKIP (no base) $name"; continue }
    # base = the PRISTINE texture from the backup if we have one (so re-running never stacks veins)
    $basePath = Join-Path $backupDir $name
    if (-not (Test-Path $basePath)) { $basePath = $path }
    $base = [System.Drawing.Image]::FromFile($basePath)
    $out = New-Object System.Drawing.Bitmap(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.DrawImage($base, 0, 0, 16, 16)
    $g.Dispose()
    $base.Dispose()
    for ($y = 0; $y -lt 16; $y++) {
      for ($x = 0; $x -lt 16; $x++) {
        $v = $vein.GetPixel($x, $y)
        if ($v.A -lt 8) { continue }                 # vein texture has a transparent background
        if ($v.G -lt 45) { continue }                # "a TOUCH of sculk": keep only the brighter strands
        $b = $out.GetPixel($x, $y)
        $a = [int]($v.A * 0.40)                      # 40% => a faint teal trace, base still reads
        $r = [int](($v.R * $a + $b.R * (255 - $a)) / 255)
        $gg = [int](($v.G * $a + $b.G * (255 - $a)) / 255)
        $bl = [int](($v.B * $a + $b.B * (255 - $a)) / 255)
        $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $gg, $bl))
      }
    }
    SavePng $out $path ($name + " (+sculk veins)")
  }
  $vein.Dispose()
}