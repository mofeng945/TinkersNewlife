# 帕秋莉手册的"联动安全"处理（一次性/可重复执行）
#
# 规则（2026-09-27 反编译 Patchouli 1.20.1-85 实证 ✓，见备忘录 §710）：
#   1) 条目 icon：`BookIcon.from` **自己 catch** 异常，缺物品只会 warn + 空图标
#      ⇒ icon 可以继续指向跨模组物品 ✓（不需要换成原版图标 ✗）
#   2) 页面 `patchouli:spotlight` 的 `item`：走 `ItemStackVariableSerializer.fromJson`
#      ⇒ `ItemStackUtil.loadFromParsed` 对不存在的物品 **抛 RuntimeException（不 catch）** ✗
#      ⇒ 经 `BookContentsBuilder` 的 catch 变成 "Error building entry ..." ⇒ **整本书构建失败** ✗
#      ⇒ spotlight 的 item **必须**是必然存在的 id（minecraft:/tconstruct:/tinkersnewlife:）✓
#   3) `"flag": "mod:<modid>"` 只管"显示与否"，不阻止解析 ✗ ⇒ 光有 flag 挡不住 (2) ✗
#
# 用法：powershell -ExecutionPolicy Bypass -File tools\patchouli-compat.ps1 [-WhatIf]

param([switch]$WhatIf)

$root = Join-Path $PSScriptRoot '..\src\main\resources\assets\tinkersnewlife\patchouli_books'
$safe = @('minecraft', 'tconstruct', 'tinkersnewlife')

# 跨模组 spotlight 物品 → 安全的替代展示物（尽量保留主题暗示；文字里照旧写明真物品 ✓）
$spotlightMap = @{
    'irons_spellbooks:arcane_ingot'     = 'minecraft:amethyst_shard'
    'irons_spellbooks:magic_cloth'      = 'minecraft:white_wool'
    'irons_spellbooks:permafrost_shard' = 'minecraft:blue_ice'
    'irons_spellbooks:frozen_bone'      = 'minecraft:bone'
    'irons_spellbooks:hogskin'          = 'minecraft:leather'
    'irons_spellbooks:mithril_ingot'    = 'minecraft:iron_ingot'
    'irons_spellbooks:mithril_weave'    = 'minecraft:string'
    'irons_spellbooks:pyrium_ingot'     = 'minecraft:blaze_powder'
    'goety:cursed_ingot'                = 'minecraft:echo_shard'
    'goety:dark_ingot'                  = 'minecraft:netherite_scrap'
    'vampirism:blood_bottle'            = 'minecraft:cooked_beef'
    'vampirism:cursed_earth'            = 'minecraft:daylight_detector'
}
$fallback = 'minecraft:paper'

# 需要按模组显示与否的联动页 → 应带的 flag
$flagFor = @{
    'basics_arcane_anvil.json'          = 'mod:irons_spellbooks'
    'modifier_aristocratic_dining.json' = 'mod:vampirism'
    'modifier_hardened_skin.json'       = 'mod:vampirism'
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0

foreach ($f in (Get-ChildItem -Recurse -Path $root -Filter '*.json' -File)) {
    $text = [System.IO.File]::ReadAllText($f.FullName, $utf8)
    $orig = $text

    # ---- (2) spotlight 的 item 换成安全 id ----
    $text = [regex]::Replace($text, '("type"\s*:\s*"patchouli:spotlight"\s*,\s*"item"\s*:\s*")([a-z0-9_]+):([a-z0-9_/\.]+)(")', {
        param($m)
        $ns = $m.Groups[2].Value
        if ($safe -contains $ns) { return $m.Value }
        $id = "${ns}:$($m.Groups[3].Value)"
        $rep = if ($spotlightMap.ContainsKey($id)) { $spotlightMap[$id] } else { $fallback }
        Write-Host ("  spotlight {0}: {1} -> {2}" -f (Split-Path $f.FullName -Leaf), $id, $rep)
        return $m.Groups[1].Value + $rep + $m.Groups[4].Value
    })

    # ---- (3) 联动页补 flag（只在缺失时插入；插入点 = 最后一个 ']' 之后） ----
    if ($flagFor.ContainsKey($f.Name) -and $text -notmatch '"flag"') {
        $idx = $text.LastIndexOf(']')
        if ($idx -gt 0) {
            $ins = ',' + "`r`n" + '  "flag": "' + $flagFor[$f.Name] + '"'
            $text = $text.Substring(0, $idx + 1) + $ins + $text.Substring($idx + 1)
            Write-Host ("  flag {0}: + {1}" -f $f.Name, $flagFor[$f.Name])
        }
    }

    if ($text -ne $orig) {
        $changed++
        if (-not $WhatIf) { [System.IO.File]::WriteAllText($f.FullName, $text, $utf8) }
    }
}
Write-Host ("patchouli compat: {0} file(s) {1}" -f $changed, $(if ($WhatIf) { 'would change (dry run)' } else { 'changed' }))
