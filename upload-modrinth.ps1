# Modrinth 上传脚本 - Tinker's Newlife
# 用法:
#   $env:MODRINTH_TOKEN = "你的 Modrinth API Token"
#   $env:MODRINTH_PROJECT = "项目 slug 或 ID(Modrinth 项目页 URL 里的名字)"
#   powershell -ExecutionPolicy Bypass -File upload-modrinth.ps1
#
# 版本号 / jar / 更新说明文件都从仓库里自动推断（gradle.properties 是唯一事实来源），
# 不再需要每发一版就手改脚本。
# 可覆盖参数: -Token xxx -Project xxx -Version 1.0.1.12 -Jar 路径 -Changelog 路径 -Type release

param(
    [string]$Token = $env:MODRINTH_TOKEN,
    [string]$Project = $env:MODRINTH_PROJECT,
    [string]$Jar = "",
    [string]$Version = "",
    [string]$Name = "",
    [string]$Changelog = "",
    [string]$Type = "release"
)

$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot

# ---- 版本号：从 gradle.properties 的 mod_version 读 ----
if ([string]::IsNullOrEmpty($Version)) {
    $props = Join-Path $repo 'gradle.properties'
    if (-not (Test-Path $props)) {
        Write-Host "错误: 找不到 gradle.properties（无法推断版本号）" -ForegroundColor Red
        exit 1
    }
    $m = [regex]::Match((Get-Content -Raw -Encoding UTF8 -Path $props), '(?m)^\s*mod_version\s*=\s*(\S+)\s*$')
    if (-not $m.Success) {
        Write-Host "错误: gradle.properties 里找不到 mod_version" -ForegroundColor Red
        exit 1
    }
    $Version = $m.Groups[1].Value.Trim()
}

# ---- jar：优先精确匹配本版本，其次取 build\libs 下最新的 tinkersnewlife-*.jar ----
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

# ---- 名称 / 更新说明 ----
if ([string]::IsNullOrEmpty($Name)) { $Name = "Tinker's Newlife $Version" }
$ChangelogText = ""
if ([string]::IsNullOrEmpty($Changelog)) {
    $a = Join-Path $repo "CHANGELOG-$Version-en.md"
    $b = Join-Path $repo "CHANGELOG-$Version.md"
    if (Test-Path $a) { $Changelog = $a } elseif (Test-Path $b) { $Changelog = $b }
}
if ($Changelog -and (Test-Path $Changelog)) {
    $ChangelogText = Get-Content -Raw -Encoding UTF8 -Path $Changelog
    Write-Host ("更新说明: " + (Split-Path $Changelog -Leaf) + "（" + $ChangelogText.Length + " 字符）") -ForegroundColor Cyan
} else {
    Write-Host "警告: 没找到本版本的 CHANGELOG-<版本>-en.md，更新说明将为空" -ForegroundColor Yellow
}

if ([string]::IsNullOrEmpty($Token)) {
    Write-Host "错误: 未提供 Modrinth API Token。请先设置环境变量 MODRINTH_TOKEN。" -ForegroundColor Red
    Write-Host "获取方式: https://modrinth.com/settings/feeds 或 https://modrinth.com/developers 创建 Token" -ForegroundColor Yellow
    exit 1
}
if ([string]::IsNullOrEmpty($Project)) {
    Write-Host "错误: 未提供项目 slug/ID。请先设置环境变量 MODRINTH_PROJECT。" -ForegroundColor Red
    exit 1
}
if (-not $Jar -or -not (Test-Path $Jar)) {
    Write-Host "错误: 找不到 jar 文件（先跑 gradlew build）: $Jar" -ForegroundColor Red
    exit 1
}

$Dependencies = '[{"project_id":"tconstruct","dependency_type":"required"},{"project_id":"mantle","dependency_type":"required"},{"project_id":"curios","dependency_type":"optional"}]'
$GameVersions = '["1.20.1"]'
$Loaders = '["forge"]'

Write-Host "上传中: $Jar -> project $Project (version $Version, $Type)" -ForegroundColor Cyan

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

$response | Out-File -FilePath upload-response.json -Encoding UTF8

# 尝试解析响应判断是否成功
try {
    $obj = $response | ConvertFrom-Json
    if ($obj.id) {
        Write-Host "上传成功!版本 ID: $($obj.id)" -ForegroundColor Green
        Write-Host "版本页: https://modrinth.com/project/$Project/version/$($obj.id)" -ForegroundColor Green
    } else {
        Write-Host "响应异常(无版本 ID):" -ForegroundColor Yellow
        Write-Host $response
    }
} catch {
    Write-Host "上传失败,响应内容(已保存到 upload-response.json):" -ForegroundColor Red
    Write-Host $response
    exit 1
}
