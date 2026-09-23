# 给本模组的流体批量生成「同名标签」（Forge + 匠魂两套命名空间 + 汇总标签）。
#
# 为什么要生成而不是手写：这支清单有 30+ 条，手写必然漏/写错 ✓。
# 每条流体的**条件**必须与它注册时的条件一致 ✗ —— 否则没装对应联动模组时
# 标签里会出现一个不存在的流体 id，加载报错 ✓。
#
# 生成两样东西（都在 data/<ns>/tags/fluids/ 下）：
#   1. forge:fluids/<name>        —— 生态最通用的"熔融X/液体X"标签（其他模组的机器/配方按它找）
#   2. tconstruct:fluids/<name>   —— 匠魂自己的流体标签命名空间（匠魂把原版熔融金属打在这里）
#
# ⚠ 一开始还生成了 forge:fluids/molten 与 all 两个**汇总标签**，后来**删掉了** ✗：
#   汇总标签只能带一组 mod_loaded 条件，于是"装了冰火但没装铁魔法"时，
#   `#forge:fluids/molten_pyrium` 这条引用会指向一个**根本不存在的标签** ✗
#   （Forge 对 `#` 引用的缺失标签有时报错、有时静默 —— 这种不确定性不该塞进包里）。
#   真正有价值的是**每个流体自己的同名标签** ✓，那 60 个文件就够了。
#
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen-fluid-tags.ps1
param(
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dataDir = Join-Path $root 'src\main\resources\data'

# name = 流体名（注册名，不带 _still）；mod = 领取条件（空 = 常驻）
$fluids = @(
    # ---- 原生 ----
    @{ name = 'gheloth_blood';             mod = '' }
    @{ name = 'molten_nicholas_blessing';  mod = '' }
    @{ name = 'hastur_malice';             mod = '' }
    @{ name = 'ashen_ink';                 mod = '' }
    @{ name = 'molten_durandal';           mod = '' }
    @{ name = 'curse_residue';             mod = '' }
    # ---- 冰火传说 ----
    @{ name = 'fire_blood';                mod = 'iceandfire' }
    @{ name = 'ice_blood';                 mod = 'iceandfire' }
    @{ name = 'lightning_blood';           mod = 'iceandfire' }
    @{ name = 'molten_dread';              mod = 'iceandfire' }
    @{ name = 'molten_dreadsteel';         mod = 'iceandfire' }
    @{ name = 'molten_dragonsteel_fire';   mod = 'iceandfire' }
    @{ name = 'molten_dragonsteel_ice';    mod = 'iceandfire' }
    @{ name = 'molten_dragonsteel_lightning'; mod = 'iceandfire' }
    # ---- 诡厄巫法 ----
    @{ name = 'molten_cursed_metal';       mod = 'goety' }
    @{ name = 'molten_dark_metal';         mod = 'goety' }
    @{ name = 'unholy_blood';              mod = 'goety' }
    @{ name = 'everburning_holy_fire';     mod = 'goety' }
    # ---- 诡厄巫法：启示录 ----
    @{ name = 'molten_broken_ring';        mod = 'goety_revelation' }
    # ---- 铁魔法 ----
    @{ name = 'primordial_fire_soul';      mod = 'irons_spellbooks' }
    @{ name = 'molten_arcane_ingot';       mod = 'irons_spellbooks' }
    @{ name = 'holy_spirit';               mod = 'irons_spellbooks' }
    @{ name = 'scorching_ice';             mod = 'irons_spellbooks' }
    @{ name = 'liquid_arcane';             mod = 'irons_spellbooks' }
    @{ name = 'cinder_ash';                mod = 'irons_spellbooks' }
    @{ name = 'molten_pyrium';             mod = 'irons_spellbooks' }
    @{ name = 'molten_mithril';            mod = 'irons_spellbooks' }
    @{ name = 'magic_gold_essence';        mod = 'irons_spellbooks' }
    @{ name = 'origin_polymer';            mod = 'irons_spellbooks' }
    @{ name = 'liquid_lightning';          mod = 'irons_spellbooks' }
)

function Write-Tag([string]$ns, [string]$relPath, [string]$content, [bool]$dry) {
    $dir = Join-Path $dataDir "$ns\tags\fluids"
    if ($relPath.Contains('/')) {
        $sub = $relPath.Substring(0, $relPath.LastIndexOf('/'))
        $dir = Join-Path $dir $sub
        $relPath = $relPath.Substring($relPath.LastIndexOf('/') + 1)
    }
    if (-not (Test-Path $dir)) { if (-not $dry) { New-Item -ItemType Directory -Path $dir -Force | Out-Null } }
    $file = Join-Path $dir "$relPath.json"
    if (-not $dry) { [System.IO.File]::WriteAllText($file, $content, (New-Object System.Text.UTF8Encoding($false))) }
    Write-Host ("  {0} {1}:{2}" -f $(if ($dry) { '(dry)' } else { '  ok ' }), $ns, $relPath)
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$made = 0

foreach ($f in $fluids) {
    $name = $f.name
    $mod = $f.mod
    $fluidId = "tinkersnewlife:${name}_still"

    # ⚠⚠ 条件**不能**写成标签顶层的 "conditions" ✗ —— Forge 1.20.1 的 TagLoader 不认它，
    #     结果是"没装联动模组时标签照样加载，里面指向一个不存在的流体" → 加载直接报错 ✗。
    #     实测（dev 客户端，未装联动）：50 条 `Couldn't load tag ... missing following references` ✓。
    #     正确做法是**逐条目**用 {"id": ..., "required": false} ✓ ——
    #     缺失时静默跳过该条目，标签本身照常加载 ✓（包里 tinkers_advanced 的 forge:ingots 就是这么写的 ✓）。
    $value = if ($mod) {
@"
    {
      "id": "$fluidId",
      "required": false
    }
"@
    } else {
        "    `"$fluidId`""
    }

    $body = @"
{
  "replace": false,
  "values": [
$value
  ]
}
"@
    Write-Tag 'forge' $name $body $DryRun.IsPresent
    Write-Tag 'tconstruct' $name $body $DryRun.IsPresent
    $made += 2
}

Write-Host "完成：$made 个标签文件（DryRun=$($DryRun.IsPresent)）"
