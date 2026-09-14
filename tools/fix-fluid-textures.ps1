# 流体贴图批量修正脚本
#
# 修两件事：
#   1) 所有**流动(flowing)** 贴图的帧宽从 16 改成 **32**（原版约定：still = 16×16 帧、flowing = 32×32 帧；
#      原版 water_flow.png 就是 32 宽）。做法是对每一帧做 2× 最近邻放大，图案不变、像素网格对齐。
#   2) 三个"单文件"流体（灰白之墨 ashen_ink、熔融杜兰达尔 molten_durandal、哈斯塔恶意 hastur_malice）
#      拆成 still + flowing 两个文件；其中前两个原本**只有一帧、也没有 .mcmeta（完全不流动）**，
#      这里用"纵向逐帧滚动 1px"合成 16 帧的**完美循环**动画。
#
# 不改动：still 贴图保持 16 宽；已存在的 .mcmeta 帧参数不变（帧数没变，依然有效）。
# 用法：powershell -ExecutionPolicy Bypass -File tools\fix-fluid-textures.ps1

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\block'
$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\mantle\fluid_texture'
$pf = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb

function Read-Strip([string]$path) {
    $b = [System.Drawing.Bitmap]::new($path)
    $w = $b.Width; $h = $b.Height
    $d = $b.LockBits((New-Object System.Drawing.Rectangle 0, 0, $w, $h), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, $pf)
    $st = $d.Stride
    $bytes = [byte[]]::new($st * $h)
    [System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $bytes, 0, $bytes.Length)
    $b.UnlockBits($d); $b.Dispose()
    return @{ bytes = $bytes; w = $w; h = $h; stride = $st }
}

function Write-Strip([string]$path, [byte[]]$bytes, [int]$w, [int]$h) {
    $b = [System.Drawing.Bitmap]::new($w, $h, $pf)
    $d = $b.LockBits((New-Object System.Drawing.Rectangle 0, 0, $w, $h), [System.Drawing.Imaging.ImageLockMode]::WriteOnly, $pf)
    [System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $d.Scan0, $bytes.Length)
    $b.UnlockBits($d)
    $b.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $b.Dispose()
}

function Write-Mcmeta([string]$pngPath, [int]$frametime) {
    $json = "{`n  `"animation`": {`n    `"frametime`": " + $frametime + "`n  }`n}"
    [System.IO.File]::WriteAllText(($pngPath + '.mcmeta'), $json, (New-Object System.Text.UTF8Encoding($false)))
}

# 把 16 宽的帧条 2x 放大成 32 宽（每帧 size×size）
function Widen-Strip([string]$srcPath, [string]$dstPath) {
    $s = Read-Strip $srcPath
    $fs = $s.w                     # 帧尺寸（16）
    $frames = [int]($s.h / $fs)
    $nw = $fs * 2
    $nb = [byte[]]::new($nw * 4 * $nw * $frames)   # 每行 nw*4 字节，共 nw*frames 行
    for ($f = 0; $f -lt $frames; $f++) {
        for ($y = 0; $y -lt $nw; $y++) {
            $sy = [int]($y / 2)
            $outRow = (($f * $nw) + $y) * $nw * 4
            $inRow = (($f * $fs) + $sy) * $s.stride
            for ($x = 0; $x -lt $nw; $x++) {
                $sx = [int]($x / 2)
                $i = $inRow + $sx * 4
                $o = $outRow + $x * 4
                $nb[$o] = $s.bytes[$i]; $nb[($o + 1)] = $s.bytes[($i + 1)]
                $nb[($o + 2)] = $s.bytes[($i + 2)]; $nb[($o + 3)] = $s.bytes[($i + 3)]
            }
        }
    }
    Write-Strip $dstPath $nb $nw ($nw * $frames)
    return @{ frames = $frames; w = $nw; h = $nw * $frames }
}

