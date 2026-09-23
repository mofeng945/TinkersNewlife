# 生成「魔法布料」的物品贴图：**匠魂丝绢底图 + 按哈斯塔恶念的调色板渐变映射** ✓
#
# 用户要求：魔法布料的纹理 = 丝绢（tconstruct:item/materials/silky_cloth）按哈斯塔恶念的颜色改色 ✓；
#   第一版用"单一材质色 × 亮度"⇒ 用户反馈"颜色太单一、我的哈斯塔恶念没这么单调" ✗
#   ⇒ 改为**三色调色板渐变**：取该材质在匠魂生成器里的三张底图（border / body / highlight ✓）
#     作为「暗 / 中 / 亮」三色，再把丝绢的亮度归一化后**在三色间插值** ✓ ⇒ 青绿渐变、不再单调 ✓。
#
# 调色板来源（默认 `-Mode palette`）：
#   textures/generator/hastur_malice_border.png    （纯色，实测 = 材质色 #00593D ✓）
#   textures/generator/hastur_malice_body.png      （中间调 ≈ #96EFD3 ✓）
#   textures/generator/hastur_malice_highlight.png （高光 ≈ #BDFFEB ✓）
#   也可以用 `-Color '#rrggbb'`（单色 tint 模式 ✓）或 `-Color fluid`（流体贴图平均色 ✓）。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen-magic-cloth-texture.ps1
#   powershell ... -File tools\gen-magic-cloth-texture.ps1 -Mode tint -Color '#00aaaa'
#   powershell ... -File tools\gen-magic-cloth-texture.ps1 -Mode tint -Color fluid

param(
    [ValidateSet('palette', 'tint')][string]$Mode = 'palette',
    [string]$Color = '',            # tint 模式用：'' = colors.json 材质色；'fluid' = 流体平均色；或 #rrggbb
    [string]$PalettePrefix = 'hastur_malice',   # palette 模式用：generator 目录下的 <前缀>_border/_body/_highlight.png
    [double]$Dark = 0.62,           # tint 模式：最暗处系数
    [double]$Range = 0.38           # tint 模式：亮度每 1.0 增加的系数
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$assetRoot = Join-Path $root 'src\main\resources\assets\tinkersnewlife'
$colorJson = Join-Path $assetRoot 'mantle\colors.json'
$fluidPng = Join-Path $assetRoot 'textures\block\hastur_malice_still.png'
$genDir = Join-Path $assetRoot 'textures\generator'
$outDir = Join-Path $assetRoot 'textures\item'
$outPng = Join-Path $outDir 'magic_cloth.png'
$buildDir = Join-Path $root 'build'
if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir | Out-Null }

function Get-AverageColor([string]$path) {
    $img = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($path))
    $r = 0.0; $g = 0.0; $b = 0.0; $n = 0
    for ($y = 0; $y -lt $img.Height; $y++) {
        for ($x = 0; $x -lt $img.Width; $x++) {
            $p = $img.GetPixel($x, $y); if ($p.A -eq 0) { continue }
            $r += $p.R; $g += $p.G; $b += $p.B; $n++
        }
    }
    $img.Dispose()
    if ($n -eq 0) { return @(128, 128, 128) }
    return @([int]($r / $n), [int]($g / $n), [int]($b / $n))
}
function Lerp3($a, $b, [double]$t) {
    return @([int]($a[0] + ($b[0] - $a[0]) * $t), [int]($a[1] + ($b[1] - $a[1]) * $t), [int]($a[2] + ($b[2] - $a[2]) * $t))
}

# ---------- 找匠魂 jar（取丝绢贴图） ----------
$jar = 'C:\Users\ASUS\.gradle\caches\forge_gradle\deobf_dependencies\slimeknights\tconstruct\TConstruct\1.20.1-3.11.2.166_mapped_official_1.20.1\TConstruct-1.20.1-3.11.2.166_mapped_official_1.20.1.jar'
if (-not (Test-Path $jar)) {
    $jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\forge_gradle\deobf_dependencies\slimeknights\tconstruct" -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jar) { throw "找不到匠魂 jar（用于取丝绢贴图）" }
$silky = Join-Path $buildDir 'tcon-silky_cloth.png'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
$entry = $zip.GetEntry('assets/tconstruct/textures/item/materials/silky_cloth.png')
if (-not $entry) { $zip.Dispose(); throw "匠魂 jar 里没有 silky_cloth.png" }
$fs = [System.IO.File]::Create($silky); $es = $entry.Open(); $es.CopyTo($fs); $es.Close(); $fs.Close(); $zip.Dispose()
Write-Host "丝绢底图：$silky"

$src = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile($silky))

