# 生成「魔法布料」的物品贴图：**用匠魂的丝绢贴图 + 按哈斯塔恶念的颜色改色** ✓
#
# 用户要求：魔法布料的纹理 = 丝绢（tconstruct:item/materials/silky_cloth）按哈斯塔恶念的颜色重新上色 ✓
#
# 做法：
#   1. 从匠魂 jar 里取出 `assets/tconstruct/textures/item/materials/silky_cloth.png`（16×16 ✓）；
#   2. 逐像素取**亮度** L（保留丝绢的布料明暗层次 ✓），再按 `k = Dark + Range × L` 乘目标色 ✓
#      （与 `tools/gen-wizard-armor-materials.ps1` 同一套思路：只换色、不换纹样 ✓）；
#   3. 输出 `assets/tinkersnewlife/textures/item/magic_cloth.png` ✓（并另存一张放大预览到 build\ ✓）。
#
# 颜色来源：默认读 `assets/tinkersnewlife/mantle/colors.json` 里
#   `material.tinkersnewlife.hastur_malice` ✓（**材质色**，与工具部件用的同一个 ✓）。
#   想换成流体那种青绿，用 `-Color '#00aaaa'` 或 `-Color fluid`（取流体贴图平均色 ✓）即可。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen-magic-cloth-texture.ps1
#   powershell ... -File tools\gen-magic-cloth-texture.ps1 -Color '#00aaaa'
#   powershell ... -File tools\gen-magic-cloth-texture.ps1 -Color fluid

param(
    [string]$Color = '',            # '' = 读 colors.json 的材质色；'fluid' = 流体贴图平均色；或直接给 #rrggbb
    [double]$Dark = 0.62,           # 最暗处的系数
    [double]$Range = 0.38           # 亮度每 1.0 增加的系数（Dark + Range = 最亮处系数）
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$colorJson = Join-Path $root 'src\main\resources\assets\tinkersnewlife\mantle\colors.json'
$fluidPng = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\block\hastur_malice_still.png'
$buildDir = Join-Path $root 'build'
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
$outPng = Join-Path $outDir 'magic_cloth.png'
if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir | Out-Null }

# ---------- 找匠魂 jar ----------
$candidates = @(
    'C:\Users\ASUS\.gradle\caches\forge_gradle\deobf_dependencies\slimeknights\tconstruct\TConstruct\1.20.1-3.11.2.166_mapped_official_1.20.1\TConstruct-1.20.1-3.11.2.166_mapped_official_1.20.1.jar'
)
$jar = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $jar) {
    $jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\forge_gradle\deobf_dependencies\slimeknights\tconstruct" -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jar) { throw "找不到匠魂 jar（用于取丝绢贴图）" }
Write-Host "匠魂 jar：$jar"

$silky = Join-Path $buildDir 'tcon-silky_cloth.png'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
$entry = $zip.GetEntry('assets/tconstruct/textures/item/materials/silky_cloth.png')
if (-not $entry) { $zip.Dispose(); throw "匠魂 jar 里没有 silky_cloth.png" }
$fs = [System.IO.File]::Create($silky)
$es = $entry.Open(); $es.CopyTo($fs); $es.Close(); $fs.Close(); $zip.Dispose()
Write-Host "已取出丝绢贴图：$silky"

# ---------- 目标颜色 ----------
function Parse-Hex([string]$hex) {
    $h = $hex.TrimStart('#')
    return @([Convert]::ToInt32($h.Substring(0, 2), 16), [Convert]::ToInt32($h.Substring(2, 2), 16), [Convert]::ToInt32($h.Substring(4, 2), 16))
}
if ($Color -eq 'fluid') {
    $f = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($fluidPng))
    $r = 0; $g = 0; $b = 0; $n = 0
    for ($y = 0; $y -lt $f.Height; $y++) {
        for ($x = 0; $x -lt $f.Width; $x++) {
            $p = $f.GetPixel($x, $y); if ($p.A -eq 0) { continue }
            $r += $p.R; $g += $p.G; $b += $p.B; $n++
        }
    }
    $f.Dispose()
    $rgb = @([int]($r / $n), [int]($g / $n), [int]($b / $n))
    Write-Host ("颜色来源：流体贴图平均色 #{0:x2}{1:x2}{2:x2}" -f $rgb[0], $rgb[1], $rgb[2])
} elseif ($Color) {
    $rgb = Parse-Hex $Color
    Write-Host "颜色来源：命令行 -Color $Color"
} else {
    $txt = [System.IO.File]::ReadAllText($colorJson, [System.Text.Encoding]::UTF8)
    $m = [regex]::Match($txt, '"material\.tinkersnewlife"\s*:\s*\{.*?"hastur_malice"\s*:\s*"#([0-9a-fA-F]{6})"', 'Singleline')
    if (-not $m.Success) { throw "colors.json 里找不到 material.tinkersnewlife.hastur_malice" }
    $rgb = Parse-Hex $m.Groups[1].Value
    Write-Host ("颜色来源：colors.json 材质色 #{0:x2}{1:x2}{2:x2}" -f $rgb[0], $rgb[1], $rgb[2])
}

# ---------- 改色 ----------
$src = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($silky))
$out = New-Object System.Drawing.Bitmap $src.Width, $src.Height
for ($y = 0; $y -lt $src.Height; $y++) {
    for ($x = 0; $x -lt $src.Width; $x++) {
        $p = $src.GetPixel($x, $y)
        if ($p.A -eq 0) { continue }                      # 透明处保持透明 ✓
        $l = (0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B) / 255.0
        $k = $Dark + $Range * $l
        $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(
            $p.A,
            [Math]::Min(255, [int]($rgb[0] * $k)),
            [Math]::Min(255, [int]($rgb[1] * $k)),
            [Math]::Min(255, [int]($rgb[2] * $k))))
    }
}
$src.Dispose()
$out.Save($outPng, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "已输出：$outPng（$($out.Width)x$($out.Height)）"

# 放大 12 倍的预览（方便直接看效果 ✓）
$z = New-Object System.Drawing.Bitmap ($out.Width * 12), ($out.Height * 12)
$zg = [System.Drawing.Graphics]::FromImage($z)
$zg.Clear([System.Drawing.Color]::FromArgb(255, 30, 30, 36))
$zg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$zg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$zg.DrawImage($out, 0, 0, $z.Width, $z.Height)
$zg.Dispose()
$preview = Join-Path $buildDir 'magic_cloth-preview-x12.png'
$z.Save($preview, [System.Drawing.Imaging.ImageFormat]::Png)
$z.Dispose(); $out.Dispose()
Write-Host "预览（×12）：$preview"
