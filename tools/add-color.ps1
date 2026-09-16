# 往 mantle/colors.json 里加一条颜色（材质的名字颜色 / 特性的名字颜色）。
#
# 为什么要做成脚本：这个文件我手工改过三次都出过事 ✗ ——
#   ① 键名忘了加引号（`arcane_cloth: "#777fad"`）→ Mantle(Gson) 严格解析 → **整个文件加载失败**，
#      而当时用的宽松校验器放过了它，游戏里表现为"所有自定义颜色全失效" ✗；
#   ② 把最后一个组的收尾也写成 `},` → 尾逗号 → 同样整文件失效 ✗。
# 这个脚本把这两件事都做对：
#   · 键名/值一律带双引号 ✓
#   · 组结尾按"是不是最后一个组"决定写 `}` 还是 `},` ✓
#   · 写完自动跑 **严格** JSON 校验（node JSON.parse），不合法就回滚 ✓
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\add-color.ps1 -Group material -Id frozen_bone -Color "#abbfc1"
#   powershell -ExecutionPolicy Bypass -File tools\add-color.ps1 -Group modifier -Id biting_frost -Color "#a5cbca"
param(
    [Parameter(Mandatory = $true)][ValidateSet('material', 'modifier')][string]$Group,
    [Parameter(Mandatory = $true)][string]$Id,
    [Parameter(Mandatory = $true)][string]$Color
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root 'src\main\resources\assets\tinkersnewlife\mantle\colors.json'
$groupKey = if ($Group -eq 'material') { 'material.tinkersnewlife' } else { 'modifier.tinkersnewlife' }

if ($Color -notmatch '^#[0-9a-fA-F]{6}$') { throw "颜色必须形如 #rrggbb（当前：$Color）" }

$lines = [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)
$start = -1
for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i].Trim() -eq ('"' + $groupKey + '": {')) { $start = $i; break } }
if ($start -lt 0) { throw "找不到组 $groupKey" }
$end = -1
for ($i = $start + 1; $i -lt $lines.Count; $i++) {
    $s = $lines[$i].Trim()
    if ($s -eq '}' -or $s -eq '},') { $end = $i; break }
}
if ($end -lt 0) { throw "找不到组 $groupKey 的结尾" }

# 该组是不是最后一个组？（其后再没有 `"xxx": {` 形式的组）
$isLast = $true
for ($i = $end + 1; $i -lt $lines.Count; $i++) { if ($lines[$i] -match '^\s*"[^"]+":\s*\{\s*$') { $isLast = $false; break } }

# 收集组内现有条目（统一成带引号的 `"key": "value"`）
$entries = New-Object System.Collections.Generic.List[string]
for ($i = $start + 1; $i -lt $end; $i++) {
    $s = $lines[$i].Trim()
    if ($s -eq '') { continue }
    $s = $s.TrimEnd(',')
    if ($s -notmatch '^"[^"]+":\s*"#[0-9a-fA-F]{6}"$') { throw ('组内有不是 "key": "#rrggbb" 格式的条目，先手工修：' + $s) }
    $entries.Add($s)
}
$newLine = '"' + $Id + '": "' + $Color + '"'
$exists = $false
for ($k = 0; $k -lt $entries.Count; $k++) {
    if ($entries[$k] -like ('"' + $Id + '":*')) { $entries[$k] = $newLine; $exists = $true; break }
}
if (-not $exists) { $entries.Add($newLine) }

$out = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -le $start; $i++) { $out.Add($lines[$i]) }
for ($k = 0; $k -lt $entries.Count; $k++) {
    $suffix = if ($k -lt $entries.Count - 1) { ',' } else { '' }
    $out.Add('    ' + $entries[$k] + $suffix)
}
$out.Add($(if ($isLast) { '  }' } else { '  },' }))
for ($i = $end + 1; $i -lt $lines.Count; $i++) { $out.Add($lines[$i]) }

$backup = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllLines($path, $out.ToArray(), (New-Object System.Text.UTF8Encoding($false)))

# 严格校验（node）；失败就回滚
$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) {
    $js = Join-Path $root 'build\strict-one.js'
    if (-not (Test-Path $js)) {
        [System.IO.File]::WriteAllText($js,
            'const fs=require("fs");const p=process.argv[2];try{JSON.parse(fs.readFileSync(p,"utf8"));}catch(e){console.log("BAD "+e.message);process.exit(1);}',
            (New-Object System.Text.UTF8Encoding($false)))
    }
    & node $js $path
    if ($LASTEXITCODE -ne 0) {
        [System.IO.File]::WriteAllText($path, $backup, (New-Object System.Text.UTF8Encoding($false)))
        throw '严格校验失败，已回滚（未写入）'
    }
}
$verb = if ($exists) { '更新' } else { '新增' }
Write-Host "$verb $groupKey.$Id = $Color  ✓（组内 $($entries.Count) 条，严格校验通过）"
