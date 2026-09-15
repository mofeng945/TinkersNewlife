# 资源 JSON 校验：帕秋莉/语言/配方/模型/方块状态等，落盘前跑一遍能挡住"手写 JSON 里带裸引号"这类错误。
# 用法：powershell -ExecutionPolicy Bypass -File tools\check-json.ps1
#
# 注：PowerShell 5.1 的 ConvertFrom-Json 无法处理**空键名**（方块状态里的 "": {...}），
#     这类文件会被误报，脚本里显式放行（它们是合法的 Minecraft JSON）。
Add-Type -AssemblyName System.Web.Extensions
$root = Split-Path -Parent $PSScriptRoot
$res = Join-Path $root 'src\main\resources'
$bad = 0; $checked = 0; $skipped = 0
$ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
Get-ChildItem -Recurse -File -Path $res -Filter '*.json' | ForEach-Object {
    $path = $_.FullName
    $rel = $path.Replace($res + '\', '')
    $raw = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $checked++
    try { $ser.DeserializeObject($raw) | Out-Null }
    catch {
        if ($raw -match '"variants"\s*:\s*\{\s*""') { $skipped++; return }   # 空键方块状态：合法
        Write-Host ('  BAD  ' + $rel + '  ->  ' + $_.Exception.Message)
        $bad++
    }
}
Write-Host ('checked ' + $checked + ', bad ' + $bad + ', skipped(empty-key blockstates) ' + $skipped)
if ($bad -gt 0) { exit 1 }