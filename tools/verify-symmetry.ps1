# 校验：左右成对的方块在底图上是否**逐像素完全镜像对称** ✓
# 做法：对每一对（源 → 派生 ✓）与每个面，把"源方块对应面的矩形"横向翻一下，和"派生方块该面的矩形"逐像素比对 ✓
#   不一致数 = 0 才算真对称 ✓（这是对导入工具结果的独立复核 ✓，不复用它的代码路径 ✓）
param(
    [string]$Atlas = 'src\main\resources\assets\tinkersnewlife\textures\armor\wizard\grey.png',
    [string]$Pairs = 'body_plating_1>body_plating_2,left_arm_plating_5>right_arm_plating_8,left_arm_lace_6>right_arm_lace_9,left_arm_maille_7>right_arm_maille_10'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing

# 从 Java 读各方块的 w/h/d/u/v（法袍那 11 条 ✓）
$java = [System.IO.File]::ReadAllLines((Join-Path $root 'src\main\java\com\mofengbaizhi\tinkersnewlife\client\model\WizardArmorModel.java'), [System.Text.Encoding]::UTF8)
$ours = @{}
foreach ($l in $java) {
    $m = [regex]::Match($l, 'addLocalBox\(\w+, "([^"]+)",\s*[-\d.]+F,\s*[-\d.]+F,\s*[-\d.]+F,\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F,\s*(\d+),\s*(\d+)\)')
    if ($m.Success) {
        $ours[$m.Groups[1].Value] = @{ W = [double]$m.Groups[2].Value; H = [double]$m.Groups[3].Value; D = [double]$m.Groups[4].Value;
                                       U = [double]$m.Groups[5].Value; V = [double]$m.Groups[6].Value }
    }
}
function FaceRect($b, [string]$f) {
    $u = $b.U; $v = $b.V; $w = $b.W; $h = $b.H; $d = $b.D
    switch ($f) {
        'down'  { return @(($u + $d + $w), $v, $w, $d) }
        'up'    { return @(($u + $d), $v, $w, $d) }
        'west'  { return @($u, ($v + $d), $d, $h) }
        'north' { return @(($u + $d), ($v + $d), $w, $h) }
        'east'  { return @(($u + $d + $w), ($v + $d), $d, $h) }
        'south' { return @(($u + $d + $w + $d), ($v + $d), $w, $h) }
    }
}
$swap = @{ 'west' = 'east'; 'east' = 'west'; 'north' = 'north'; 'south' = 'south'; 'up' = 'up'; 'down' = 'down' }
$img = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromFile((Join-Path $root $Atlas)))
$bad = 0
foreach ($pair in ($Pairs -split ',')) {
    $ps = $pair -split '>'; $srcName = $ps[0].Trim(); $dstName = $ps[1].Trim()
    $sb = $ours[$srcName]; $db = $ours[$dstName]
    # 判据（连踩两次假报警才定下来 ✓）：
    #   ① 不能比"面矩形内所有像素" ✗ —— 同一块的相邻面在**分数边界**上共用一像素列/行（一像素劈不开 ✓），
    #      该像素归哪个面取决于写入顺序 ⇒ 会被误判成不对称 ✗；
    #   ② 也不能整块做简单横向镜像比对 ✗ —— 面在块内的排布（[down][up] / [west][north][east][south] ✓）
    #      **本身不是镜像对称的** ⇒ 整块镜像必然对不上 ✗。
    #   ⇒ 正确判据：**排除被 ≥2 个面覆盖的"共用像素"**，只比每个面的专属像素 ✓（那才是肉眼看到的图案 ✓）。
    #      共用像素会被相邻两个面同时采样，物理上不可能两侧都满足 ✓，与对称性无关 ✓。
    function OwnedCount($b) {
        $cnt = @{}
        foreach ($f in 'down', 'up', 'west', 'north', 'east', 'south') {
            $r = FaceRect $b $f
            for ($y = [Math]::Floor($r[1]); $y -lt [Math]::Ceiling($r[1] + $r[3]); $y++) {
                for ($x = [Math]::Floor($r[0]); $x -lt [Math]::Ceiling($r[0] + $r[2]); $x++) {
                    $k = "$x,$y"
                    if ($cnt.ContainsKey($k)) { $cnt[$k] = $cnt[$k] + 1 } else { $cnt[$k] = 1 }
                }
            }
        }
        return $cnt
    }
    $ownS = OwnedCount $sb
    $ownD = OwnedCount $db
    $mism = 0; $n = 0; $shared = 0
    foreach ($f in 'down', 'up', 'west', 'north', 'east', 'south') {
        $sr = FaceRect $sb $swap[$f]; $dr = FaceRect $db $f
        for ($y = [Math]::Floor($dr[1]); $y -lt [Math]::Ceiling($dr[1] + $dr[3]); $y++) {
            for ($x = [Math]::Floor($dr[0]); $x -lt [Math]::Ceiling($dr[0] + $dr[2]); $x++) {
                $tu = (($x + 0.5) - $dr[0]) / $dr[2]; $tv = (($y + 0.5) - $dr[1]) / $dr[3]
                $tu = [Math]::Max(0.0, [Math]::Min(1.0, $tu)); $tv = [Math]::Max(0.0, [Math]::Min(1.0, $tv))
                $sx = [int][Math]::Floor($sr[0] + (1 - $tu) * $sr[2]); $sy = [int][Math]::Floor($sr[1] + $tv * $sr[3])
                $sx = [Math]::Max([Math]::Floor($sr[0]), [Math]::Min([Math]::Ceiling($sr[0] + $sr[2]) - 1, $sx))
                $sy = [Math]::Max([Math]::Floor($sr[1]), [Math]::Min([Math]::Ceiling($sr[1] + $sr[3]) - 1, $sy))
                if ($ownD["$x,$y"] -gt 1 -or $ownS["$sx,$sy"] -gt 1) { $shared++; continue }
                $n++
                if ($img.GetPixel($x, $y).ToArgb() -ne $img.GetPixel($sx, $sy).ToArgb()) { $mism++ }
            }
        }
    }
    $mark = if ($mism -eq 0) { '✓' } else { '✗' }
    Write-Host ("  {0,-20} 专属像素 {1,4}  不一致 {2,3}  跳过共用 {3,4}  {4}" -f $srcName, $n, $mism, $shared, $mark)
    if ($mism -ne 0) { $bad++ }
}
$img.Dispose()
Write-Host ("不同步的块：$bad ⇒ " + $(if ($bad -eq 0) { '四对专属像素逐像素镜像对称 ✓✓（共用边界像素与对称无关 ✓）' } else { '仍有不对称 ✗' }))
