# 静态自检：混沌之流的"单次结算 + 随机二选一"闸门（2026-09-19 第二轮：用户口径「改简单一点，每次攻击在物理 / 某一学派法术里随机选一种，不拆分了」）
#
# 为什么要有它：
#   ① 本特性的关键不变量**不靠优先级、也不靠配置**——
#      "每次攻击只结算一次" + "改判类型那一次必须包在 DamagePipeline 里"，
#      都是"字符串/注解之外的东西"，编译期不会报错 ✗：
#      · 少包一次 enter/exit ⇒ 我们自己的放大器会在重发的那一发上**再叠一次**（＝变相翻倍 ✗）；
#      · 又冒出第二处 hurt()（或多段循环）⇒ 又回到"段数 × 增幅"膨胀 ✗；
#      而且**表面上一切正常** ✗。
#   ② 全工程"会改数值"的伤害事件处理器**逐个**都得有 skipNested 闸门（外部 mod 管不了，至少我们自己的要管住 ✓）。
#
# 本脚本钉死 6 组断言（不需要开游戏）：
#   [1] util/DamagePipeline 闸门本体语义正确（ThreadLocal + enter/exit/skipNested/isNested）；
#   [2] ChaosFlowHandler 是**单次**结算（全类只有 1 处 hurt()），且 enter/exit 成对、exit 在 finally 里；
#   [3] 随机二选一（物理 = 什么都不做直接 return；法术 = setCanceled(true) + 随机学派 + 同样数值重发一次）；
#   [4] **没有**任何残留的分段痕迹（Flurry / QUEUES / BURST_LIMIT / per = base / segments / markup 快照 /
#       invulnerableTime 清零 / LevelTickEvent 滞后队列）；配置键也只剩 enabled；
#   [5] 原有语义仍在（LOWEST 优先级 + 手持带 chaos_flow 的工具这道门槛）；
#   [6] 全工程"会改数值"的处理器清单：非白名单的**逐个**都有闸门（含用户报告链路上的 13 处点名）。
#
# 用法： powershell -ExecutionPolicy Bypass -File tools\check-chaos-flow-gates.ps1
# 退出码：0 = 全过；1 = 有断言失败。

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$src  = Join-Path $root 'src\main\java'
$res  = Join-Path $root 'src\main\resources'
$pipePath = Join-Path $src 'com\mofengbaizhi\tinkersnewlife\util\DamagePipeline.java'
$flowPath = Join-Path $src 'com\mofengbaizhi\tinkersnewlife\content\modifier\events\ChaosFlowHandler.java'
$cfgPath  = Join-Path $src 'com\mofengbaizhi\tinkersnewlife\config\ModConfig.java'

$fail = 0
function Ok($m)  { Write-Host "  [OK]   $m" }
function Bad($m) { Write-Host "  [FAIL] $m"; $script:fail++ }

