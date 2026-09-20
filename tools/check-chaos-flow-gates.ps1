# 静态自检：混沌之流的"嵌套段结算"闸门（2026-09-19 用户报告：附加伤害被拆段 + 每段再吃一遍增幅 ⇒ 数值爆炸）
#
# 为什么要有它：这次的修法**不靠优先级、只靠一个 ThreadLocal 标记**
# （util/DamagePipeline）—— 标记是"字符串/注解之外的东西"，编译期不会报错，
# 少写一处、或以后有人新加一个"在伤害事件里 setAmount"的处理器忘了加闸门，
# 就又会变成"段数 × 增幅 = 指数级膨胀" ✗，而且**表面上一切正常** ✗。
# 所以本脚本把三件事钉死（不需要开游戏）：
#   ① 闸门本体在（DamagePipeline 存在 + 语义正确：ThreadLocal + enter/exit/skipNested）；
#   ② 混沌之流**每一处** hurt() 都被 DamagePipeline.enter/exit 包住（含滞后发放那条队列路径）；
#   ③ 全工程"会改数值"的伤害事件处理器**逐个**都调了 skipNested（白名单只放行有意的：
#       减伤/限伤/抹零/快照还原/真伤顶开/秩序之初换类型 —— 它们不是"增幅" ✓）。
#
# 用法： powershell -ExecutionPolicy Bypass -File tools\check-chaos-flow-gates.ps1
# 退出码：0 = 全过；1 = 有断言失败。

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$src  = Join-Path $root 'src\main\java'
$pipePath = Join-Path $src 'com\mofengbaizhi\tinkersnewlife\util\DamagePipeline.java'
$flowPath = Join-Path $src 'com\mofengbaizhi\tinkersnewlife\content\modifier\events\ChaosFlowHandler.java'

$fail = 0
function Ok($m)  { Write-Host "  [OK]   $m" }
function Bad($m) { Write-Host "  [FAIL] $m"; $script:fail++ }

Write-Host "== 混沌之流嵌套段闸门静态自检 =="

# --- ① 闸门本体 ---
Write-Host "`n[1] util/DamagePipeline（同线程嵌套标记）"
if (-not (Test-Path -LiteralPath $pipePath)) { Bad "找不到 $pipePath"; exit 1 }
$pipe = Get-Content -LiteralPath $pipePath -Raw
if ($pipe -match 'ThreadLocal<Boolean>\s+NESTED') { Ok 'ThreadLocal<Boolean> 标记（同线程同步 ⇒ 零锁零开销）' }
else { Bad 'DamagePipeline 里没有 ThreadLocal 标记' }
if ($pipe -match 'public static void enter\(\)' -and $pipe -match 'public static void exit\(\)') {
    Ok 'enter()/exit() 成对存在（由混沌之流在每次 hurt() 前后包住）'
} else { Bad 'DamagePipeline 缺 enter()/exit()' }
if ($pipe -match 'public static boolean skipNested\(\)') { Ok 'skipNested() 是各处理器统一入口' }
else { Bad 'DamagePipeline 缺 skipNested()' }
if ($pipe -match 'static boolean isNested\(\)') { Ok 'isNested() 可查询' } else { Bad '缺 isNested()' }

