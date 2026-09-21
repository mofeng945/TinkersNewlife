# tools/GenBrokenTextures.ps1
#
# 从「已有的工具贴图」派生出「破损（broken）贴图」。
#
# ── 为什么是这个命名、这个路径？（不是我们自己发明的规矩）
# 匠魂（TConstruct 1.20.1-3.11.2.166）本身就有一整套破损机制，源码证据：
#   * slimeknights/tconstruct/library/tools/helper/ToolDamageUtil.java
#       工具耐久耗尽 -> NBT 写 `tic_broken: 1b`（ToolStack.TAG_BROKEN）
#   * slimeknights/tconstruct/library/client/model/TinkerItemProperties.java
#       注册物品属性 `tconstruct:broken`（float，破损=1，未破损=0），
#       只对匠魂自己的物品注册（ToolClientEvents.clientSetupEvent）
#   * slimeknights/tconstruct/library/data/AbstractToolItemModelProvider.java
#       tool(...) -> transformTool("tool/<name>/broken", 原模型, "", false, "broken", parts...)
#       即：把基模型抄一份成 `item/tool/<name>/broken.json`，
#       并把列出的 part 贴图名统一加后缀 `_broken`
#   * slimeknights/tconstruct/library/client/materials/MaterialRenderInfo.java
#       getSprite() 会按「材质后缀」去找 `<part>_<材质后缀>`，
#       例：broken 模型的 part 贴图是 `.../head1_broken`，材质 tconstruct:iron
#           -> 实取 `.../head1_broken_tconstruct_iron`；找不到才回退到 `head1_broken`
#   所以贴图约定 =  `<part>_broken.png` / `<part>_broken_<材质后缀>.png`（同目录、同尺寸）
#
# ── 本脚本干什么
#   1. 扫 models/item/*.json，挑出 `"loader": "tconstruct:tool"`（= 匠魂工具）的模型，
#      从它的 `textures` 里取出每个「部件贴图」的基名（如 item/tool/war_scythe/head1）；
#   2. 在对应贴图目录里找出该基名的**所有材质变体**（`head1.png`、`head1_tconstruct_iron.png` …），
#      逐个派生出 `head1_broken.png`、`head1_broken_tconstruct_iron.png` …；
#   3. 破损手法（保守、像素风一致、不模糊不过滤）：
#        a) 整体压暗 + 轻微降饱和：RGB = mix(rgb, luma, 22%) × 0.62（alpha 原样保留）
#        b) 手绘感裂纹：1px 宽折线（有几率拐 45°），只画在**不透明**像素上
#           （×0.45，约等于原图 28% 亮度）；条数/长度随部件不透明像素数缩放
#           （条数 ~ 不透明像素数/40，长度 ~ /18，下限 1 条 2 格，上限 8 条 8 格；
#             所以 16x16 的小部件只留 1~2 格裂，64x64 的杜兰达尔剑才有明显裂网）
#        c) 边缘崩口：只啃「有透明邻居 且 至少 2 个不透明 4-邻居」的边界像素（最多 6 个、
#           不超过不透明像素的 5%），取离中心最远的那几个 —— 且带**连通性守卫**：
#           啃完若不透明区断成两块就撤回该格，薄到 1~2px 的刀身/手柄绝不会断成浮空两截
#        d) 动图（`*.png.mcmeta`，如 tconstruct_fiery 的 16x32 / 32x64 两帧）：
#           逐帧施加**同一套**相对坐标的损伤，并把 .mcmeta 一并复制给 `_broken` 版本
#   4. **同一部件的所有材质变体共用同一套损伤图案**（图案由基贴图 + 部件路径做种子决定），
#      所以铁的破损处和金的破损处位置一致，不会各破各的
#   5. 绝不修改任何既有 PNG / 既有模型 JSON：
#        贴图只新建 `*_broken*.png`（已存在则默认跳过，用 -Force 重生成）；
#        模型只新建 `models/item/tool/<工具>/broken.json`（同理）
#   6. 本脚本**不改**基模型的 `overrides`（铁律：models/** 既有文件一律不动）。
#      `tconstruct:broken` 那个分支由 Java 侧接线补上 —— 见
#        src/main/java/com/mofengbaizhi/tinkersnewlife/client/renderer/BrokenToolModels.java
#      （RegisterAdditional 让 broken.json 被烘焙 + ModifyBakingResult 给物品模型包一层
#        破损感知的 ItemOverrides，效果与匠魂自己写 overrides 完全一致）
#
# ── 怎么再跑一遍（贴图/部件名改了就重跑；Java 不用动）
#     powershell -ExecutionPolicy Bypass -File tools\GenBrokenTextures.ps1
#     powershell -ExecutionPolicy Bypass -File tools\GenBrokenTextures.ps1 -Force        # 覆盖重生成
#     powershell -ExecutionPolicy Bypass -File tools\GenBrokenTextures.ps1 -DryRun       # 只看清单不写盘
#     powershell -ExecutionPolicy Bypass -File tools\GenBrokenTextures.ps1 -Filter war_scythe
#
# 注意：本机是 PowerShell 5.1，中文脚本必须以 UTF-8 **带 BOM** 保存（本文件已带）。