if ($Mode -eq 'palette') {
    # ---------- 三色调色板（暗=border / 中=body / 亮=highlight ✓） ----------
    $cDark = Get-AverageColor (Join-Path $genDir ($PalettePrefix + '_border.png'))
    $cMid = Get-AverageColor (Join-Path $genDir ($PalettePrefix + '_body.png'))
    $cLight = Get-AverageColor (Join-Path $genDir ($PalettePrefix + '_highlight.png'))
    Write-Host ("调色板：暗 #{0:x2}{1:x2}{2:x2}  中 #{3:x2}{4:x2}{5:x2}  亮 #{6:x2}{7:x2}{8:x2}" -f `
        $cDark[0], $cDark[1], $cDark[2], $cMid[0], $cMid[1], $cMid[2], $cLight[0], $cLight[1], $cLight[2])

    # 先把丝绢亮度归一化到 [0,1]（用实测最小/最大 ✓，这样三色都用得上 ✓）
    $minL = 1.0; $maxL = 0.0
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $p = $src.GetPixel($x, $y); if ($p.A -eq 0) { continue }
            $l = (0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B) / 255.0
            if ($l -lt $minL) { $minL = $l }; if ($l -gt $maxL) { $maxL = $l }
        }
    }
    Write-Host ("丝绢亮度范围：{0:N3} ~ {1:N3} ⇒ 归一化后映射到三色渐变 ✓" -f $minL, $maxL)

    $out = New-Object System.Drawing.Bitmap $src.Width, $src.Height
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $p = $src.GetPixel($x, $y)
            if ($p.A -eq 0) { continue }
            $l = (0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B) / 255.0
            $t = if ($maxL -gt $minL) { ($l - $minL) / ($maxL - $minL) } else { 0.5 }
            if ($t -lt 0.5) { $c = Lerp3 $cDark $cMid ($t * 2) } else { $c = Lerp3 $cMid $cLight (($t - 0.5) * 2) }
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, $c[0], $c[1], $c[2]))
        }
    }
} else {
    # ---------- 单色 tint（旧行为 ✓） ----------
    if ($Color -eq 'fluid') {
        $rgb = Get-AverageColor $fluidPng
        Write-Host ("颜色：流体平均色 #{0:x2}{1:x2}{2:x2}" -f $rgb[0], $rgb[1], $rgb[2])
    } elseif ($Color) {
        $h = $Color.TrimStart('#'); $rgb = @([Convert]::ToInt32($h.Substring(0, 2), 16), [Convert]::ToInt32($h.Substring(2, 2), 16), [Convert]::ToInt32($h.Substring(4, 2), 16))
        Write-Host "颜色：命令行 $Color"
    } else {
        $txt = [System.IO.File]::ReadAllText($colorJson, [System.Text.Encoding]::UTF8)
        $m = [regex]::Match($txt, '"material\.tinkersnewlife"\s*:\s*\{.*?"hastur_malice"\s*:\s*"#([0-9a-fA-F]{6})"', 'Singleline')
        if (-not $m.Success) { throw "colors.json 里找不到 material.tinkersnewlife.hastur_malice" }
        $h = $m.Groups[1].Value; $rgb = @([Convert]::ToInt32($h.Substring(0, 2), 16), [Convert]::ToInt32($h.Substring(2, 2), 16), [Convert]::ToInt32($h.Substring(4, 2), 16))
        Write-Host ("颜色：colors.json 材质色 #{0:x2}{1:x2}{2:x2}" -f $rgb[0], $rgb[1], $rgb[2])
    }
    $out = New-Object System.Drawing.Bitmap $src.Width, $src.Height
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $p = $src.GetPixel($x, $y)
            if ($p.A -eq 0) { continue }
            $l = (0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B) / 255.0
            $k = $Dark + $Range * $l
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A,
                [Math]::Min(255, [int]($rgb[0] * $k)), [Math]::Min(255, [int]($rgb[1] * $k)), [Math]::Min(255, [int]($rgb[2] * $k))))
        }
    }
}
$src.Dispose()
$out.Save($outPng, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "已输出：$outPng（$($out.Width)x$($out.Height)）"

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
