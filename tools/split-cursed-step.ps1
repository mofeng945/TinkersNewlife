<#
.SYNOPSIS
  §1265 三模拆分：把"这一步安全可搬"的咒术类从 common/ 搬进 cursed/，并核对 jar。

.DESCRIPTION
  每一步都做这五件事（少一件都会出事）：
    ① 不动点依赖分析 —— 谁被任何"留在 common 的类"引用 ⇒ 谁这一步不能搬；
    ② 补 import —— 同包但留在 common 的类，搬走后必须 import；
    ③ 搬文件（★包名不变 ⇒ 全仓 import 一处都不用改）；
    ④ 构建 + ★★ 核对 cursed 的 jar 里到底有没有这些类（编译绿 ≠ 类进包）；
    ⑤ 只在核对通过后才部署（-AllJars，★共享层与内容分居两包，必须都发）。

.NOTES
  ⚠ 为什么必须"核对 jar"：§1264 —— 我加过一条 exclude 把自家类也排掉，
    而 BUILD SUCCESSFUL 照样绿 ✗ 只有看 jar 才不会漏。
#>
param(
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

$javaRoot = 'common\src\main\java'
$destRoot = 'cursed\src\main\java'

function IsCursed([string]$rel) {
    return ($rel -match 'curse|FumoMo|Momo|Shikigami|Domain|WuWei|Puppet|BlackBird|TenShadows|JacobLadder|Yuchuzi|SpiritVortex|Gourd')
}

$all = Get-ChildItem $javaRoot -Recurse -File -Filter '*.java'
$cand = @(); $other = @()
foreach ($f in $all) {
    $rel = $f.FullName.Replace($repo + '\' + $javaRoot + '\', '')
    if (IsCursed $rel) { $cand += $f } else { $other += $f }
}
$ot = ''
foreach ($f in $other) { $ot += [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8) }

$text = @{}; $name = @{}
foreach ($f in $cand) {
    $text[$f.FullName] = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $name[$f.FullName] = $f.BaseName
}

$movers = @{}
foreach ($k in $text.Keys) { $movers[$k] = $true }
while ($true) {
    $removed = 0
    foreach ($k in @($movers.Keys)) {
        $nm = $name[$k]
        if ($ot.Contains($nm)) { $movers.Remove($k); $removed++; continue }
        $blocked = $false
        foreach ($k2 in $text.Keys) {
            if ($k2 -eq $k -or $movers.ContainsKey($k2)) { continue }
            if ($text[$k2].Contains($nm)) { $blocked = $true; break }
        }
        if ($blocked) { $movers.Remove($k); $removed++ }
    }
    if ($removed -eq 0) { break }
}

Write-Host ("候选 {0} 个 ⇒ 这一步安全可搬 {1} 个" -f $cand.Count, $movers.Count)
if ($movers.Count -eq 0) { Write-Host '⇒ 没有可搬的了：剩下的全被 common 引用，得先切中枢'; exit 0 }
if ($DryRun) { $movers.Keys | ForEach-Object { '  ' + $name[$_] } | Sort-Object; exit 0 }

$plan = @()
foreach ($k in @($movers.Keys)) {
    $f = Get-Item -LiteralPath $k
    $rel = $f.FullName.Replace($repo + '\' + $javaRoot + '\', '')
    $pkgDir = Split-Path $f.FullName -Parent
    $src = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $pkg = ''
    if ($src -match '(?m)^\s*package\s+([\w\.]+)\s*;') { $pkg = $Matches[1] }
    $adds = @()
    foreach ($s in (Get-ChildItem $pkgDir -File -Filter '*.java' |
            Where-Object { $_.BaseName -ne $f.BaseName -and -not $movers.ContainsKey($_.FullName) })) {
        if ($src -match ('\b' + [regex]::Escape($s.BaseName) + '\b')) {
            $imp = 'import ' + $pkg + '.' + $s.BaseName + ';'
            if ($src -notmatch [regex]::Escape($imp)) { $adds += $imp }
        }
    }
    if ($adds.Count -gt 0) {
        $src = [regex]::Replace($src, '(?m)^(\s*package\s+[\w\.]+\s*;)', ('$1' + "`n" + ($adds -join "`n")), 1)
        [System.IO.File]::WriteAllText($f.FullName, $src, (New-Object System.Text.UTF8Encoding($false)))
    }
    $plan += [pscustomobject]@{ from = $f.FullName; to = (Join-Path $destRoot $rel); name = $f.BaseName; imports = $adds.Count }
}
foreach ($p in $plan) {
    New-Item -ItemType Directory -Force -Path (Split-Path $p.to -Parent) | Out-Null
    Move-Item -LiteralPath $p.from -Destination $p.to -Force
    Write-Host ('  搬 ' + $p.name.PadRight(32) + ' 补 import ' + $p.imports)
}

Write-Host '=== 构建 ==='
cmd /c ".\gradlew.bat build --console=plain > tools\_tmp_step.txt 2>&1"
$B = [System.IO.File]::ReadAllLines((Resolve-Path 'tools\_tmp_step.txt').Path, [System.Text.Encoding]::GetEncoding(936))
$ok = @($B | Where-Object { $_ -match 'BUILD SUCCESSFUL' }).Count -gt 0
Remove-Item tools\_tmp_step.txt -Force -ErrorAction SilentlyContinue
if (-not $ok) {
    Write-Host '⚠ 构建失败 ⇒ 原路退回 ✓'
    foreach ($p in $plan) {
        New-Item -ItemType Directory -Force -Path (Split-Path $p.from -Parent) | Out-Null
        if (Test-Path -LiteralPath $p.to) { Move-Item -LiteralPath $p.to -Destination $p.from -Force }
    }
    cmd /c ".\gradlew.bat build --console=plain > tools\_tmp_step2.txt 2>&1" | Out-Null
    $B2 = [System.IO.File]::ReadAllLines((Resolve-Path 'tools\_tmp_step2.txt').Path, [System.Text.Encoding]::GetEncoding(936))
    Write-Host ('  退回后 BUILD SUCCESSFUL = ' + (@($B2 | Where-Object { $_ -match 'BUILD SUCCESSFUL' }).Count -gt 0))
    Remove-Item tools\_tmp_step2.txt -Force -ErrorAction SilentlyContinue
    exit 1
}

# ★★ 核对 jar：编译绿不算数，类必须在包里（§1264 的教训）
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = (Resolve-Path 'cursed\build\libs\tinkersnewlife_cursed-1.0.1.20.jar').Path
$z = [System.IO.Compression.ZipFile]::OpenRead($jar)
$classEntries = @($z.Entries | Where-Object { $_.FullName -match '\.class$' })
$z.Dispose()
$missing = @()
foreach ($p in $plan) {
    $want = ($p.to.Replace($repo + '\' + $destRoot + '\', '')).Replace('\', '/').Replace('.java', '.class')
    if (-not ($classEntries | Where-Object { $_.FullName -eq $want })) { $missing += $want }
}
Write-Host ("cursed jar = {0} 字节 ✗ .class {1} 个 ✗ 本步缺 {2} 个" -f (Get-Item $jar).Length, $classEntries.Count, $missing.Count)
if ($missing.Count -gt 0) {
    $missing | Select-Object -First 10 | ForEach-Object { '  ✗ 缺 ' + $_ }
    Write-Host '⚠ 类没进包 ⇒ 退回 ✓'
    foreach ($p in $plan) {
        New-Item -ItemType Directory -Force -Path (Split-Path $p.from -Parent) | Out-Null
        if (Test-Path -LiteralPath $p.to) { Move-Item -LiteralPath $p.to -Destination $p.from -Force }
    }
    exit 1
}
Write-Host '  ✓ 类全部在包里 ✓'

Write-Host '=== 部署（★共享层与内容分居两包 ⇒ 必须 -AllJars ✓）==='
powershell -NoProfile -ExecutionPolicy Bypass -File tools\deploy.ps1 -IncludeNL -AllJars 2>&1 | Select-Object -Last 5
Write-Host '=== 完成（★注意：⭐ 记得在备忘录追加一节 ✗ 与本脚本的这一步对应 ✓）==='