param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [switch]$Force,      # 覆盖已存在的 *_broken*.png（默认：已存在就跳过）
    [switch]$DryRun,     # 只打印，不写盘
    [string]$Filter = '' # 只处理路径匹配该正则的部件贴图，便于调参
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$assets   = Join-Path $Root 'src\main\resources\assets\tinkersnewlife'
$modelDir = Join-Path $assets 'models\item'
$texRoot  = Join-Path $assets 'textures'

# ── 破损参数（要调观感就改这里，然后 -Force 重跑） ───────────────────────────────
$K_DARK    = 0.62  # 整体压暗系数（用户口径：RGB ×0.62）
$K_DESAT   = 0.85  # 1.0 = 不动饱和；越小越灰
$K_CRACK   = 0.45  # 裂纹像素在「已压暗」基础上的再乘系数 -> 约为原亮度 0.28
$MAX_CRACK = 8     # 裂纹最多几条
$MAX_CLEN  = 8     # 单条裂纹最多几格
$MAX_CHIP  = 6     # 崩口最多几格

# ── 工具函数 ──────────────────────────────────────────────────────────────────
function Get-StableSeed([string]$s) {
    # FNV-1a；用部件路径做种子 => 同一部件的所有材质变体拿到**完全相同**的损伤图案
    [long]$h = 2166136261
    foreach ($ch in $s.ToCharArray()) {
        $h = $h -bxor [long][char]$ch
        $h = ($h * 16777619) -band 0xFFFFFFFFL
    }
    return [int]($h % 2000000000L)
}

function Test-Opaque([System.Drawing.Bitmap]$bmp, [int]$x, [int]$y) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $bmp.Width -or $y -ge $bmp.Height) { return $false }
    return ($bmp.GetPixel($x, $y).A -gt 0)
}

