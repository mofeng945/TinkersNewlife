# Modrinth 上传脚本 - Tinker's Newlife
# 用法:
#   $env:MODRINTH_TOKEN = "你的 Modrinth API Token"
#   $env:MODRINTH_PROJECT = "项目 slug 或 ID(Modrinth 项目页 URL 里的名字)"
#   powershell -ExecutionPolicy Bypass -File upload-modrinth.ps1
#
# 可覆盖参数: -Token xxx -Project xxx -Version 0.1.9.2 -Jar 路径 -Type beta

param(
    [string]$Token = $env:MODRINTH_TOKEN,
    [string]$Project = $env:MODRINTH_PROJECT,
    [string]$Jar = "build\libs\tinkersnewlife-0.1.9.2.jar",
    [string]$Version = "0.1.9.2",
    [string]$Name = "Tinker's Newlife 0.1.9.2",
    [string]$Type = "beta"
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

$Changelog = @"
## 0.1.9.2 (beta)

### 修复
- 修复飞剑死亡复活后状态错乱:不再自动生成脚下飞剑、不再自动消耗耐久,飞行能力可正常重新启用
- 修复量子背包按键打开时界面闪烁(按住按键不再重复打开,局域网模式同样稳定)
- 修复飞剑耐久消耗绕过匠魂正常逻辑:发射与飞行消耗改走 ToolDamageUtil(粘液覆层等耐久保护正常生效),命中返还基于名义消耗 20
"@

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
