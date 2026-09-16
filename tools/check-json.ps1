# 资源 JSON 校验：帕秋莉/语言/配方/模型/方块状态等，落盘前跑一遍。
# 用法：powershell -ExecutionPolicy Bypass -File tools\check-json.ps1
#
# ⚠ 为什么必须用**严格**解析器（node 的 JSON.parse）：
#   PowerShell 的 ConvertFrom-Json / JavaScriptSerializer 都太宽松 ——
#   它们会放过"**键名没加引号**"（`arcane_cloth: "#777fad"`）这种非法 JSON ✗，
#   而真正读文件的 Mantle(Gson)/Minecraft 是严格的 → 结果是
#   `Failed to load JSON from resource tinkersnewlife:mantle/colors.json`
#   **整个 colors.json 全废**（所有自定义材料/特性名都退回默认灰）✗。
#   同类漏网的还有"文件开头带 UTF-8 BOM"（严格解析器会直接报错）✗。
#
# 注：空键名的方块状态（"variants": { "": {...} }）是合法的 Minecraft JSON，
#     但严格解析器不认 —— 这类文件显式放行（与旧脚本行为一致）。
$root = Split-Path -Parent $PSScriptRoot
$res = Join-Path $root 'src\main\resources'

$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) {
    Write-Host '!! 找不到 node.exe —— 退回宽松校验（可能漏掉非法 JSON，建议装 Node 后重跑）'
    Add-Type -AssemblyName System.Web.Extensions
    $bad = 0; $checked = 0; $skipped = 0
    $ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
    Get-ChildItem -Recurse -File -Path $res -Filter '*.json' | ForEach-Object {
        $rel = $_.FullName.Replace($res + '\', '')
        $raw = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
        $checked++
        try { $ser.DeserializeObject($raw) | Out-Null }
        catch {
            if ($raw -match '"variants"\s*:\s*\{\s*""') { $skipped++; return }
            Write-Host ('  BAD  ' + $rel + '  ->  ' + $_.Exception.Message)
            $bad++
        }
    }
    Write-Host ('checked ' + $checked + ', bad ' + $bad + ', skipped(empty-key blockstates) ' + $skipped)
    if ($bad -gt 0) { exit 1 }
    exit 0
}

$js = Join-Path $root 'build\strict-json-check.js'
$script = @'
const fs = require("fs"), path = require("path");
const root = process.argv[2];
let files = [];
(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith(".json")) files.push(p);
  }
})(root);
let bad = 0, skipped = 0;
for (const p of files) {
  const raw = fs.readFileSync(p, "utf8");
  const rel = p.replace(/\\/g, "/").replace(/^.*\/src\/main\/resources\//, "");
  try { JSON.parse(raw); }
  catch (e) {
    if (/"variants"\s*:\s*\{\s*"/.test(raw)) { skipped++; continue; }   // 空键方块状态：合法
    bad++;
    console.log("  BAD  " + rel + "  ->  " + e.message);
  }
}
console.log("checked " + files.length + ", bad " + bad + ", skipped(empty-key blockstates) " + skipped);
process.exit(bad > 0 ? 1 : 0);
'@
$dir = Split-Path $js -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
[System.IO.File]::WriteAllText($js, $script, (New-Object System.Text.UTF8Encoding($false)))
& node $js $res
exit $LASTEXITCODE
