<#
.SYNOPSIS
  把四个子项目（common / cursed / imagination / apostle）build\libs 下的 jar 部署到实例。
.PARAMETER IncludeNL
  额外写入 [NL]NewLifestyle 实例。**默认不写**。
.NOTES
  §1255 三模拆分：产物从"根 build\libs 一个"改成"四个子项目各一个"。
  ⚠ 本文件含中文 ⇒ **必须 UTF-8 带 BOM 保存**（PS 5.1 否则按 ANSI 读会语法错）。
#>
param(
    [switch]$IncludeNL
)
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot

# ⭐ §1255 四处产物（⭐ 顺序固定 ✗ ⭐ 便于日志核对 ✓）
$modDirs = @('common', 'cursed', 'imagination', 'apostle')
$jars = @()
foreach ($d in $modDirs) {
    $libs = Join-Path $repo ($d + '\build\libs')
    if (-not (Test-Path -LiteralPath $libs)) { continue }
    $jars += Get-ChildItem -LiteralPath $libs -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' }
}
if ($jars.Count -eq 0) {
    Write-Host 'ERROR: 四个子项目的 build\libs 下都没有 jar，先跑 gradlew build'
    exit 1
}
Write-Host ("构建产物 {0} 个：" -f $jars.Count)
foreach ($j in $jars) { Write-Host ("  {0} ({1} 字节)" -f $j.Name, $j.Length) }

# ⚠ 判据按**命令行**匹配：早期写成"任何 java.exe 在跑就拒绝"会误判我自己的 gradle 守护进程。
$procs = @()
$procs += Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Select-Object ProcessId, CommandLine
$procs += Get-CimInstance Win32_Process -Filter "Name='javaw.exe'" -ErrorAction SilentlyContinue |
    Select-Object ProcessId, CommandLine
function Get-RunningInstancePid([string]$instanceDir) {
    foreach ($p in $procs) {
        if ($p.CommandLine -and
            $p.CommandLine.IndexOf($instanceDir, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
            return $p.ProcessId
        }
    }
    return $null
}

$bases = @(
    'G:\tex\.minecraft\versions',
    'D:\tex\.minecraft\versions',
    'E:\minecraft\versions'
) | Where-Object { Test-Path -LiteralPath $_ }

if ($bases.Count -eq 0) {
    Write-Host 'ERROR: 一个实例根都没找到，请把实例所在磁盘加进脚本里的 $bases'
    exit 4
}

$pattern = if ($IncludeNL) { @('1.20.1-Forge*', '[[]NL]*') } else { @('1.20.1-Forge*') }
if (-not $IncludeNL) {
    Write-Host '（默认只写测试包；要把 [NL] 实例也更新，加 -IncludeNL）'
}

$targets = @()
foreach ($b in $bases) {
    foreach ($p in $pattern) {
        $targets += Get-ChildItem -LiteralPath $b -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like $p } |
            ForEach-Object { Join-Path $_.FullName 'mods' }
    }
}
$targets = $targets | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -Unique

if ($targets.Count -eq 0) {
    Write-Host 'ERROR: 找到实例根，但没有匹配的实例/mods 目录'
    exit 5
}

$skipped = 0
foreach ($t in $targets) {
    $instanceDir = Split-Path -Parent $t
    $pid2 = Get-RunningInstancePid $instanceDir
    if ($pid2) {
        Write-Host ("跳过（该实例正在运行，pid={0}）：{1}" -f $pid2, $t)
        $skipped++
        continue
    }
    foreach ($jar in $jars) {
        # ⚠ 版本号一变文件名就变：先删同名的旧版本 jar，否则 mods 里两个版本 ⇒ Duplicate mod 加载失败
        $base = $jar.Name -replace '-[0-9][^-]*\.jar$', ''
        Get-ChildItem -LiteralPath $t -Filter ($base + '-*.jar') -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -ne $jar.Name } |
            ForEach-Object {
                Remove-Item -LiteralPath $_.FullName -Force
                Write-Host ("  已删除旧版本：" + $_.Name)
            }
        $dest = Join-Path $t $jar.Name
        Copy-Item -LiteralPath $jar.FullName -Destination $dest -Force
        $item = Get-Item -LiteralPath $dest
        $srcHash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm MD5).Hash
        $destHash = (Get-FileHash -LiteralPath $dest -Algorithm MD5).Hash
        if ($item.Length -ne $jar.Length -or $destHash -ne $srcHash) {
            Write-Host ("失败：字节数或 MD5 不一致 " + $dest)
            exit 3
        }
        Write-Host ("OK  {0} ({1} 字节 / MD5 {2})" -f $dest, $item.Length, $destHash.Substring(0, 12))
    }
}

if ($skipped -gt 0) {
    Write-Host ("⚠ 有 {0} 个目标被跳过（该实例正在运行）⇒ 关掉后重跑本脚本即可补齐" -f $skipped)
}

Write-Host '部署完成'