# 在**基贴图的第 0 帧**上算出一套损伤图案（裂纹格 + 崩口格）
function New-DamagePattern([System.Drawing.Bitmap]$bmp, [int]$frameH, [int]$seed) {
    $w = $bmp.Width
    $ox = New-Object 'System.Collections.Generic.List[int]'   # 不透明像素坐标，扁平存放 x,y
    $opaqueSet = New-Object 'System.Collections.Generic.HashSet[string]'
    [double]$cx = 0; [double]$cy = 0
    for ($y = 0; $y -lt $frameH; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            if ($bmp.GetPixel($x, $y).A -gt 0) {
                $ox.Add($x); $ox.Add($y)
                [void]$opaqueSet.Add("$x,$y")
                $cx += $x; $cy += $y
            }
        }
    }
    $n = [int]($ox.Count / 2)
    if ($n -eq 0) { return $null }
    $cx /= $n; $cy /= $n

    $dirs4 = @()
    $dirs4 += , @(-1, 0); $dirs4 += , @(1, 0); $dirs4 += , @(0, -1); $dirs4 += , @(0, 1)
    $dirs8 = @()
    foreach ($dx in @(-1, 0, 1)) {
        foreach ($dy in @(-1, 0, 1)) {
            if ($dx -ne 0 -or $dy -ne 0) { $dirs8 += , @($dx, $dy) }
        }
    }

    $rng = New-Object System.Random($seed)

    # b) 裂纹：1px 宽折线
    $crackCount = [Math]::Min($MAX_CRACK, [Math]::Max(1, [int][Math]::Round($n / 40.0)))
    $crackLen   = [Math]::Min($MAX_CLEN, [Math]::Max(2, [int][Math]::Round($n / 18.0)))
    $crack = New-Object 'System.Collections.Generic.HashSet[string]'
    for ($c = 0; $c -lt $crackCount; $c++) {
        $i = $rng.Next($n) * 2
        $x = $ox[$i]; $y = $ox[$i + 1]
        $di = $rng.Next($dirs8.Count)
        for ($s = 0; $s -lt $crackLen; $s++) {
            if ($opaqueSet.Contains("$x,$y")) { [void]$crack.Add("$x,$y") }
            if ($rng.Next(100) -lt 45) {                       # 折向 ±45°，避免看起来是直尺画的
                $di = ($di + $(if ($rng.Next(2) -eq 0) { 1 } else { 7 })) % $dirs8.Count
            }
            $x += $dirs8[$di][0]; $y += $dirs8[$di][1]
            if ($x -lt 0 -or $y -lt 0 -or $x -ge $w -or $y -ge $frameH) { break }
        }
    }

    # c) 崩口：有透明邻居、且至少 2 个不透明 4-邻居的边界像素，取离中心最远的几个
    $chip = New-Object 'System.Collections.Generic.HashSet[string]'
    $chipCount = 0
    if ($n -ge 40) { $chipCount = [Math]::Min($MAX_CHIP, [int][Math]::Floor($n * 0.05)) }
    if ($chipCount -gt 0) {
        $cand = New-Object 'System.Collections.Generic.List[object]'
        for ($k = 0; $k -lt $n; $k++) {
            $x = $ox[$k * 2]; $y = $ox[$k * 2 + 1]
            $op = 0; $tr = 0
            foreach ($dd in $dirs4) {
                if (Test-Opaque $bmp ($x + $dd[0]) ($y + $dd[1])) { $op++ } else { $tr++ }
            }
            if ($tr -ge 1 -and $op -ge 2) {
                $dist = [Math]::Sqrt([Math]::Pow($x - $cx, 2) + [Math]::Pow($y - $cy, 2))
                $cand.Add([pscustomobject]@{ X = $x; Y = $y; D = $dist })
            }
        }
        foreach ($c in ($cand | Sort-Object -Property D -Descending | Select-Object -First $chipCount)) {
            [void]$chip.Add("$($c.X),$($c.Y)")
            # 连通性守卫：啃掉这块之后不透明区必须还是一整块（4-连通），
            # 否则这块崩口会让 1~2px 宽的刀身/手柄断成"浮空的两截"，看起来像贴图坏了
            if (-not (Test-Connected $opaqueSet $chip $w $frameH)) { [void]$chip.Remove("$($c.X),$($c.Y)") }
        }
    }

    return [pscustomobject]@{ Crack = $crack; Chip = $chip; N = $n; W = $w; FrameH = $frameH }
}

function Get-FrameCount([string]$pngPath) {
    # 动图判定：同目录存在 <png>.mcmeta => 竖排帧，帧高 = 宽（原版默认）
    $mc = $pngPath + '.mcmeta'
    if (-not (Test-Path $mc)) { return 1 }
    $bmp = New-Object System.Drawing.Bitmap -ArgumentList $pngPath
    $w = $bmp.Width; $h = $bmp.Height
    $bmp.Dispose()
    if ($h % $w -eq 0 -and $h -gt $w) { return [int]($h / $w) }
    Write-Warning "动图尺寸异常，按单帧处理: $pngPath (${w}x${h})"
    return 1
}

