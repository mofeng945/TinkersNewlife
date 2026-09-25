<#
.SYNOPSIS
  把 build\libs 下最新的 jar 部署到测试实例。
.PARAMETER IncludeNL
  额外写入 [NL]NewLifestyle 实例。**默认不写** —— 会话口径一直是"只装测试包、NL 包不动"
  （见备忘录反复记录），所以这里要显式加开关才会碰它。
#>
param(
    [switch]$IncludeNL
)
# ⚠ 放在 tools/ 而不是 build/：build/ 是 Gradle 的输出目录，`gradlew build` 会把它清掉
#   （2026-09-13 实测：build/deploy.ps1 就是这么没的）。
# ⚠ 同时硬拦截"游戏运行中覆盖 jar"：本会话曾因此让客户端崩在
#   NoClassDefFoundError（热替换 jar 后类加载失败）。
# ⚠ 2026-09-25：实例根目录**不再写死 G:** —— 本机没有 G 盘（实例在 D:\tex，
#   另有 C:\_G_backup_20260922 冷备）。改成多候选搜索，找不到就明确报出来。
#   同时**默认只写测试包**（NL 包要 -IncludeNL 才写），冷备目录永不写入。
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$libs = Join-Path $repo 'build\libs'
$jar = Get-ChildItem -LiteralPath $libs -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    Write-Host 'ERROR: build\libs 下没有 jar，先跑 gradlew build'
    exit 1
}
Write-Host ("构建产物：{0} ({1} 字节)" -f $jar.Name, $jar.Length)

# ⚠ 判据必须按**命令行**匹配：早期写成"任何 java.exe 在跑就拒绝"，
#   结果误判了我自己 gradle 守护进程 ⇒ 用户明明没开游戏却拒绝部署。
$procs = @()
$procs += Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Select-Object ProcessId, CommandLine
$procs += Get-CimInstance Win32_Process -Filter "Name='javaw.exe'" -ErrorAction SilentlyContinue |
    Select-Object ProcessId, CommandLine
$running = $procs | Where-Object {
    $_.CommandLine -and $_.CommandLine -match 'net\.minecraft\.client\.main\.Main|minecraft|ModLauncher'
}
if ($running) {
    Write-Host '拒绝部署：检测到 Minecraft/Forge 进程正在运行。'
    $running | ForEach-Object { Write-Host ("  pid=" + $_.ProcessId) }
    Write-Host '（覆盖运行中的 jar 会造成 NoClassDefFoundError 崩溃，请先关游戏）'
    exit 2
}

# 实例根：多候选搜索（按顺序取存在的；**冷备目录只用于"确认存在"，绝不写入**）
$bases = @(
    'G:\tex\.minecraft\versions',
    'D:\tex\.minecraft\versions',
    'E:\minecraft\versions'
) | Where-Object { Test-Path -LiteralPath $_ }

if ($bases.Count -eq 0) {
    Write-Host 'ERROR: 一个实例根都没找到，请把实例所在磁盘加进脚本里的 $bases：'
    Write-Host '  G:\tex\.minecraft\versions / D:\tex\.minecraft\versions / E:\minecraft\versions'
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

$srcHash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm MD5).Hash

foreach ($t in $targets) {
    # ⚠ 版本号一变，jar 文件名就变了：必须先把旧版本的 tinkersnewlife-*.jar 删掉，
    #   否则 mods 里会同时存在两个版本 → Forge 报 "Duplicate mod" 直接加载失败。
    Get-ChildItem -LiteralPath $t -Filter 'tinkersnewlife-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne $jar.Name } |
        ForEach-Object {
            Remove-Item -LiteralPath $_.FullName -Force
            Write-Host ("  已删除旧版本：" + $_.Name)
        }
    $dest = Join-Path $t $jar.Name
    Copy-Item -LiteralPath $jar.FullName -Destination $dest -Force
    $item = Get-Item -LiteralPath $dest
    $destHash = (Get-FileHash -LiteralPath $dest -Algorithm MD5).Hash
    if ($item.Length -ne $jar.Length -or $destHash -ne $srcHash) {
        Write-Host ("失败：字节数或 MD5 不一致 " + $dest)
        exit 3
    }
    Write-Host ("OK  {0} ({1} 字节 / MD5 {2})  {3}" -f $dest, $item.Length, $destHash, $item.LastWriteTime)
}

Write-Host '部署完成'