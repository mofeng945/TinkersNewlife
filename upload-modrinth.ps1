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
# ⚠⚠ 2026-09-14 现状：**本脚本的"真上传"还没成功过一次，暂时建议手动上传**。
#   已经查清/修好的部分：
#     * 端点是 POST https://api.modrinth.com/v2/version（写 /project/{id}/version 会 404）；
#     * 请求必须是 multipart，且**元数据要放在一个名为 `data` 的字段里（整块 JSON，含
#       project_id 与 file_parts）**，另加文件字段 —— 官方文档原文确认过；
#     * `-DryRun` 可用（只打印将上传什么，不联网）。
#   还没解决的部分：本机只有 Windows PowerShell 5.1（没有 pwsh 7），
#     HttpClient 阻塞式上传大 jar 实测会**卡住不返回**（版本也没创建）。
#     下一步可以试 curl.exe + 把 data JSON 写进临时文件（`-F "data=<file;type=application/json"`），
#     但未验证 —— 在那之前请按下面的清单手动上传。
#
# 【手动上传清单】（Modrinth 项目页 → Versions → Create version）
#   文件      : build\libs\tinkersnewlife-<版本>.jar
#   版本号    : 与 gradle.properties 的 mod_version 一致（当前 1.0.1.12）
#   名称      : Tinker's Newlife <版本>
#   频道      : Release
#   游戏版本  : 1.20.1    加载器: Forge
#   依赖      : Tinkers' Construct(required) / Mantle(required) / Curios(optional)
#   更新说明  : 复制 CHANGELOG-<版本>-en.md 全文
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

# ⭐ 若被 Windows PowerShell 5.1 调用，自动换成 PowerShell 7 重跑：
#    5.1 的 HttpClient 阻塞式上传实测会卡死（10 分钟不返回、版本也没创建），PS7 的原生 -Form 稳定。
#    找不到 pwsh 就退回下面手写 multipart 的兜底路径。
if ($PSVersionTable.PSVersion.Major -lt 7 -and -not $env:TNL_UPLOAD_REEXEC) {
    $pwshCmd = Get-Command pwsh -ErrorAction SilentlyContinue
    if ($pwshCmd) {
        Write-Host "检测到 Windows PowerShell 5.1：改用 PowerShell 7 执行本脚本…" -ForegroundColor Yellow
        $env:TNL_UPLOAD_REEXEC = '1'
        & $pwshCmd.Source -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath @PSBoundParameters
        exit $LASTEXITCODE
    }
    Write-Host "未找到 pwsh：退回内置的手写 multipart 路径" -ForegroundColor Yellow
}

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

$Dependencies = @(
    [ordered]@{ project_id = 'tconstruct'; dependency_type = 'required' },
    [ordered]@{ project_id = 'mantle';      dependency_type = 'required' },
    [ordered]@{ project_id = 'curios';      dependency_type = 'optional' }
)
$GameVersions = @('1.20.1')
$Loaders = @('forge')
$FileName = Split-Path $Jar -Leaf
# 文件在 multipart 里的字段名（随便取，但要写在 data.file_parts / primary_file 里）
$FilePartName = 'file'

# ⚠ Modrinth 的"创建版本"接口（POST /v2/version）**不是**一堆平铺字段：
#    官方文档原文 ——「The request is a multipart request with at least two form fields: one is `data`,
#    which includes a JSON body with the version metadata ..., and at least one field containing an upload file.」
#    也就是说：data = 整块 JSON（里面含 project_id / file_parts），另加文件字段。
#    以前脚本写成 /v2/project/{id}/version 会 404；平铺字段则会被服务端当成"读 data 时读到了
#    tinkers-newlife 这种非 JSON 文本"，报 400 `Error while parsing JSON: expected ident at line 1 column 2`。
$metadata = [ordered]@{
    project_id     = $Project
    name           = $Name
    version_number = $Version
    changelog      = $ChangelogText
    dependencies   = $Dependencies
    game_versions  = $GameVersions
    loaders        = $Loaders
    version_type   = $Type
    featured       = $false
    status         = 'listed'
    file_parts     = @($FilePartName)
    primary_file   = $FilePartName
}
$dataJson = $metadata | ConvertTo-Json -Depth 8 -Compress

