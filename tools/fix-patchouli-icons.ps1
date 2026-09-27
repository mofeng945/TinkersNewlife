# 一次性脚本：把手册（帕秋莉）里**跨模组**的 icon / item 引用换成"永远存在"的安全图标。
#
# 为什么必须换（§709）：Patchouli 的 BookContentsBuilder 在构建条目时是
#     catch (Exception e) { throw new RuntimeException("Error building entry ...") }
# ⇒ 只要有一个 icon 指向不存在的物品（未装那个模组时），**整本手册都构建失败** ✗。
# 而手册条目是静态资源，没法按 mod_loaded 隐藏 ⇒ 唯一稳妥做法 = 只用
# minecraft: / tconstruct: / tinkersnewlife: 这三个"必然存在"的命名空间 ✓。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools\fix-patchouli-icons.ps1 [-WhatIf]

param([switch]$WhatIf)

$root = Join-Path $PSScriptRoot '..\src\main\resources\assets\tinkersnewlife\patchouli_books'
$safe = @('minecraft', 'tconstruct', 'tinkersnewlife')

# 跨模组图标 → 安全替代（尽量保留"主题暗示"）
$map = @{
    'irons_spellbooks:arcane_ingot'      = 'minecraft:amethyst_shard'
    'irons_spellbooks:magic_cloth'       = 'minecraft:white_wool'
    'irons_spellbooks:permafrost_shard'  = 'minecraft:blue_ice'
    'irons_spellbooks:frozen_bone'       = 'minecraft:bone'
    'irons_spellbooks:hogskin'           = 'minecraft:leather'
    'irons_spellbooks:mithril_ingot'     = 'minecraft:iron_ingot'
    'irons_spellbooks:mithril_weave'     = 'minecraft:string'
    'irons_spellbooks:pyrium_ingot'      = 'minecraft:blaze_powder'
    'irons_spellbooks:arcane_anvil'      = 'minecraft:anvil'
    'goety:cursed_ingot'                 = 'minecraft:echo_shard'
    'goety:dark_ingot'                   = 'minecraft:netherite_scrap'
    'goety:nether_brick_brazier'         = 'minecraft:campfire'
    'vampirism:blood_bottle'             = 'minecraft:cooked_beef'
    'vampirism:cursed_earth'             = 'minecraft:daylight_detector'
}
$fallback = 'minecraft:paper'

$changed = 0
$files = Get-ChildItem -Recurse -Path $root -Filter '*.json' -File
foreach ($f in $files) {
    $text = [System.IO.File]::ReadAllText($f.FullName, (New-Object System.Text.UTF8Encoding($false)))
    $orig = $text
    $new = [regex]::Replace($text, '("(?:icon|item)"\s*:\s*")([a-z0-9_]+):([a-z0-9_/\.]+)(")', {
        param($m)
        $ns = $m.Groups[2].Value
        if ($safe -contains $ns) { return $m.Value }
        $id = "${ns}:$($m.Groups[3].Value)"
        $rep = if ($map.ContainsKey($id)) { $map[$id] } else { $fallback }
        Write-Host ("  {0}: {1} -> {2}" -f (Split-Path $f.FullName -Leaf), $id, $rep)
        return $m.Groups[1].Value + $rep + $m.Groups[4].Value
    })
    if ($new -ne $orig) {
        $changed++
        if (-not $WhatIf) {
            [System.IO.File]::WriteAllText($f.FullName, $new, (New-Object System.Text.UTF8Encoding($false)))
        }
    }
}
Write-Host ("patchouli icon sweep: {0} file(s) {1}" -f $changed, $(if ($WhatIf) { 'would change (dry run)' } else { 'changed' }))