# 从单帧静态贴图合成"纵向滚动"的循环动画条
function Make-ScrollStrip([string]$srcPath, [string]$dstPath, [int]$widen) {
    $s = Read-Strip $srcPath
    $fs = $s.w
    $frames = $fs                      # 16 帧，每帧滚 1px -> 完美循环
    $nw = $fs * $widen
    $nb = [byte[]]::new($nw * 4 * $nw * $frames)
    for ($f = 0; $f -lt $frames; $f++) {
        for ($y = 0; $y -lt $nw; $y++) {
            $sy = [int]($y / $widen)
            $sy = ($sy + $f) % $fs     # 纵向滚动
            $outRow = (($f * $nw) + $y) * $nw * 4
            $inRow = $sy * $s.stride
            for ($x = 0; $x -lt $nw; $x++) {
                $sx = [int]($x / $widen)
                $i = $inRow + $sx * 4
                $o = $outRow + $x * 4
                $nb[$o] = $s.bytes[$i]; $nb[($o + 1)] = $s.bytes[($i + 1)]
                $nb[($o + 2)] = $s.bytes[($i + 2)]; $nb[($o + 3)] = $s.bytes[($i + 3)]
            }
        }
    }
    Write-Strip $dstPath $nb $nw ($nw * $frames)
    return @{ frames = $frames; w = $nw; h = $nw * $frames }
}

Write-Host '=== 1) 已有的 still/flowing 成对：把 flowing 加宽到 32 ==='
$pairs = @(
    @{ still = 'dragon_blood_still';             flow = 'dragon_blood_flowing' },
    @{ still = 'gheloth_blood_still';            flow = 'gheloth_blood_flow' },
    @{ still = 'molten_dragonsteel_still';       flow = 'molten_dragonsteel_flowing' },
    @{ still = 'molten_dread_still';             flow = 'molten_dread_flowing' },
    @{ still = 'molten_dreadsteel_still';        flow = 'molten_dreadsteel_flowing' },
    @{ still = 'molten_nicholas_blessing_still'; flow = 'molten_nicholas_blessing_flow' },
    @{ still = 'unholy_blood_still';             flow = 'unholy_blood_flowing' }
)
foreach ($p in $pairs) {
    $fp = Join-Path $dir ($p.flow + '.png')
    if (-not (Test-Path $fp)) { Write-Host ("  缺失 " + $p.flow); continue }
    $r = Widen-Strip $fp $fp
    Write-Host ("  " + $p.flow.PadRight(34) + " -> " + $r.w + "x" + $r.h + " (" + $r.frames + " 帧, 每帧 " + $r.w + "x" + $r.w + ")")
}

Write-Host '=== 2) 三个单文件流体：拆成 still + flowing ==='
# 2a. 哈斯塔恶意：原本就是 10 帧动画，只需拆文件
$h = Join-Path $dir 'hastur_malice.png'
if (Test-Path $h) {
    $s = Read-Strip $h
    Copy-Item $h (Join-Path $dir 'hastur_malice_still.png') -Force
    Write-Mcmeta (Join-Path $dir 'hastur_malice_still.png') 4
    $r = Widen-Strip $h (Join-Path $dir 'hastur_malice_flowing.png')
    Write-Mcmeta (Join-Path $dir 'hastur_malice_flowing.png') 4
    Write-Host ("  hastur_malice: still 16x" + $s.h + " (" + [int]($s.h / 16) + " 帧)  flowing " + $r.w + "x" + $r.h)
}
# 2b. 灰白之墨 / 熔融杜兰达尔：原本只有一帧且无动画 -> 合成滚动循环
foreach ($n in @('ashen_ink', 'molten_durandal')) {
    $sp = Join-Path $dir ($n + '.png')
    if (-not (Test-Path $sp)) { Write-Host ("  缺失 " + $n); continue }
    $rs = Make-ScrollStrip $sp (Join-Path $dir ($n + '_still.png')) 1
    Write-Mcmeta (Join-Path $dir ($n + '_still.png')) 4
    $rf = Make-ScrollStrip $sp (Join-Path $dir ($n + '_flowing.png')) 2
    Write-Mcmeta (Join-Path $dir ($n + '_flowing.png')) 4
    Write-Host ("  " + $n.PadRight(18) + " still " + $rs.w + "x" + $rs.h + " (" + $rs.frames + " 帧)  flowing " + $rf.w + "x" + $rf.h)
}

Write-Host '=== 3) 更新 fluid_texture 映射，让三个流体指向新文件 ==='
foreach ($n in @('ashen_ink', 'molten_durandal', 'hastur_malice')) {
    $jp = Join-Path $texDir ($n + '.json')
    if (-not (Test-Path $jp)) { Write-Host ("  缺失 " + $jp); continue }
    $o = Get-Content -Raw -Encoding UTF8 $jp | ConvertFrom-Json
    $o.still = 'tinkersnewlife:block/' + $n + '_still'
    $o.flowing = 'tinkersnewlife:block/' + $n + '_flowing'
    $json = $o | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText($jp, $json, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ("  " + $n + " -> still=" + $o.still + "  flowing=" + $o.flowing)
}
Write-Host 'done.'
