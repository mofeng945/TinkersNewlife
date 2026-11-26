# Deploy the current root-project JAR. Include NL only with -IncludeNL.
param([switch]$IncludeNL)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$versionLine = Select-String -LiteralPath (Join-Path $repo 'gradle.properties') -Pattern '^mod_version=(.+)$'
if (@($versionLine).Count -ne 1) { throw 'Cannot resolve mod_version' }
$version = $versionLine.Matches[0].Groups[1].Value.Trim()
$jars = @(Get-Item -LiteralPath (Join-Path $repo "build\libs\tinkersnewlife-$version.jar"))

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