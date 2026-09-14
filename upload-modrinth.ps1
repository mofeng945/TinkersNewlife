# Modrinth 上传脚本 - Tinker's Newlife
#
# 最简单的用法（一次性配置好 Token 和项目 slug，以后一条命令发版）：
#   1) 在仓库根目录建一个 release.env（已在 .gitignore 里，不会被提交），内容两行：
#        MODRINTH_TOKEN=你的Token
#        MODRINTH_PROJECT=你的项目slug或ID
#      想临时换配置也可以直接用环境变量，或传参数 -Token xxx -Project xxx。
#   2) 平时发版只需要跑：
#        powershell -ExecutionPolicy Bypass -File upload-modrinth.ps1
#      （它会自动从 gradle.properties 取版本号、从 build\libs 取对应 jar、
#        从 CHANGELOG-<版本>-en.md 取更新说明）
#
# 想要"构建 + 部署到测试实例 + 上传"一条龙：跑 tools\release.ps1
#
# 常用参数：
#   -DryRun          只打印将要上传什么，不真的上传（不需要 Token）
#   -Type beta       版本类型（release / beta / alpha，默认 release）
#   -Version x.y.z   覆盖版本号     -Jar 路径   覆盖 jar
#   -Changelog 路径  覆盖更新说明   -OpenPage  成功后自动打开版本页

param(
    [string]$Token = $env:MODRINTH_TOKEN,
    [string]$Project = $env:MODRINTH_PROJECT,
    [string]$Jar = "",
    [string]$Version = "",
    [string]$Name = "",
    [string]$Changelog = "",
    [string]$Type = "release",
    [switch]$DryRun,
    [switch]$OpenPage
)

$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot
if ([string]::IsNullOrEmpty($repo)) { $repo = (Get-Location).Path }

# ---------- 本地配置：release.env（优先环境变量，其次这个文件） ----------
$envFile = Join-Path $repo 'release.env'
if (Test-Path $envFile) {
    foreach ($line in (Get-Content -Encoding UTF8 -Path $envFile)) {
        $s = $line.Trim()
        if ($s -eq '' -or $s.StartsWith('#')) { continue }
        $i = $s.IndexOf('=')
        if ($i -le 0) { continue }
        $k = $s.Substring(0, $i).Trim()
        $v = $s.Substring($i + 1).Trim().Trim('"')
        if ($k -eq 'MODRINTH_TOKEN' -and [string]::IsNullOrEmpty($Token)) { $Token = $v }
        if ($k -eq 'MODRINTH_PROJECT' -and [string]::IsNullOrEmpty($Project)) { $Project = $v }
    }
}

# ---------- 版本号：gradle.properties 的 mod_version ----------
if ([string]::IsNullOrEmpty($Version)) {
    $props = Join-Path $repo 'gradle.properties'
    if (-not (Test-Path $props)) { Write-Host "错误: 找不到 gradle.properties（无法推断版本号）" -ForegroundColor Red; exit 1 }
    $m = [regex]::Match((Get-Content -Raw -Encoding UTF8 -Path $props), '(?m)^\s*mod_version\s*=\s*(\S+)\s*$')
    if (-not $m.Success) { Write-Host "错误: gradle.properties 里找不到 mod_version" -ForegroundColor Red; exit 1 }
    $Version = $m.Groups[1].Value.Trim()
}

# ---------- jar：精确匹配本版本，否则取 build\libs 下最新的 ----------
if ([string]::IsNullOrEmpty($Jar)) {
    $libs = Join-Path $repo 'build\libs'
    $exact = Join-Path $libs "tinkersnewlife-$Version.jar"
    if (Test-Path $exact) {
        $Jar = $exact
    } else {
        $f = Get-ChildItem -LiteralPath $libs -Filter 'tinkersnewlife-*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'sources|javadoc' } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($f) { $Jar = $f.FullName }
    }
}
if (-not $Jar -or -not (Test-Path $Jar)) {
    Write-Host "错误: 找不到 jar（先跑 .\gradlew build）: $Jar" -ForegroundColor Red
    exit 1
}

# ---------- 名称 / 更新说明 ----------
if ([string]::IsNullOrEmpty($Name)) { $Name = "Tinker's Newlife $Version" }
$ChangelogText = ""
if ([string]::IsNullOrEmpty($Changelog)) {
    $a = Join-Path $repo "CHANGELOG-$Version-en.md"
    $b = Join-Path $repo "CHANGELOG-$Version.md"
    if (Test-Path $a) { $Changelog = $a } elseif (Test-Path $b) { $Changelog = $b }
}
if ($Changelog -and (Test-Path $Changelog)) {
    $ChangelogText = Get-Content -Raw -Encoding UTF8 -Path $Changelog
} else {
    Write-Host "警告: 没找到 CHANGELOG-$Version-en.md，更新说明将为空" -ForegroundColor Yellow
}