# 去掉 Java 注释（只保留真正的代码）—— "分段痕迹"必须在**代码**里清零 ✓，
# 类注释为了解释"本轮删掉了什么"不可避免地会提到 Flurry / takeMarkup 这些名字 ✓（那是文档、不是代码 ✗）。
function Get-JavaCode([string]$text) {
    $sb = New-Object System.Text.StringBuilder
    $i = 0; $n = $text.Length; $mode = 'code'
    while ($i -lt $n) {
        $c = $text[$i]
        $c2 = if ($i + 1 -lt $n) { $text[$i + 1] } else { [char]0 }
        if ($mode -eq 'code') {
            if ($c -eq '/' -and $c2 -eq '/') { $mode = 'line'; $i += 2; continue }
            if ($c -eq '/' -and $c2 -eq '*') { $mode = 'block'; $i += 2; continue }
            if ($c -eq '"') { $mode = 'str'; [void]$sb.Append($c); $i++; continue }
            if ($c -eq "'") { $mode = 'chr'; [void]$sb.Append($c); $i++; continue }
            [void]$sb.Append($c); $i++
        }
        elseif ($mode -eq 'line') { if ($c -eq "`n") { $mode = 'code'; [void]$sb.Append($c) }; $i++ }
        elseif ($mode -eq 'block') { if ($c -eq '*' -and $c2 -eq '/') { $mode = 'code'; $i += 2 } else { $i++ } }
        elseif ($mode -eq 'str') {
            [void]$sb.Append($c)
            if ($c -eq '\') { if ($i + 1 -lt $n) { [void]$sb.Append($c2) }; $i += 2; continue }
            if ($c -eq '"') { $mode = 'code' }
            $i++
        }
        else {
            [void]$sb.Append($c)
            if ($c -eq '\') { if ($i + 1 -lt $n) { [void]$sb.Append($c2) }; $i += 2; continue }
            if ($c -eq "'") { $mode = 'code' }
            $i++
        }
    }
    return $sb.ToString()
}

Write-Host "== 混沌之流『单次结算 + 随机二选一』静态自检 =="

# ⚠ 源文件与文案都**没有 BOM**，而 Windows PowerShell 5.1 的 Get-Content -Raw 会按 ANSI 读
#   ⇒ 中文注释/文案会变乱码、涉及中文的断言会**假失败** ✗。所以一律走这个 UTF-8 读取器 ✓。
function Read-Utf8([string]$path) {
    return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

# --- ① 闸门本体 ---
Write-Host "`n[1] util/DamagePipeline（同线程嵌套标记）"
if (-not (Test-Path -LiteralPath $pipePath)) { Bad "找不到 $pipePath"; exit 1 }
$pipe = Read-Utf8 $pipePath
if ($pipe -match 'ThreadLocal<Boolean>\s+NESTED') { Ok 'ThreadLocal<Boolean> 标记（同线程同步 ⇒ 零锁零开销）' }
else { Bad 'DamagePipeline 里没有 ThreadLocal 标记' }
if ($pipe -match 'public static void enter\(\)' -and $pipe -match 'public static void exit\(\)') {
    Ok 'enter()/exit() 成对存在（由混沌之流在重发那一次 hurt() 前后包住）'
} else { Bad 'DamagePipeline 缺 enter()/exit()' }
if ($pipe -match 'public static boolean skipNested\(\)') { Ok 'skipNested() 是各处理器统一入口' }
else { Bad 'DamagePipeline 缺 skipNested()' }
if ($pipe -match 'static boolean isNested\(\)') { Ok 'isNested() 可查询' } else { Bad '缺 isNested()' }

# --- ② 混沌之流：单次结算 ---
Write-Host "`n[2] ChaosFlowHandler：单次结算（全类只有一处 hurt()）"
if (-not (Test-Path -LiteralPath $flowPath)) { Bad "找不到 $flowPath"; exit 1 }
$flow = Read-Utf8 $flowPath
$hurtCalls = ([regex]::Matches($flow, '\.hurt\s*\(')).Count
if ($hurtCalls -eq 1) { Ok '全类只有 1 处 hurt() ⇒ 每次攻击只结算一次（没有分段循环/滞后队列 ✓）' }
else { Bad "hurt() 出现 $hurtCalls 次（应为 1）—— 多于 1 处就说明又在分段了 ✗" }
$enters = ([regex]::Matches($flow, 'DamagePipeline\.enter\(\)')).Count
$exits  = ([regex]::Matches($flow, 'DamagePipeline\.exit\(\)')).Count
if ($enters -eq 1 -and $exits -eq 1) { Ok 'DamagePipeline.enter/exit 各 1 次、成对（+1/+1 覆盖唯一那次重发）' }
else { Bad "DamagePipeline enter/exit 次数应为 1/1，实际 $enters/$exits" }
if ($flow -match '(?s)finally\s*\{[^}]*DamagePipeline\.exit\(\);') { Ok 'exit 在 finally 里（异常也不会把标记留在主线程上）' }
else { Bad 'exit 不在 finally 里 —— 一旦 hurt() 抛异常，主线程会永久处于"嵌套中"✗' }
if ($flow -match 'target\.hurt\(schoolDamage, total\)') {
    Ok '重发用的是**同样的总数值**（total，与进入本处理器时的 amount 一致 ✓）'
} else { Bad '没有找到 target.hurt(schoolDamage, total) —— 法术那一路不是"原数值单发"' }
# 防重入：本处理器自己也要在最开头早退（否则法术那一发会被无限改判 ✗）
if ($flow -match 'if \(Boolean\.TRUE\.equals\(SPLITTING\.get\(\)\)\) return;') {
    Ok 'onLivingHurt 最开头有防重入早退（法术那一发不会被再改判一次）'
} else { Bad 'onLivingHurt 缺防重入早退' }
# 击杀归属：取消原始那一次 + 自己重发 ⇒ 必须显式补一次（killed_by_player 类战利品 ✓）
if ($flow -match 'creditKill\(target, attacker\);') { Ok '重发前显式 creditKill（击杀归属不丢 ✓）' }
else { Bad '重发前没有 creditKill —— killed_by_player 类战利品会丢 ✗' }

# --- ③ 随机二选一 ---
Write-Host "`n[3] 随机二选一：物理（放行原伤害）或 法术（改判类型后单发）"
if ($flow -match 'RANDOM\.nextFloat\(\)\s*<\s*CHANCE_PHYSICAL') { Ok '先掷一次骰子决定「物理 / 法术」' }
else { Bad '没有找到"物理 / 法术"的随机分支（应当只掷一次骰子）' }
if ($flow -match 'private static final float CHANCE_PHYSICAL') { Ok '物理概率是常量 CHANCE_PHYSICAL' }
else { Bad '缺 CHANCE_PHYSICAL 常量' }
# 物理那一路必须"什么都不做"：return 出现在掷骰分支里，且不 setCanceled / 不 hurt
$physBranch = [regex]::Match($flow, '(?s)if \(RANDOM\.nextFloat\(\) < CHANCE_PHYSICAL\)\s*\{(.{0,400}?)\n        \}')
if (-not $physBranch.Success) { Bad '拿不到"物理"分支的代码块' }
else {
    $b = $physBranch.Groups[1].Value
    if ($b -match 'return;' -and $b -notmatch 'setCanceled' -and $b -notmatch '\.hurt\(') {
        Ok '物理分支：不取消、不重发 ⇒ 原始那一次（伤害源与数值）原样结算 ✓'
    } else { Bad '物理分支改动了事件或重发了伤害 —— 物理必须是"什么都不做" ✗' }
}
# 法术那一路：取消 + 随机学派 + 用学派伤害源
if ($flow -match 'event\.setCanceled\(true\);') { Ok '法术分支取消原始那一次（setCanceled(true)）' }
else { Bad '法术分支没有取消原始事件 —— 会变成"打两次"✗' }
if ($flow -match 'schoolKeysCached\(\)') { Ok '学派表走 schoolKeysCached()（沿用既有实现，带 30 秒缓存）' }
else { Bad '没有沿用既有的学派表读取（schoolKeysCached）' }
if ($flow -match 'schools\.get\(RANDOM\.nextInt\(schools\.size\(\)\)\)') { Ok '随机取一个学派（nextInt(size)，均匀分布 ✓）' }
else { Bad '没有"随机取一个学派"的写法' }
if ($flow -match 'schoolSource\(target, attacker, school\)') { Ok '沿用既有的"学派 → DamageSource"映射（schoolSource）' }
else { Bad '没有沿用 schoolSource(...) 这套学派伤害源构造' }

# --- ④ 不许有分段痕迹（只看**代码**：类注释里为解释"删了什么"提到旧名字是允许的 ✓） ---
Write-Host "`n[4] 分段痕迹必须清零（新模型是单次结算）"
$flowCode = Get-JavaCode $flow
$leftovers = @(
    @{ Re='Flurry';                  Why='滞后发放用的"一串段"结构' },
    @{ Re='QUEUES';                  Why='每维度待发放队列' },
    @{ Re='BURST_LIMIT';             Why='同 tick 打完的段数阈值' },
    @{ Re='LevelTickEvent';          Why='每 tick 限量发放（只服务于分段）' },
    @{ Re='segmentsPerTick';         Why='segments_per_tick 的读取器' },
    @{ Re='maxSchoolSegments';       Why='max_school_segments 的读取器' },
    @{ Re='takeMarkup';              Why='"上游抬升量"账本（只服务于"均分基数"）' },
    @{ Re='SNAPSHOT';                Why='HIGHEST 快照表（只服务于"抬升量"）' },
    @{ Re='onSnapshotHurt';          Why='HIGHEST 快照监听器（新模型不需要）' },
    @{ Re='IdentityHashMap';         Why='快照表的键' },
    @{ Re='EventPriority\.HIGHEST';  Why='快照监听器的优先级' },
    @{ Re='physicalSource';          Why='物理段用的伤害源构造器（改判后不需要）' },
    @{ Re='float\s+per\s*=';                 Why='均分后的每段数值' },
    @{ Re='(int|float)\s+segments\s*=';      Why='"段数"这个变量本身' },
    @{ Re='invulnerableTime\s*=\s*[0-9]';    Why='手动无敌帧清零/恢复（多段才需要）' }
)
$leftoverFailBefore = $fail
foreach ($lo in $leftovers) {
    if ($flowCode -match $lo.Re) { Bad "ChaosFlowHandler **代码**里还留有分段痕迹：$($lo.Re)（$($lo.Why)）" }
}
if ($fail -eq $leftoverFailBefore) { Ok '代码里没有任何分段痕迹 ✓（单次结算，不再逐段 hurt / 不再清无敌帧）' }
# 反向断言：类注释里必须**如实写明**删掉了这些东西（文档不能含糊 ✓）
foreach ($doc in @('均分', 'Flurry', 'takeMarkup', 'HIGHEST')) {
    if ($flow -notmatch [regex]::Escape($doc)) { Bad "类注释里没有提到已删除的旧机制「$doc」—— 删了什么必须写清楚 ✗" }
}
if ($fail -eq $leftoverFailBefore) { Ok '类注释写明了本轮删掉的旧机制（均分 / Flurry / takeMarkup / HIGHEST ✓）' }

# 配置键：只留 enabled；两个只服务于分段的键必须删掉
if (-not (Test-Path -LiteralPath $cfgPath)) { Bad "找不到 $cfgPath" }
else {
    $cfg = Read-Utf8 $cfgPath
    foreach ($k in @('max_school_segments', 'segments_per_tick', 'CHAOS_FLOW_MAX_SCHOOL_SEGMENTS', 'CHAOS_FLOW_SEGMENTS_PER_TICK')) {
        if ($cfg -match [regex]::Escape($k)) { Bad "ModConfig 里还留着只服务于分段的配置：$k" }
    }
    if ($cfg -match 'CHAOS_FLOW_ENABLED\s*=\s*b\.define\("enabled", true\)') { Ok '配置只剩 chaos_flow.enabled（总开关 ✓）' }
    else { Bad 'chaos_flow.enabled 总开关丢了 ✗' }
}

# --- ⑤ 原有语义仍在 ---
Write-Host "`n[5] 原有语义仍在（触发门槛 + 优先级）"
if ($flow -match '@SubscribeEvent\(priority = EventPriority\.LOWEST\)') { Ok '仍在 EventPriority.LOWEST（别人都改完之后才掷骰/改判 ✓）' }
else { Bad '优先级被改了 —— 必须仍是 LOWEST ✗' }
if ($flow -match 'ToolHelper\.getCombatToolWith\(source, attacker, ChaosFlowModifier\.ID\)') {
    Ok '仍有"手持带 chaos_flow 的工具"这道门槛（别的伤害不受影响 ✓）'
} else { Bad '触发门槛（手持混沌之流工具）被改掉/丢了 ✗' }
if ($flow -match 'ToolHelper\.getActiveModifierLevel\(tool, ChaosFlowModifier\.ID\) <= 0') { Ok '仍查工具上的特性等级 > 0' }
else { Bad '特性等级判定丢了 ✗' }
if ($flow -match 'ModConfig\.CHAOS_FLOW_ENABLED\.get\(\)') { Ok '总开关仍生效（enabled=false ⇒ 一发都不改判 ✓）' }
else { Bad '总开关没有生效 ✗' }

# --- ⑥ 全工程"会改数值"的处理器清单 ---
Write-Host "`n[6] 全工程伤害事件里『会改数值』的处理器：是否有 skipNested 闸门"
# 白名单：这些**不是增幅**（减伤/限伤/抹零/快照还原/真伤按原意顶开/换伤害类型），
# 故意不加闸门 ✓ —— 加了反而会改变既有语义 ✗。
$whitelist = @(
    'LifeLampRingHandler',   # 慈悲：都该被截（设计如此 ✓）
    'DamageLimitEffect',     # 限伤（目标方保护）
    'MomoMerchantHandler',   # 墨默吟唱抗性 ×0.4（减伤）
    'DarkMetalMagicResistHandler', # 破法（减免）
    'ShikigamiHandler',      # 把被抹成 0 的伤害按"主人攻击力"恢复（绝对量，不是增幅）
    'TruePierce',            # 真伤按原意顶开数值
    'OrderOriginHandler',    # 秩序之初：取消 + 换成物理重打（改类型，不改数值）
    'ShikigamiBehavior'
)
$files = Get-ChildItem -LiteralPath $src -Recurse -Filter '*.java'
$guarded = @(); $unguarded = @(); $exempt = @()
foreach ($f in $files) {
    $t = Get-Content -LiteralPath $f.FullName -Raw
    if ($t -notmatch 'LivingHurtEvent|LivingDamageEvent') { continue }
    # "会改数值"的形态（三类）：
    #   ① setAmount(...)                  —— 直接改这一发事件里的数值；
    #   ② getAmount() + hurt(             —— 按"这一发"算一个追加量、再自己发一段（冷酷 / 炽热）；
    #   ③ PENDING.add(                    —— 同上，只是走了"同 tick 末落地"的队列形态。
    $mSet = [regex]::Matches($t, 'setAmount\s*\(')
    $defers = ($t -match 'getAmount\(\)') -and ($t -match '\.hurt\(')
    $queues = $t -match 'PENDING\.add\('
    if ($mSet.Count -eq 0 -and -not ($defers -or $queues)) { continue }
    # DamagePipeline 只是"被引用的工具类"（它的注释里提到 LivingHurtEvent）⇒ 不算处理器 ✓
    if ($f.BaseName -eq 'DamagePipeline') { continue }
    $hasGuard = $t -match 'DamagePipeline\.skipNested\(\)'
    if ($whitelist -contains $f.BaseName) { $exempt += $f.BaseName; continue }
    if ($hasGuard) { $guarded += $f.BaseName } else { $unguarded += $f.BaseName }
}
Write-Host ("  有闸门（$($guarded.Count)）：" + (($guarded | Sort-Object -Unique) -join ', '))
Write-Host ("  白名单豁免（$($exempt.Count)）：" + (($exempt | Sort-Object -Unique) -join ', '))
if ($unguarded.Count -eq 0) { Ok '全部"会改数值"的处理器都有闸门 ✓' }
else { Bad ("以下文件会改伤害数值但**没有**闸门 ⇒ 改判重发时会被再叠一次 ✗：" + (($unguarded | Sort-Object -Unique) -join ', ')) }

# 重点点名：用户报告里那几条（黑闪 / 群星之子 / 巫师套装 / 魔杖 / 投射咒法 / 堕落 / 幻兽琥珀 / 处刑 / 冷酷 / 炽热 / 龙杖 / 魔导）
Write-Host "`n[7] 用户报告链路上的关键处理器逐一点名"
$must = @(
    @{ Name='黑闪（原伤^2.5）';                 File='BlackFlashHandler.java' },
    @{ Name='灰白之墨·群星之子（×2^级）';        File='ChildOfTheStarsHandler.java' },
    @{ Name='巫师套装（法术 ×(1+0.1×件数)）';    File='WizardArmorSetHandler.java' },
    @{ Name='模块化魔杖（法术增幅）';            File='ModularStaffModifier.java' },
    @{ Name='投射咒法（×2^层）';                 File='TinkersNewlife.java' },
    @{ Name='堕落（+bonus）';                    File='CorruptionHandler.java' },
    @{ Name='幻兽琥珀（+bonus）';                File='LightningManipulationTechnique.java' },
    @{ Name='处刑人之剑（×2）';                  File='ExecutionDomain.java' },
    @{ Name='冷酷（追加冰霜）';                  File='FormlessIceHandler.java' },
    @{ Name='炽热（追加炽焰）';                  File='PyriumHandler.java' },
    @{ Name='龙杖（每龙 +攻击）';                File='DragonStaffHandler.java' },
    @{ Name='魔导增伤（外部法术）';              File='IronSpellsArcaneHandler.java' },
    @{ Name='混沌之流本体';                      File='ChaosFlowHandler.java' }
)
foreach ($m in $must) {
    $hit = $files | Where-Object { $_.Name -eq $m.File } | Select-Object -First 1
    if ($null -eq $hit) { Bad "$($m.Name)：找不到 $($m.File)"; continue }
    $t = Get-Content -LiteralPath $hit.FullName -Raw
    if ($t -match 'DamagePipeline\.(skipNested|enter)\(\)') { Ok "$($m.Name) —— $($m.File)" }
    else { Bad "$($m.Name)：$($m.File) 没有 DamagePipeline 闸门" }
}

# --- ⑧ 文案：不许再出现"均分 / 拆成多段 / 每学派 1 段" ---
Write-Host "`n[8] 文案（语言文件 + 帕秋莉）必须已改成新口径"
$textFiles = @(
    (Join-Path $res 'assets\tinkersnewlife\lang\zh_cn.json'),
    (Join-Path $res 'assets\tinkersnewlife\lang\en_us.json'),
    (Join-Path $res 'assets\tinkersnewlife\patchouli_books\guide\zh_cn\entries\trait_chaos_flow.json'),
    (Join-Path $res 'assets\tinkersnewlife\patchouli_books\guide\en_us\entries\trait_chaos_flow.json'),
    (Join-Path $res 'assets\tinkersnewlife\patchouli_books\guide\zh_cn\entries\material_origin_alloy.json'),
    (Join-Path $res 'assets\tinkersnewlife\patchouli_books\guide\en_us\entries\material_origin_alloy.json'),
    (Join-Path $res 'assets\tinkersnewlife\patchouli_books\guide\zh_cn\entries\trait_overview.json'),
    (Join-Path $res 'assets\tinkersnewlife\patchouli_books\guide\en_us\entries\trait_overview.json')
)
$stalePattern = '均分|拆成多段|拆成 \d+ 段|每学派 1 段|split evenly|split into \d+ physical|1 physical \+ 1 per school|split by school'
$stale = 0
foreach ($tf in $textFiles) {
    if (-not (Test-Path -LiteralPath $tf)) { Bad "找不到文案文件 $tf"; continue }
    # ⚠ 一律用 .NET 以 UTF-8 读：本仓库的 lang/*.json 没有 BOM，
    #   Windows PowerShell 5.1 的 Get-Content -Raw 会按 ANSI 读 ⇒ 中文变乱码、断言会假失败 ✗。
    $txt = [System.IO.File]::ReadAllText($tf, [System.Text.Encoding]::UTF8)
    $hits = [regex]::Matches($txt, $stalePattern)
    if ($hits.Count -gt 0) { Bad "$(Split-Path -Leaf $tf) 里仍有旧口径文案：$(($hits | ForEach-Object { $_.Value }) -join ' / ')"; $stale++ }
}
if ($stale -eq 0) { Ok '8 个文案文件全部已是新口径（无"均分 / 拆成多段 / 每学派 1 段"残留 ✓）' }
# 新口径关键词抽查（每个语言各一处）
$zhLang = [System.IO.File]::ReadAllText($textFiles[0], [System.Text.Encoding]::UTF8)
if ($zhLang -match '随机挑一种' -and $zhLang -match '不再拆分为多段') { Ok '中文本地化里写明"随机挑一种"与"不再拆分为多段" ✓' }
else { Bad '中文本地化没有写明新口径（随机挑一种 / 不再拆分为多段）' }
$enLang = [System.IO.File]::ReadAllText($textFiles[1], [System.Text.Encoding]::UTF8)
if ($enLang -match 'rolls randomly' -and $enLang -match 'no more splitting') { Ok '英文本地化里写明 "rolls randomly" 与 "no more splitting" ✓' }
else { Bad '英文本地化没有写明新口径（rolls randomly / no more splitting）' }

Write-Host ""
if ($fail -eq 0) { Write-Host "全部通过：bad 0"; exit 0 }
else { Write-Host "有 $fail 项未通过"; exit 1 }
