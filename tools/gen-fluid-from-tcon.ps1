# 从**匠魂本体**取流体贴图底图并改色，产出本模组的 `<name>_still.png` / `<name>_flowing.png` + `.mcmeta`。
#
# 为什么要有这个脚本（踩过的坑）：
#   `gen-fluid-texture.ps1` 的 `-Source` 指的是**本模组 textures/block 下已有的 `<Source>_still.png`**，
#   它是"拿我们自己的贴图再改一次色"；如果拿它去对着匠魂的目录名（如 `molten/transparent`）用，
#   脚本会直接抛"找不到源贴图" ✗。而项目的既定口径是
#   **"流体材质尽量复用匠魂改色"** —— 底图必须来自匠魂本体 ✓。
#
# 匠魂的流体美术路径（注意：**没有 `_still` 后缀**、也不是 `textures/block` ✗）：
#   assets/tconstruct/textures/fluid/<分类>/<名字>/still.png
#   assets/tconstruct/textures/fluid/<分类>/<名字>/flowing.png
#
# 尺寸对齐既有约定：**裁到 16×256 still / 32×256 flowing**（`holy_spirit` / `molten_pyrium` /
#   `origin_polymer` 等全是这个尺寸 ✓）。匠魂原图是 16×624 / 16×784 / 32×1248 的多帧竖条，
#   裁剪即"取前 N 帧"，帧数与动画观感都保留 ✓。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen-fluid-from-tcon.ps1 `
#       -Name liquid_lightning -TconPath molten/transparent `
#       -Ramp FF2E2603,FF5C4E08,FF8F7A0E,FFC2A81C,FFE8D64A,FFFFF08C,FFFFFFFF `
#       -FogColor FF6B5A08
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$TconPath,
    [Parameter(Mandatory = $true)][string[]]$Ramp,
    [string]$FogColor = 'FF5A2A08',
    [int]$CropHeight = 256,
    [int]$Frametime = 4
)

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\block'
$mixDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\mantle\fluid_texture'
foreach ($d in @($texDir, $mixDir)) { if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null } }

if ($Ramp.Count -eq 1 -and $Ramp[0] -like '*,*') { $Ramp = $Ramp[0].Split(',') }
if ($Ramp.Count -ne 7) { throw "色带必须是 7 个颜色（当前 $($Ramp.Count) 个）" }

function Parse-Color([string]$hex) {
    $h = $hex.TrimStart('#')
    if ($h.Length -eq 6) { $h = 'FF' + $h }
    return @{
        A = [Convert]::ToInt32($h.Substring(0, 2), 16)
        R = [Convert]::ToInt32($h.Substring(2, 2), 16)
        G = [Convert]::ToInt32($h.Substring(4, 2), 16)
        B = [Convert]::ToInt32($h.Substring(6, 2), 16)
    }
}

$stops = @(0, 63, 102, 140, 178, 216, 255)
$colors = @()
foreach ($c in $Ramp) { $colors += (Parse-Color $c) }

function Ramp-Color([int]$grey) {
    if ($grey -le $stops[0]) { return $colors[0] }
    if ($grey -ge $stops[6]) { return $colors[6] }
    for ($i = 0; $i -lt 6; $i++) {
        $lo = $stops[$i]; $hi = $stops[$i + 1]
        if ($grey -ge $lo -and $grey -le $hi) {
            $t = ($grey - $lo) / [double]($hi - $lo)
            $a = $colors[$i]; $b = $colors[$i + 1]
            return @{
                A = [int]($a.A + ($b.A - $a.A) * $t)
                R = [int]($a.R + ($b.R - $a.R) * $t)
                G = [int]($a.G + ($b.G - $a.G) * $t)
                B = [int]($a.B + ($b.B - $a.B) * $t)
            }
        }
    }
    return $colors[6]
}

# ---------- 1. 在本地找匠魂 jar，取出底图 ----------
$entryStill = "assets/tconstruct/textures/fluid/$TconPath/still.png"
$entryFlowing = "assets/tconstruct/textures/fluid/$TconPath/flowing.png"

$jars = @()
$jars += (Get-ChildItem 'G:\tex\.minecraft\versions' -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Get-ChildItem $_.FullName -Filter '*TConstruct*.jar' -ErrorAction SilentlyContinue })
$jars += (Get-ChildItem 'G:\tex\.minecraft\versions' -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Get-ChildItem $_.FullName -Recurse -Filter '*TConstruct*.jar' -ErrorAction SilentlyContinue })
$jars = $jars | Sort-Object FullName -Unique

$cacheDir = Join-Path $root 'build\tcon-fluid'
if (-not (Test-Path $cacheDir)) { New-Item -ItemType Directory -Path $cacheDir -Force | Out-Null }