Write-Host "上传中…（文件 $jarSize 字节，请稍等）" -ForegroundColor Cyan

# ⚠ 不用 curl.exe 拼命令行：更新说明是多行 Markdown，PowerShell 5.1 把多行参数传给原生程序时会拆坏，
#    curl 报 `URL rejected: Malformed input to a URL function`（实测踩过）。
#    这里手写 multipart 报文（RFC 7578：name="..." 带引号），字节级可控。
Add-Type -AssemblyName System.Net.Http
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromMinutes(10)
$client.DefaultRequestHeaders.Authorization =
    New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)

$boundary = '----TinkersNewlife' + [Guid]::NewGuid().ToString('N')
$CRLF = "`r`n"
$ms = New-Object System.IO.MemoryStream

function Write-Text([System.IO.Stream]$s, [string]$text) {
    $b = [System.Text.Encoding]::UTF8.GetBytes($text)
    $s.Write($b, 0, $b.Length)
}

# data 段：JSON 元数据
Write-Text $ms ("--$boundary$CRLF" +
    'Content-Disposition: form-data; name="data"' + $CRLF +
    'Content-Type: application/json; charset=utf-8' + $CRLF + $CRLF)
Write-Text $ms $dataJson
Write-Text $ms $CRLF

# 文件段（二进制，必须用字节写）
Write-Text $ms ("--$boundary$CRLF" +
    'Content-Disposition: form-data; name="' + $FilePartName + '"; filename="' + $FileName + '"' + $CRLF +
    'Content-Type: application/java-archive' + $CRLF + $CRLF)
$fileBytes = [System.IO.File]::ReadAllBytes($Jar)
$ms.Write($fileBytes, 0, $fileBytes.Length)
Write-Text $ms $CRLF
Write-Text $ms ("--$boundary--$CRLF")

$response = ''
if ($PSVersionTable.PSVersion.Major -ge 7) {
    # ⭐ PowerShell 7：直接用原生的 -Form（内部就是标准 multipart，data 字符串 + file 走文件流）
    Write-Host "（使用 PowerShell 7 的原生 -Form 上传）"
    $headers = @{ Authorization = "Bearer $Token" }
    $form = @{
        data = $dataJson
        $FilePartName = Get-Item -LiteralPath $Jar
    }
    try {
        $respObj = Invoke-RestMethod -Uri 'https://api.modrinth.com/v2/version' -Method Post `
            -Headers $headers -Form $form -TimeoutSec 600
        $response = $respObj | ConvertTo-Json -Depth 8
    } catch {
        Write-Host ("请求异常: " + $_.Exception.Message) -ForegroundColor Red
        $response = '{"error":"' + ($_.Exception.Message -replace '"', "'") + '"}'
    }
} else {
    # 兜底（PowerShell 5.1）：手写 multipart + HttpClient
    $bodyBytes = $ms.ToArray()
    # ⚠ 必须用 ::new(...)：New-Object 会把 byte[] 当参数列表"展开"，直接报 "Cannot find an overload"
    $content = [System.Net.Http.ByteArrayContent]::new($bodyBytes)
    $content.Headers.ContentType =
        [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("multipart/form-data; boundary=$boundary")
    try {
        $resp = $client.PostAsync('https://api.modrinth.com/v2/version', $content).GetAwaiter().GetResult()
        $response = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $resp.IsSuccessStatusCode) {
            Write-Host ("HTTP " + [int]$resp.StatusCode + " " + $resp.ReasonPhrase) -ForegroundColor Yellow
        }
    } catch {
        Write-Host ("请求异常: " + $_.Exception.Message) -ForegroundColor Red
        $response = '{"error":"' + ($_.Exception.Message -replace '"', "'") + '"}'
    } finally {
        $client.Dispose()
    }
}
$ms.Dispose()

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
