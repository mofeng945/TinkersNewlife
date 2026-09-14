# 一条命令发版：构建 -> 部署到本地测试实例 -> 上传 Modrinth
#
# 用法（在仓库根目录）：
#   powershell -ExecutionPolicy Bypass -File tools\release.ps1            # 全套：构建+部署+上传
#   powershell -ExecutionPolicy Bypass -File tools\release.ps1 -DryRun     # 只构建+部署，上传走预演
#   powershell -ExecutionPolicy Bypass -File tools\release.ps1 -SkipDeploy # 构建+上传（不碰测试实例）
#   powershell -ExecutionPolicy Bypass -File tools\release.ps1 -SkipUpload # 只构建+部署（本地测）
#
# 前置：Modrinth Token 与项目 slug 放在仓库根目录的 release.env（已被 .gitignore 忽略）：
#   MODRINTH_TOKEN=xxxxx
#   MODRINTH_PROJECT=你的项目slug
#
# 版本号改 gradle.properties 的 mod_version；更新说明写 CHANGELOG-<版本>-en.md —— 本脚本会自动用上。

param(
    [switch]$DryRun,
    [switch]$SkipDeploy,
    [switch]$SkipUpload,
    [switch]$SkipBuild,
    [string]$Type = "release"
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrEmpty($repo)) { $repo = (Get-Location).Path }
Set-Location $repo

function Step($n, $text) {
    Write-Host ''
    Write-Host ("==== [" + $n + "] " + $text + " ====") -ForegroundColor Cyan
}

# ---------- 1. 版本号 ----------
Step 1 '读取版本号'
$m = [regex]::Match((Get-Content -Raw -Encoding UTF8 -Path (Join-Path $repo 'gradle.properties')), '(?m)^\s*mod_version\s*=\s*(\S+)\s*$')
if (-not $m.Success) { Write-Host '错误: gradle.properties 里找不到 mod_version' -ForegroundColor Red; exit 1 }
$version = $m.Groups[1].Value.Trim()
Write-Host ("当前版本: " + $version) -ForegroundColor Green
$changelog = Join-Path $repo "CHANGELOG-$version-en.md"
if (Test-Path $changelog) {
    Write-Host ("更新说明: CHANGELOG-$version-en.md（" + (Get-Item $changelog).Length + " 字节）")
} else {
    Write-Host ("警告: 缺少 CHANGELOG-$version-en.md —— 上传的更新说明会是空的（建议先写）") -ForegroundColor Yellow
}

# ---------- 2. 构建 ----------
if (-not $SkipBuild) {
    Step 2 '构建（gradlew build）'
    & cmd /c ".\gradlew build --console=plain 2>&1" | Tee-Object -FilePath (Join-Path $repo 'build\release-build.log') | Select-Object -Last 5 |
        ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { Write-Host '构建失败：看 build\release-build.log' -ForegroundColor Red; exit 1 }
} else {
    Step 2 '跳过构建（-SkipBuild）'
}

$jar = Join-Path $repo "build\libs\tinkersnewlife-$version.jar"
if (-not (Test-Path $jar)) {
    Write-Host ("错误: 没有找到 " + $jar + "（版本号和 jar 名对不上？）") -ForegroundColor Red
    exit 1
}
Write-Host ("产物: tinkersnewlife-$version.jar（" + (Get-Item $jar).Length + " 字节）") -ForegroundColor Green

# ---------- 3. 部署到测试实例 ----------
if (-not $SkipDeploy) {
    Step 3 '部署到本地测试实例'
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'deploy.ps1')
    if ($LASTEXITCODE -eq 2) {
        Write-Host '游戏正在运行，已跳过部署（jar 仍在 build\libs 里，随时可再跑 tools\deploy.ps1）' -ForegroundColor Yellow
    } elseif ($LASTEXITCODE -ne 0) {
        Write-Host '部署失败（不影响后面的上传）' -ForegroundColor Yellow
    }
} else {
    Step 3 '跳过部署（-SkipDeploy）'
}

# ---------- 4. 上传 Modrinth ----------
if ($SkipUpload) {
    Step 4 '跳过上传（-SkipUpload）'
    Write-Host ("完成（本地）。产物: build\libs\tinkersnewlife-$version.jar") -ForegroundColor Green
    exit 0
}
Step 4 '上传到 Modrinth'
$uploadArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $repo 'upload-modrinth.ps1'), '-Version', $version, '-Type', $Type)
if ($DryRun) { $uploadArgs += '-DryRun' }
& powershell @uploadArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host '上传失败（上面有原因）。修好后可单独重跑：powershell -File upload-modrinth.ps1' -ForegroundColor Red
    exit 1
}
Write-Host ''
Write-Host ("全部完成：版本 " + $version + " 已构建" + $(if ($SkipDeploy) { '' } else { '、部署' }) + $(if ($DryRun) { '（上传为预演）' } else { '、上传' })) -ForegroundColor Green