function Test-Connected([System.Collections.Generic.HashSet[string]]$opaqueSet,
                        [System.Collections.Generic.HashSet[string]]$removed,
                        [int]$w, [int]$frameH) {
    # 把 $removed 从 $opaqueSet 抠掉后，剩余像素是否 4-连通成一整块
    $remain = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($k in $opaqueSet) { if (-not $removed.Contains($k)) { [void]$remain.Add($k) } }
    if ($remain.Count -le 1) { return $true }
    $start = $null
    foreach ($k in $remain) { $start = $k; break }
    $stack = New-Object 'System.Collections.Generic.Stack[string]'
    $seen = New-Object 'System.Collections.Generic.HashSet[string]'
    $stack.Push($start); [void]$seen.Add($start)
    $dxy = @()
    $dxy += , @(-1, 0); $dxy += , @(1, 0); $dxy += , @(0, -1); $dxy += , @(0, 1)
    while ($stack.Count -gt 0) {
        $cur = $stack.Pop()
        $parts = $cur.Split(',')
        $x = [int]$parts[0]; $y = [int]$parts[1]
        foreach ($d in $dxy) {
            $nx = $x + $d[0]; $ny = $y + $d[1]
            if ($nx -lt 0 -or $ny -lt 0 -or $nx -ge $w -or $ny -ge $frameH) { continue }
            $nk = "$nx,$ny"
            if ($remain.Contains($nk) -and -not $seen.Contains($nk)) { [void]$seen.Add($nk); $stack.Push($nk) }
        }
    }
    return ($seen.Count -eq $remain.Count)
}

# ── 1. 找出所有匠魂工具模型，收集「部件贴图基名」 ───────────────────────────────
$targets = New-Object 'System.Collections.Generic.List[object]'
$seen = New-Object 'System.Collections.Generic.HashSet[string]'

function Add-Target([string]$relPath) {
    # $relPath 形如 item/tool/war_scythe/head1（已去掉命名空间），指向 textures\<relPath>.png
    $dir  = Split-Path $relPath -Parent
    $base = Split-Path $relPath -Leaf
    $key  = "$dir/$base"
    if ($seen.Add($key)) {
        $targets.Add([pscustomobject]@{ Dir = $dir; Base = $base; Key = $key })
    }
}

$toolModelNames = New-Object 'System.Collections.Generic.List[string]'
foreach ($f in Get-ChildItem $modelDir -File -Filter *.json) {
    $raw = [System.IO.File]::ReadAllText($f.FullName)
    try { $json = $raw | ConvertFrom-Json } catch { Write-Warning "跳过无法解析的 JSON: $($f.Name)"; continue }
    if ($json.loader -ne 'tconstruct:tool') { continue }
    $toolModelNames.Add($f.BaseName)

    $partNames = @()
    if ($json.parts) { $partNames = @($json.parts | ForEach-Object { $_.name }) }

    foreach ($prop in $json.textures.PSObject.Properties) {
        $key = $prop.Name
        if ($key -like 'large_*') { continue }                       # 大工具贴图在 large/ 子目录，本模组没有
        if ($partNames.Count -gt 0 -and ($partNames -notcontains $key)) { continue }
        $rel = [string]$prop.Value
        if ($rel -match ':') { $rel = $rel.Split(':', 2)[1] }         # 去掉命名空间
        Add-Target $rel
    }
}

# 非 tconstruct:tool 加载器、但物品本身是匠魂工具的（模型是普通 layer0 贴图）：
# durandal_sword（DurandalSwordItem extends ModifiableItem，会 tic_broken，但图标是普通贴图）
$plainTargets = @(
    @{ Dir = 'item'; Base = 'durandal_sword' }
)
foreach ($t in $plainTargets) { Add-Target ("{0}/{1}" -f $t.Dir, $t.Base) }

Write-Host ("匠魂工具模型 {0} 个：{1}" -f $toolModelNames.Count, ($toolModelNames -join ', '))
Write-Host ("待处理部件贴图基名 {0} 个" -f $targets.Count)

