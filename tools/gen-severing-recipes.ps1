# gen-severing-recipes.ps1 —— 从各「联动模组」的实体战利品表批量生成匠魂「肢解」配方
#
# 背景（用户口径 2026-10-06）：匠魂原版肢解【不会】复制普通掉落物（只掉 severing 配方里显式写的东西）。
#   ⇒ 用户要求「给联动模组的所有涉及实体补一遍肢解的普通掉落物配方」。
#
# 做法：
#   · 扫 NL 包 / 测试包 mods/ 下每个 jar 的 data/<modid>/loot_tables/entities/**.json
#   · 只处理 modid 命中「本仓联动模组」白名单的表
#   · 实体 id 由【战利品表文件名（去目录）】推断，并用该模组
#     assets/<modid>/lang/en_us.json 里的 `entity.<modid>.*` 键做**白名单校验**
#     （⚠ 文件名 ≠ 实体 id 的情况是存在的 ✗；该 modid 没有 entity 语言键时才放宽）
#   · 从每张表里抽【物品条目】（含 tag 条目、递归 alternatives/group 的 children）
#   · 数量取该条目 set_count 的**最大值**（肢解＝额外一份，取上限最直观）
#   · 每个 (生物, 物品) 出一条 tconstruct:severing 配方，entity 用 "type" 精确匹配
#   · 带 forge:mod_loaded 条件 ⇒ 没装该模组的包（例如只装测试包）不会加载报错
#
# 用法：powershell -ExecutionPolicy Bypass -File tools\gen-severing-recipes.ps1 [-DryRun]

param(
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\data\tinkersnewlife\recipes\severing'
$modDirs = @(
    'D:\tex\.minecraft\versions\[NL]NewLifestyle崭新世界 V0.1.7\mods',
    'D:\tex\.minecraft\versions\1.20.1-Forge_47.4.22\mods'
)

# 本仓联动模组白名单（来自 src/main/java/.../integration/ 目录名 + IntegrationLoader 常量）
$allowed = @(
    'goety', 'goety_revelation', 'goety_ladder',
    'iceandfire', 'aquaculture', 'lavafishing', 'irons_spellbooks',
    'twilightforest', 'vampirism', 'mekanism', 'kaleidoscope_tavern',
    'kaleidoscope_world_liquor', 'kaleidoscope', 'create', 'tinkersnewlife'
)

if (-not $DryRun) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    # 重跑先清空旧产出 ✓（否则改了命名规则会留下两份 ✗）；目录是本脚本自己的产出目录 ✓
    Get-ChildItem -LiteralPath $outDir -File -Filter '*.json' -ErrorAction SilentlyContinue | Remove-Item -Force
}

function Get-MaxCount($functions) {
    $max = 1
    foreach ($f in @($functions)) {
        if ($null -eq $f) { continue }
        if ([string]$f.function -ne 'minecraft:set_count') { continue }
        $c = $f.count
        if ($null -eq $c) { continue }
        if ($c -is [int] -or $c -is [long] -or $c -is [double]) { $v = [int]$c }
        elseif ($null -ne $c.max) { $v = [int]$c.max }
        elseif ($null -ne $c.min) { $v = [int]$c.min }
        else { $v = 1 }
        if ($v -gt $max) { $max = $v }
    }
    if ($max -lt 1) { $max = 1 }
    if ($max -gt 64) { $max = 64 }
    return $max
}

function Get-ItemOutputs($entries) {
    $found = New-Object System.Collections.Generic.List[object]
    foreach ($e in @($entries)) {
        if ($null -eq $e) { continue }
        $t = [string]$e.type
        if ($t -eq 'minecraft:item' -or $t -eq 'item') {
            $name = [string]$e.name
            if ($name -and $name -ne 'minecraft:air') {
                $found.Add([pscustomobject]@{ Kind = 'item'; Name = $name; Count = (Get-MaxCount $e.functions) })
            }
        } elseif ($t -eq 'minecraft:tag' -or $t -eq 'tag') {
            $name = [string]$e.name
            if ($name) { $found.Add([pscustomobject]@{ Kind = 'tag'; Name = $name; Count = (Get-MaxCount $e.functions) }) }
        }
        if ($e.children) { foreach ($c in (Get-ItemOutputs $e.children)) { $found.Add($c) } }
    }
    return $found
}

$seen = @{}
$perMod = @{}
$total = 0
$skippedJson = 0
$skippedId = 0
$warn = New-Object System.Collections.Generic.List[string]

