# 生成 16×16「锭」贴图：**拿原版铁锭做模板改色**。
#
# 用户口径："能不能模仿原版铁锭改色？" —— 所以轮廓与明暗层次**完全沿用原版铁锭** ✓，
# 只把每个像素的**亮度**映射到材料自己的色带上（明暗关系不变，颜色全换）✓。
# 之前自己画的版本（手写字符掩码 / 圆角长方条）都被否掉了：一个像砖、一个像按钮 ✗。
#
# 模板从哪来：脚本会**自动**在本地 Minecraft 版本 jar 里找
#   assets/minecraft/textures/item/iron_ingot.png
# 并缓存到 build/ 下 —— 不把原版贴图复制进本模组仓库 ✓。
# 也可以用 -Source 直接指定一张 16×16 的模板 PNG ✓。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\gen-ingot-texture.ps1 `
#       -Name magic_gold_ingot -Ramp FF2B1A33,FF57307A,FF8A4A6E,FFC07A50,FFDFA845,FFF5CE72,FFFFF2BC
#
# 色带 7 个色对应亮度 0/63/102/140/178/216/255（与材料 grey_to_sprite 调色板同一组）✓
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string[]]$Ramp,
    [string]$Source = ''
)

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

if ($Ramp.Count -eq 1 -and $Ramp[0] -like '*,*') { $Ramp = $Ramp[0].Split(',') }
if ($Ramp.Count -ne 7) { throw "色带必须是 7 个颜色（当前 $($Ramp.Count) 个）" }

function Parse-Color([string]$hex) {
    $h = $hex.TrimStart('#')
    if ($h.Length -eq 6) { $h = 'FF' + $h }
    return [System.Drawing.Color]::FromArgb(
        [Convert]::ToInt32($h.Substring(0, 2), 16),
        [Convert]::ToInt32($h.Substring(2, 2), 16),
        [Convert]::ToInt32($h.Substring(4, 2), 16),
        [Convert]::ToInt32($h.Substring(6, 2), 16))
}

$stops = @(0, 63, 102, 140, 178, 216, 255)
$colors = @()
foreach ($x in $Ramp) { $colors += (Parse-Color $x) }

# 亮度 → 色带（相邻两档之间线性插值）
function Ramp-Color([int]$grey) {
    if ($grey -le $stops[0]) { return $colors[0] }
    if ($grey -ge $stops[6]) { return $colors[6] }
    for ($i = 0; $i -lt 6; $i++) {
        $lo2 = $stops[$i]; $hi2 = $stops[$i + 1]
        if ($grey -ge $lo2 -and $grey -le $hi2) {
            $t = ($grey - $lo2) / [double]($hi2 - $lo2)
            $a = $colors[$i]; $b = $colors[$i + 1]
            return [System.Drawing.Color]::FromArgb(
                [int]($a.A + ($b.A - $a.A) * $t),
                [int]($a.R + ($b.R - $a.R) * $t),
                [int]($a.G + ($b.G - $a.G) * $t),
                [int]($a.B + ($b.B - $a.B) * $t))
        }
    }
    return $colors[6]
}

# ---------- 取模板 ----------
$template = $Source
if (-not $template -or -not (Test-Path $template)) {
    $cached = Join-Path $root 'build\vanilla_iron_ingot.png'
    if (Test-Path $cached) {
        $template = $cached
    } else {
        $jars = @()
        $jars += (Get-ChildItem 'G:\tex\.minecraft\versions' -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Get-ChildItem $_.FullName -Filter '*.jar' -ErrorAction SilentlyContinue })
        $entryName = 'assets/minecraft/textures/item/iron_ingot.png'
        $from = $null
        foreach ($jar in $jars) {
            try {
                $z = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
                $e = $z.GetEntry($entryName)
                if ($e) {
                    $ms = New-Object System.IO.MemoryStream
                    $s = $e.Open(); $s.CopyTo($ms); $s.Close()
                    [System.IO.File]::WriteAllBytes($cached, $ms.ToArray()); $ms.Close()
                    $from = $jar.FullName
                    $z.Dispose(); break
                }
                $z.Dispose()
            } catch { }
        }
        if (-not $from) {
            throw "找不到原版铁锭模板：请用 -Source 指定一张锭贴图（脚本会在本地版本 jar 里找 assets/minecraft/textures/item/iron_ingot.png）"
        }
        Write-Host "  模板取自 $from（缓存到 build\vanilla_iron_ingot.png）"
        $template = $cached
    }
}

$bmp = [System.Drawing.Bitmap]::new($template)

# ---------- 亮度归一化（按 1%~99% 分位拉伸，让原版明暗铺满整条色带）----------
$hist = New-Object 'int[]' 256
$count = 0
for ($y = 0; $y -lt $bmp.Height; $y++) {
    for ($x = 0; $x -lt $bmp.Width; $x++) {
        $p = $bmp.GetPixel($x, $y); if ($p.A -eq 0) { continue }
        $g = [int](0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B)
        $hist[$g]++; $count++
    }
}
$lo = 0; $hi = 255
if ($count -gt 0) {
    $cut = [int]($count * 0.01)
    $acc = 0; for ($i = 0; $i -lt 256; $i++) { $acc += $hist[$i]; if ($acc -ge $cut) { $lo = $i; break } }
    $acc = 0; for ($i = 255; $i -ge 0; $i--) { $acc += $hist[$i]; if ($acc -ge $cut) { $hi = $i; break } }
}
if ($hi -le $lo) { $lo = 0; $hi = 255 }

# ---------- 改色 ----------
for ($y = 0; $y -lt $bmp.Height; $y++) {
    for ($x = 0; $x -lt $bmp.Width; $x++) {
        $p = $bmp.GetPixel($x, $y)
        if ($p.A -eq 0) { continue }
        $g = 0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B
        $norm = ($g - $lo) / [double]($hi - $lo)
        if ($norm -lt 0) { $norm = 0 } elseif ($norm -gt 1) { $norm = 1 }
        $col = Ramp-Color ([int][Math]::Round($norm * 255))
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, $col.R, $col.G, $col.B))
    }
}

$dst = Join-Path $outDir "$Name.png"
$bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Host "  生成 $Name.png（16x16，照原版铁锭改色；亮度 $lo~$hi；主色 $($Ramp[4])）"