# --- ② 混沌之流自己的 enter/exit ---
Write-Host "`n[2] ChaosFlowHandler：每处 hurt() 都在标记内"
if (-not (Test-Path -LiteralPath $flowPath)) { Bad "找不到 $flowPath"; exit 1 }
$flow = Get-Content -LiteralPath $flowPath -Raw
$enters = ([regex]::Matches($flow, 'DamagePipeline\.enter\(\)')).Count
$exits  = ([regex]::Matches($flow, 'DamagePipeline\.exit\(\)')).Count
if ($enters -eq $exits -and $enters -ge 3) {
    Ok "enter/exit 成对且覆盖全部路径（enter $enters / exit $exits；应为 物理段 + 学派段 + 滞后队列 = 3）"
} else { Bad "enter/exit 不成对或不足（enter $enters / exit $exits，应 ≥3 且相等）" }
if ($flow -match 'DamagePipeline\.exit\(\);') { Ok 'exit 在 finally 里（异常也不会把标记留在主线程上）' }
else { Bad 'exit 不在 finally 里 —— 一旦 hurt() 抛异常，主线程会永久处于"嵌套中"✗' }
# 防重入：本处理器自己也要在最开头早退
if ($flow -match 'if \(Boolean\.TRUE\.equals\(SPLITTING\.get\(\)\)\) return;') {
    Ok 'onLivingHurt 最开头有防重入早退（段不会被再拆一次）'
} else { Bad 'onLivingHurt 缺防重入早退' }
# 附加伤害只算一次：必须有"抬升量"快照 + 只在物理段发出
if ($flow -match 'takeMarkup\(event, total\)' -and $flow -match 'SNAPSHOT') {
    Ok '有"上游抬升量"快照（HIGHEST 记 → LOWEST 取，取值即删）'
} else { Bad '缺少"上游抬升量"快照 —— 附加伤害仍会被一起均分 ✗' }
if ($flow -match 'target\.hurt\(physicalSource\(target, attacker\), per \+ markup\)') {
    Ok '抬升量并入物理段、只发一次（不再参与均分）'
} else { Bad '抬升量没有"只发一次"的落点' }
if ($flow -match 'float per = base / segments;') { Ok '均分基数 = total − 抬升量（段数组成不变）' }
else { Bad '均分基数不是"total − 抬升量"' }

# --- ③ 全工程"会改数值"的处理器清单 ---
Write-Host "`n[3] 全工程伤害事件里『会改数值』的处理器：是否有 skipNested 闸门"
# 白名单：这些**不是增幅**（减伤/限伤/抹零/快照还原/真伤按原意顶开/换伤害类型），
# 故意不加闸门 ✓ —— 加了反而会改变既有语义 ✗。
$whitelist = @(
    'LifeLampRingHandler',   # 慈悲：按段截（设计如此：每一段都该被截 ✓）
    'DamageLimitEffect',     # 限伤（目标方保护）
    'MomoMerchantHandler',   # 墨默吟唱抗性 ×0.4（减伤）
    'DarkMetalMagicResistHandler', # 破法（减免）
    'ShikigamiHandler',      # 把被抹成 0 的伤害按"主人攻击力"恢复（绝对量，不是增幅）
    'TruePierce',            # 真伤按原意顶开数值
    'OrderOriginHandler',    # 秩序之初：取消 + 换成物理重打（改类型，不改数值）
    'ShikigamiBehavior'
)
# 扫描范围：所有含 LivingHurtEvent/LivingDamageEvent 的 java 文件，且文件里出现 setAmount 或"改了 amount"的写法
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
    # 该文件是否只做"快照还原"（LifeLampRing 的 restore 走 event::setAmount）
    $hasGuard = $t -match 'DamagePipeline\.skipNested\(\)'
    if ($whitelist -contains $f.BaseName) { $exempt += $f.BaseName; continue }
    if ($hasGuard) { $guarded += $f.BaseName } else { $unguarded += $f.BaseName }
}
Write-Host ("  有闸门（$($guarded.Count)）：" + (($guarded | Sort-Object -Unique) -join ', '))
Write-Host ("  白名单豁免（$($exempt.Count)）：" + (($exempt | Sort-Object -Unique) -join ', '))
if ($unguarded.Count -eq 0) { Ok '全部"会改数值"的处理器都有闸门 ✓' }
else { Bad ("以下文件会改伤害数值但**没有**闸门 ⇒ 会被逐段放大 ✗：" + (($unguarded | Sort-Object -Unique) -join ', ')) }

# 重点点名：用户报告里那几条（黑闪 / 群星之子 / 巫师套装 / 魔杖 / 投射咒法 / 堕落 / 幻兽琥珀 / 处刑 / 冷酷 / 炽热）
Write-Host "`n[4] 用户报告链路上的关键处理器逐一点名"
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

Write-Host ""
if ($fail -eq 0) { Write-Host "全部通过：bad 0"; exit 0 }
else { Write-Host "有 $fail 项未通过"; exit 1 }
