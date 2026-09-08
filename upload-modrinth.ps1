# Modrinth 上传脚本 - Tinker's Newlife
# 用法:
#   $env:MODRINTH_TOKEN = "你的 Modrinth API Token"
#   $env:MODRINTH_PROJECT = "项目 slug 或 ID(Modrinth 项目页 URL 里的名字)"
#   powershell -ExecutionPolicy Bypass -File upload-modrinth.ps1
#
# 可覆盖参数: -Token xxx -Project xxx -Version 1.0.1 -Jar 路径 -Type release

param(
    [string]$Token = $env:MODRINTH_TOKEN,
    [string]$Project = $env:MODRINTH_PROJECT,
    [string]$Jar = "build\libs\tinkersnewlife-1.0.1.7.jar",
    [string]$Version = "1.0.1.7",
    [string]$Name = "Tinker's Newlife 1.0.1.7",
    [string]$Type = "release"
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrEmpty($Token)) {
    Write-Host "错误: 未提供 Modrinth API Token。请先设置环境变量 MODRINTH_TOKEN。" -ForegroundColor Red
    Write-Host "获取方式: https://modrinth.com/settings/feeds 或 https://modrinth.com/developers 创建 Token" -ForegroundColor Yellow
    exit 1
}
if ([string]::IsNullOrEmpty($Project)) {
    Write-Host "错误: 未提供项目 slug/ID。请先设置环境变量 MODRINTH_PROJECT。" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $Jar)) {
    Write-Host "错误: 找不到 jar 文件 $Jar" -ForegroundColor Red
    exit 1
}

$Changelog = Get-Content -Raw -Encoding UTF8 -Path "CHANGELOG-1.0.1.7-en.md"

$Dependencies = '[{"project_id":"tconstruct","dependency_type":"required"},{"project_id":"mantle","dependency_type":"required"},{"project_id":"curios","dependency_type":"optional"}]'
$GameVersions = '["1.20.1"]'
$Loaders = '["forge"]'

Write-Host "上传中: $Jar -> project $Project (version $Version, $Type)" -ForegroundColor Cyan

$response = & curl.exe -sS -X POST "https://api.modrinth.com/v2/project/$Project/version" `
    -H "Authorization: Bearer $Token" `
    -F "name=$Name" `
    -F "version_number=$Version" `
    -F "changelog=$Changelog" `
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