# ── 2. 逐个基名生成 ───────────────────────────────────────────────────────────
$stat = [ordered]@{ Made = 0; Skipped = 0; Missing = 0; Mcmeta = 0 }
$report = New-Object 'System.Collections.Generic.List[string]'

foreach ($t in $targets) {
    $dirFs   = Join-Path $texRoot ($t.Dir -replace '/', '\')
    $basePng = Join-Path $dirFs ($t.Base + '.png')
    if (-not (Test-Path $basePng)) {
        Write-Warning "缺少基贴图（跳过）: $($t.Dir)/$($t.Base).png"
        $stat.Missing++
        continue
    }
    if ($Filter -and ($t.Key -notmatch $Filter)) { continue }

    # 收集该基名的所有材质变体（排除已生成的 _broken）
    $sources = @(Get-ChildItem $dirFs -File -Filter ($t.Base + '*.png') |
                 Where-Object { $_.BaseName -eq $t.Base -or $_.BaseName.StartsWith($t.Base + '_') } |
                 Where-Object { $_.BaseName -notmatch '_broken' } |
                 Sort-Object Name)
    if ($sources.Count -eq 0) { continue }

    # ① 用基贴图算一次损伤图案 —— 同部件的所有材质变体共用（铁的破处 = 金的破处）
    $baseBmp = New-Object System.Drawing.Bitmap -ArgumentList $basePng
    $baseFrames = Get-FrameCount $basePng
    $pattern = New-DamagePattern $baseBmp ([int]($baseBmp.Height / $baseFrames)) (Get-StableSeed $t.Key)
    $baseBmp.Dispose()
    if ($null -eq $pattern) { Write-Warning "空基贴图（跳过）: $($t.Dir)/$($t.Base).png"; continue }

    # ② 逐个变体上色
    foreach ($src in $sources) {
        $suffix  = $src.BaseName.Substring($t.Base.Length)            # '' 或 '_tconstruct_iron'
        $outName = $t.Base + '_broken' + $suffix + '.png'
        $outPath = Join-Path $dirFs $outName
        if ((Test-Path $outPath) -and -not $Force) { $stat.Skipped++; continue }

        $srcBmp = New-Object System.Drawing.Bitmap -ArgumentList $src.FullName
        $w = $srcBmp.Width; $h = $srcBmp.Height
        $mcmetaPath = $src.FullName + '.mcmeta'
        $frames = Get-FrameCount $src.FullName
        $frameH = [int]($h / $frames)

        $outBmp = New-Object System.Drawing.Bitmap -ArgumentList $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        for ($f = 0; $f -lt $frames; $f++) {
            $off = $f * $frameH
            for ($y = 0; $y -lt $frameH; $y++) {
                for ($x = 0; $x -lt $w; $x++) {
                    $p = $srcBmp.GetPixel($x, $y + $off)
                    if ($p.A -eq 0) { continue }                       # 透明区一律留空
                    if ($x -lt $pattern.W -and $y -lt $pattern.FrameH -and $pattern.Chip.Contains("$x,$y")) {
                        continue                                           # 崩口：不写 => 该格 alpha=0
                    }
                    $lum = 0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B
                    $r = ($p.R * $K_DESAT + $lum * (1 - $K_DESAT)) * $K_DARK
                    $g = ($p.G * $K_DESAT + $lum * (1 - $K_DESAT)) * $K_DARK
                    $b = ($p.B * $K_DESAT + $lum * (1 - $K_DESAT)) * $K_DARK
                    if ($x -lt $pattern.W -and $y -lt $pattern.FrameH -and $pattern.Crack.Contains("$x,$y")) {
                        $r *= $K_CRACK; $g *= $K_CRACK; $b *= $K_CRACK
                    }
                    $outBmp.SetPixel($x, $y + $off, [System.Drawing.Color]::FromArgb(
                        $p.A,
                        [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($r))),
                        [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($g))),
                        [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($b)))))
                }
            }
        }

        if (-not $DryRun) {
            $outBmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
            if (Test-Path $mcmetaPath) {
                Copy-Item $mcmetaPath ($outPath + '.mcmeta') -Force
                $stat.Mcmeta++
            }
        }
        $outBmp.Dispose(); $srcBmp.Dispose()
        $stat.Made++
        $report.Add(("  {0}/{1}  {2}x{3}{4}  不透明 {5} 格 / 裂纹 {6} 格 / 崩口 {7} 格" -f `
            $t.Dir, $outName, $w, $h, $(if ($frames -gt 1) { " ($frames 帧)" } else { '' }), `
            $pattern.N, $pattern.Crack.Count, $pattern.Chip.Count))
    }
}