foreach ($dir in $modDirs) {
    if (-not (Test-Path -LiteralPath $dir)) { continue }
    foreach ($jar in (Get-ChildItem -LiteralPath $dir -File -Filter '*.jar' -ErrorAction SilentlyContinue)) {
        $zip = $null
        try { $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName) } catch { continue }
        try {
            # ① 先建该 jar 里所有模组的"实体 id 白名单"（按 modid 缓存）
            $entityIds = @{}
            foreach ($e in $zip.Entries) {
                if ($e.FullName -notmatch '^assets/([a-z0-9_]+)/lang/en_us\.json$') { continue }
                $mid = $matches[1]
                if ($allowed -notcontains $mid) { continue }
                if (-not $entityIds.ContainsKey($mid)) { $entityIds[$mid] = New-Object System.Collections.Generic.HashSet[string] }
                $txt = $null
                try { $sr = New-Object System.IO.StreamReader($e.Open()); $txt = $sr.ReadToEnd(); $sr.Close() } catch { continue }
                foreach ($m in [regex]::Matches($txt, '"entity\.' + [regex]::Escape($mid) + '\.([a-z0-9_/\.]+)"')) {
                    [void]$entityIds[$mid].Add($mid + ':' + $m.Groups[1].Value)
                }
            }

            # ② 处理战利品表
            foreach ($entry in $zip.Entries) {
                if ($entry.FullName -notmatch '^data/([a-z0-9_]+)/loot_tables/entities/(.+)\.json$') { continue }
                $modid = $matches[1]
                $fileBase = [System.IO.Path]::GetFileNameWithoutExtension($matches[2])
                if ($allowed -notcontains $modid) { continue }

                $known = $null
                if ($entityIds.ContainsKey($modid)) { $known = $entityIds[$modid] }
                $mob = $modid + ':' + $fileBase
                # 该模组有足够多 entity 语言键 ⇒ 必须以白名单为准（文件名猜错就跳过 ✓）
                if ($known -and $known.Count -ge 5 -and -not $known.Contains($mob)) {
                    $skippedId++
                    if ($warn.Count -lt 12) { $warn.Add($modid + ' ' + $fileBase) }
                    continue
                }

                $text = $null
                try { $sr2 = New-Object System.IO.StreamReader($entry.Open()); $text = $sr2.ReadToEnd(); $sr2.Close() } catch { continue }
                $json = $null
                try { $json = $text | ConvertFrom-Json } catch { $skippedJson++; continue }
                if (-not $json.pools) { continue }

                $items = New-Object System.Collections.Generic.List[object]
                foreach ($pool in @($json.pools)) {
                    if (-not $pool.entries) { continue }
                    foreach ($o in (Get-ItemOutputs $pool.entries)) { $items.Add($o) }
                }
                if ($items.Count -eq 0) { continue }

                foreach ($it in $items) {
                    $key = $modid + '|' + $mob + '|' + $it.Kind + '|' + $it.Name
                    if ($seen.ContainsKey($key)) { continue }
                    $seen[$key] = $true

                    # 文件名：<modid>_<生物名>__<物品>.json（生物 id 里的冒号换下划线 ✓ 不再重复拼 modid ✗）
                    $safeMob = ($fileBase -replace '[^a-z0-9_]', '_')
                    $safeItem = ($it.Name -replace '[^a-z0-9_]', '_')
                    $fileName = (($modid + '_' + $safeMob + '__' + $safeItem) -replace '_+', '_') + '.json'

                    $result = if ($it.Kind -eq 'tag') {
                        '{ "tag": "' + $it.Name + '", "count": ' + $it.Count + ' }'
                    } else {
                        '{ "item": "' + $it.Name + '", "count": ' + $it.Count + ' }'
                    }
                    $body = @"
{
  "type": "tconstruct:severing",
  "entity": {
    "type": "$mob"
  },
  "result": $result,
  "conditions": [
    {
      "type": "forge:mod_loaded",
      "modid": "$modid"
    }
  ]
}
"@
                    if (-not $DryRun) {
                        [System.IO.File]::WriteAllText((Join-Path $outDir $fileName), $body, (New-Object System.Text.UTF8Encoding($false)))
                    }
                    $total++
                    if (-not $perMod.ContainsKey($modid)) { $perMod[$modid] = 0 }
                    $perMod[$modid] = $perMod[$modid] + 1
                }
            }
        } catch {
            Write-Host ('  ⚠ ' + $jar.Name + ' 处理出错: ' + $_.Exception.Message)
        } finally {
            if ($zip) { $zip.Dispose() }
        }
    }
}

Write-Host '=== 肢解配方生成结果 ==='
$perMod.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object { '  ' + $_.Key.PadRight(26) + $_.Value + ' 条' }
Write-Host ('  合计 = ' + $total + ' 条；JSON 解析失败 ' + $skippedJson + ' 张；实体 id 不在语言表内被跳过 ' + $skippedId + ' 张')
if ($warn.Count -gt 0) { Write-Host ('  示例跳过：' + ($warn -join ' | ')) }
if ($DryRun) { Write-Host '  （DryRun：没有写文件）' } else { Write-Host ('  输出目录: ' + $outDir) }
