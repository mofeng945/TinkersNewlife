# 读取 Blockbench 工程文件（.bbmodel）：普通 JSON 直接解析，导出内嵌贴图；<lz> 自动备份给提示
#
# 用途：用户"另存工程"给我的 .bbmodel 里**内嵌了贴图 base64** ✓ ⇒ 一条命令就能取出
#       模型结构 + 贴图 PNG ✓（不用再让他们单独导贴图 ✓）。
#
# ⚠ Blockbench 的**自动备份**（AppData\Roaming\Blockbench\backups\*.bbmodel）是 `<lz>` 开头的
#   lzutf8 压缩数据 ✗，本脚本不解析（那个算法不值得为它移植 ✓）；遇到时提示用户用
#   File → Save Project 存一份普通 .bbmodel ✓。
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\read-bbmodel.ps1 -Path <bbmodel> [-ExtractTextures]

param(
    [Parameter(Mandatory = $true)][string]$Path,
    [switch]$ExtractTextures
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $root 'build'
if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir | Out-Null }

$full = (Resolve-Path $Path).Path
$raw = [System.IO.File]::ReadAllText($full, [System.Text.Encoding]::UTF8)
Write-Host "文件：$full（$((Get-Item $full).Length) 字节）"

if ($raw.StartsWith('<lz>')) {
    Write-Host "⚠ 这是 Blockbench 的**自动备份**（<lz> 压缩），本脚本不解析 ✗"
    Write-Host "  ⇒ 请在 Blockbench 里 File → Save Project 存一份普通 .bbmodel 再给我 ✓（同样内嵌贴图 ✓）"
    exit 2
}

[System.IO.File]::WriteAllText((Join-Path $buildDir 'bbmodel.json'), $raw, (New-Object Text.UTF8Encoding($false)))
$j = $raw | ConvertFrom-Json
if ($j.resolution) { Write-Host "画布：$($j.resolution.width)x$($j.resolution.height)" }

# 方块数：新格式在 elements，旧格式在 cubes
$cubes = if ($j.elements) { $j.elements } elseif ($j.cubes) { $j.cubes } else { @() }
Write-Host "方块：$($cubes.Count)"

# 结构（outliner）：组名就是材料槽（plating / maille / lace ✓）
if ($j.outliner) {
    foreach ($node in $j.outliner) {
        $kids = @()
        if ($node.children) { $kids = $node.children | Where-Object { $_.name } }
        Write-Host ("  组 {0}：{1} 个子项" -f $node.name, $kids.Count)
    }
}

# 贴图（内嵌 base64 ✓）
Write-Host "贴图：$(($j.textures | Measure-Object).Count) 张"
$ti = 0
foreach ($t in $j.textures) {
    $src = $t.source
    $isData = $src -and $src.StartsWith('data:image')
    $desc = if ($isData) { "内嵌 $($src.Length) 字符" } else { '（无内嵌数据 ✗）' }
    Write-Host ("  [{0}] name={1} mode={2} {3}" -f $ti, $t.name, $t.render_mode, $desc)
    if ($ExtractTextures -and $isData) {
        $bytes = [Convert]::FromBase64String($src.Substring($src.IndexOf(',') + 1))
        $outPng = Join-Path $buildDir ("bbtexture_{0}.png" -f $ti)
        [System.IO.File]::WriteAllBytes($outPng, $bytes)
        Add-Type -AssemblyName System.Drawing
        $im = [System.Drawing.Image]::FromFile($outPng)
        Write-Host ("      → {0}  ({1}x{2})" -f $outPng, $im.Width, $im.Height)
        $im.Dispose()
    }
    $ti++
}