$srcStill = $null; $srcFlowing = $null; $fromJar = $null
foreach ($jar in $jars) {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
        $e1 = $z.GetEntry($entryStill); $e2 = $z.GetEntry($entryFlowing)
        if ($e1 -and $e2) {
            $srcStill = Join-Path $cacheDir ("{0}_still.png" -f ($TconPath -replace '/', '_'))
            $srcFlowing = Join-Path $cacheDir ("{0}_flowing.png" -f ($TconPath -replace '/', '_'))
            foreach ($pair in @(@($e1, $srcStill), @($e2, $srcFlowing))) {
                $ms = New-Object System.IO.MemoryStream
                $s = $pair[0].Open(); $s.CopyTo($ms); $s.Close()
                [System.IO.File]::WriteAllBytes($pair[1], $ms.ToArray()); $ms.Close()
            }
            $fromJar = $jar.FullName
            $z.Dispose(); break
        }
        $z.Dispose()
    } catch { }
}
if (-not $fromJar) { throw "本地找不到匠魂 jar，或里面没有 $entryStill / $entryFlowing" }
Write-Host "  底图取自 $fromJar"
Write-Host "  缓存 $srcStill / $srcFlowing"

# ---------- 2. 裁剪 + 按亮度重映射到目标色带 ----------
$pf = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
function Convert-Texture([string]$srcPath, [string]$dstPath, [int]$cropH) {
    $bmp = [System.Drawing.Bitmap]::new($srcPath)
    $w = $bmp.Width
    $h = [Math]::Min($cropH, $bmp.Height)
    if ($h -lt 1) { throw "裁剪高度不合法：$cropH" }
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, $pf)
    $stride = $data.Stride
    $bytes = [byte[]]::new($stride * $h)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    $bmp.UnlockBits($data); $bmp.Dispose()

    # 亮度分位拉伸，让底图的明暗铺满整条色带（图案形状不变）
    $hist = New-Object 'int[]' 256
    $count = 0
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $o = $y * $stride + $x * 4
            if ($bytes[$o + 3] -eq 0) { continue }
            $grey = [int](0.299 * $bytes[$o + 2] + 0.587 * $bytes[$o + 1] + 0.114 * $bytes[$o])
            $hist[$grey]++; $count++
        }
    }
    $lo = 0; $hi = 255
    if ($count -gt 0) {
        $cut = [int]($count * 0.02)
        $acc = 0; for ($i = 0; $i -lt 256; $i++) { $acc += $hist[$i]; if ($acc -ge $cut) { $lo = $i; break } }
        $acc = 0; for ($i = 255; $i -ge 0; $i--) { $acc += $hist[$i]; if ($acc -ge $cut) { $hi = $i; break } }
    }
    if ($hi -le $lo) { $lo = 0; $hi = 255 }
    Write-Host ("    亮度范围 {0}~{1}（拉伸到 0~255），输出 {2}x{3}" -f $lo, $hi, $w, $h)

    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $o = $y * $stride + $x * 4
            if ($bytes[$o + 3] -eq 0) { continue }
            $grey = 0.299 * $bytes[$o + 2] + 0.587 * $bytes[$o + 1] + 0.114 * $bytes[$o]
            $norm = ($grey - $lo) / [double]($hi - $lo)
            if ($norm -lt 0) { $norm = 0 } elseif ($norm -gt 1) { $norm = 1 }
            $c = Ramp-Color ([int]($norm * 255))
            $bytes[$o] = [byte]$c.B
            $bytes[$o + 1] = [byte]$c.G
            $bytes[$o + 2] = [byte]$c.R
        }
    }

    $out = [System.Drawing.Bitmap]::new($w, $h, $pf)
    $od = $out.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly, $pf)
    [System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $od.Scan0, $bytes.Length)
    $out.UnlockBits($od)
    $out.Save($dstPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
}

$mcmeta = "{`n  `"animation`": {`n    `"frametime`": $Frametime`n  }`n}`n"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$dstStill = Join-Path $texDir "${Name}_still.png"
$dstFlow = Join-Path $texDir "${Name}_flowing.png"
Convert-Texture $srcStill $dstStill $CropHeight
Convert-Texture $srcFlowing $dstFlow $CropHeight
[System.IO.File]::WriteAllText("$dstStill.mcmeta", $mcmeta, $utf8NoBom)
[System.IO.File]::WriteAllText("$dstFlow.mcmeta", $mcmeta, $utf8NoBom)
Write-Host "  生成 ${Name}_still.png / ${Name}_flowing.png + .mcmeta（动画 frametime=$Frametime）"

# ---------- 3. Mantle 流体纹理配置 ----------
$config = @"
{
  "still": "tinkersnewlife:block/${Name}_still",
  "flowing": "tinkersnewlife:block/${Name}_flowing",
  "color": "FFFFFFFF",
  "fogColor": "$FogColor",
  "fogStart": 0.35,
  "fogEnd": 4.5
}
"@
[System.IO.File]::WriteAllText((Join-Path $mixDir "${Name}.json"), $config, $utf8NoBom)
Write-Host "  生成 mantle/fluid_texture/${Name}.json"
Write-Host "完成：$Name（底图：匠魂 $TconPath）"
