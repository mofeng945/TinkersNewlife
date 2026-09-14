# 部署构建产物到两个测试实例。
# ⚠ 放在 tools/ 而不是 build/：build/ 是 Gradle 的输出目录，`gradlew build` 会把它清掉
#   （2026-09-13 实测：build/deploy.ps1 就是这么没的）。
# ⚠ 同时硬拦截"游戏运行中覆盖 jar"：本会话曾因此让客户端崩在
#   NoClassDefFoundError（热替换 jar 后类加载失败）。
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

$procs = @()
$procs += Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Select-Object ProcessId, CommandLine
$procs += Get-CimInstance Win32_Process -Filter "Name='javaw.exe'" | Select-Object ProcessId, CommandLine
$running = $procs | Where-Object { $_.CommandLine -and $_.CommandLine -match 'minecraft|forge|ModLauncher' }
if ($running) {
    Write-Host '拒绝部署：检测到 Minecraft/Forge 进程正在运行。'
    $running | ForEach-Object { Write-Host ("  pid=" + $_.ProcessId) }
    Write-Host '（覆盖运行中的 jar 会造成 NoClassDefFoundError 崩溃，请先关游戏）'
    exit 2
}

$targets = @(
    'G:\tex\.minecraft\versions\1.20.1-Forge_47.4.22\mods',
    'G:\tex\.minecraft\versions\[NL]NewLifestyle崭新世界 V0.1.7\mods'
)

foreach ($t in $targets) {
    if (-not (Test-Path -LiteralPath $t)) {
        Write-Host ("跳过（目录不存在）：" + $t)
        continue
    }
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
    if ($item.Length -ne $jar.Length) {
        Write-Host ("失败：字节数不一致 " + $dest)
        exit 3
    }
    Write-Host ("OK  {0} ({1} 字节)  {2}" -f $dest, $item.Length, $item.LastWriteTime)
}

Write-Host '部署完成'