Write-Host ''
$report | ForEach-Object { Write-Host $_ }

# ── 3. 生成「破损模型」JSON（**新文件**：models/item/tool/<工具>/broken.json） ──────
# 匠魂的做法是：把基模型抄一份，只把 part 贴图名统一加 `_broken`，然后由
# `overrides` 里的 `tconstruct:broken` 谓词切过去。我们**不能改既有模型 JSON**
# （铁律：models/** 的既有文件一律不动），所以走 Java 侧接线
# （BrokenToolModels.java：ModelEvent.RegisterAdditional + ModifyBakingResult 包 overrides），
# 这里只负责产出这些**新增**的 broken.json。
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$modelMade = 0; $modelSkipped = 0; $modelTouched = New-Object 'System.Collections.Generic.List[string]'

function Write-ModelFile([string]$relDir, [string]$text) {
    $dir = Join-Path $modelDir ($relDir -replace '/', '\')
    $outPath = Join-Path $dir 'broken.json'
    if ((Test-Path $outPath) -and -not $Force) { $script:modelSkipped++; return }
    if (-not $DryRun) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        [System.IO.File]::WriteAllText($outPath, $text, $utf8NoBom)   # JSON 绝不能带 BOM（check-json 严格校验会报错）
    }
    $script:modelMade++
    $script:modelTouched.Add("models/item/$relDir/broken.json")
}

foreach ($f in Get-ChildItem $modelDir -File -Filter *.json) {
    $raw = [System.IO.File]::ReadAllText($f.FullName)
    try { $json = $raw | ConvertFrom-Json } catch { continue }
    if ($json.loader -ne 'tconstruct:tool') { continue }
    if ($raw -match '"overrides"') {
        # 基模型已经自己写了 overrides（例如将来手工加了拉弦/破损分支）：不猜、不覆盖，直接跳过
        Write-Warning "已有 overrides，跳过模型生成（请人工确认）: models/item/$($f.Name)"
        continue
    }
    $keys = @()
    if ($json.parts) { $keys = @($json.parts | ForEach-Object { $_.name }) }
    else { $keys = @($json.textures.PSObject.Properties | ForEach-Object { $_.Name }) }

    $out = $raw
    foreach ($k in $keys) {
        if ($k -like 'large_*') { continue }
        $re = New-Object System.Text.RegularExpressions.Regex(('("' + [regex]::Escape($k) + '"\s*:\s*")([^"]+)(")'))
        $new = $re.Replace($out, '$1$2_broken$3', 1)
        if ($new -eq $out) { Write-Warning "模型里没找到 part 贴图键 '$k'：models/item/$($f.Name)" }
        $out = $new
    }
    Write-ModelFile ('tool/' + $f.BaseName) $out
}

# durandal_sword：物品是匠魂工具（会 tic_broken），但模型是普通 layer0 手持图
Write-ModelFile 'tool/durandal_sword' @'
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "tinkersnewlife:item/durandal_sword_broken"
  }
}
'@

Write-Host ''
foreach ($m in $modelTouched) { Write-Host ("  " + $m) }
Write-Host ''
Write-Host ("破损贴图 生成 {0} / 跳过(已存在) {1} / 缺基贴图 {2} / 复制 mcmeta {3}" -f `
    $stat.Made, $stat.Skipped, $stat.Missing, $stat.Mcmeta)
Write-Host ("破损模型 生成 {0} / 跳过(已存在) {1}{2}" -f `
    $modelMade, $modelSkipped, $(if ($DryRun) { '  [DryRun：未写盘]' } else { '' }))