$jarSize = (Get-Item -LiteralPath $Jar).Length
Write-Host '----------------------------------------------' -ForegroundColor DarkGray
Write-Host ("版本号    : " + $Version) -ForegroundColor Cyan
Write-Host ("jar       : " + (Split-Path $Jar -Leaf) + "（" + $jarSize + " 字节）")
Write-Host ("版本名    : " + $Name)
if ($ChangelogText) {
    Write-Host ("更新说明  : " + (Split-Path $Changelog -Leaf) + "（" + $ChangelogText.Length + " 字符）")
} else {
    Write-Host "更新说明  : （空）"
}
Write-Host ("版本类型  : " + $Type)
if ($Project) { Write-Host ("项目      : " + $Project) } else { Write-Host "项目      : （未配置）" }
if ($Token) { Write-Host ("Token     : 已提供（" + $Token.Length + " 字符）") } else { Write-Host "Token     : （未配置）" }
Write-Host '----------------------------------------------' -ForegroundColor DarkGray

if ($DryRun) {
    Write-Host '预演模式（-DryRun）：以上就是要上传的内容，没有真的上传。' -ForegroundColor Yellow
    if ([string]::IsNullOrEmpty($Token)) { Write-Host '提示：真上传需要 Token —— 放到 release.env 的 MODRINTH_TOKEN 里即可。' -ForegroundColor Yellow }
    if ([string]::IsNullOrEmpty($Project)) { Write-Host '提示：真上传需要项目 slug —— 放到 release.env 的 MODRINTH_PROJECT 里即可。' -ForegroundColor Yellow }
    exit 0
}

if ([string]::IsNullOrEmpty($Token)) {
    Write-Host "错误: 未提供 Modrinth API Token。" -ForegroundColor Red
    Write-Host "  方式一：在仓库根目录建 release.env，写一行 MODRINTH_TOKEN=你的Token（推荐，已被 .gitignore 忽略）" -ForegroundColor Yellow
    Write-Host "  方式二：设置环境变量 MODRINTH_TOKEN，或传 -Token xxx" -ForegroundColor Yellow
    Write-Host "  获取：https://modrinth.com/settings/pats （需要 Versions 的 Create version 权限）" -ForegroundColor Yellow
    exit 1
}
if ([string]::IsNullOrEmpty($Project)) {
    Write-Host "错误: 未提供项目 slug/ID。" -ForegroundColor Red
    Write-Host "  在 release.env 里写 MODRINTH_PROJECT=你的项目slug（就是 modrinth.com/mod/<这一串>）" -ForegroundColor Yellow
    exit 1
}

$Dependencies = '[{"project_id":"tconstruct","dependency_type":"required"},{"project_id":"mantle","dependency_type":"required"},{"project_id":"curios","dependency_type":"optional"}]'
$GameVersions = '["1.20.1"]'
$Loaders = '["forge"]'

Write-Host "上传中…（文件 $jarSize 字节，请稍等）" -ForegroundColor Cyan

$response = & curl.exe -sS -X POST "https://api.modrinth.com/v2/project/$Project/version" `
    -H "Authorization: Bearer $Token" `
    -F "name=$Name" `
    -F "version_number=$Version" `
    -F "changelog=$ChangelogText" `
    -F "dependencies=$Dependencies" `
    -F "game_versions=$GameVersions" `
    -F "version_type=$Type" `
    -F "loaders=$Loaders" `
    -F "featured=false" `
    -F "status=listed" `
    -F "file=@$($Jar -replace '\\','/')" 2>&1

# 响应写到 build\ 下（build 已被 .gitignore 忽略，不会污染仓库）
$respFile = Join-Path $repo 'build\upload-response.json'
$respDir = Split-Path $respFile -Parent
if (-not (Test-Path $respDir)) { New-Item -ItemType Directory -Path $respDir -Force | Out-Null }
$response | Out-File -FilePath $respFile -Encoding UTF8

try {
    $obj = $response | ConvertFrom-Json
    if ($obj.id) {
        $url = "https://modrinth.com/project/$Project/version/$($obj.id)"
        Write-Host ("上传成功！版本 ID: " + $obj.id) -ForegroundColor Green
        Write-Host ("版本页: " + $url) -ForegroundColor Green
        if ($OpenPage) { Start-Process $url }
    } else {
        Write-Host "响应异常（无版本 ID）：" -ForegroundColor Yellow
        Write-Host $response
        exit 1
    }
} catch {
    Write-Host "上传失败，响应内容（已保存到 build\upload-response.json）：" -ForegroundColor Red
    Write-Host $response
    exit 1
}
