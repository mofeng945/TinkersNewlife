# 静态自检：命灯指轮的三道闸门（伤害两关 + 死亡兜底）注册是否正确、口径是否一致。
#
# 为什么要有它：命灯指轮"打不死"的语义全靠【监听器优先级 + 判定口径】两件事撑着，
# 而这两件事在源码里都是"注解/字符串"，编译期不会报错、跑错了也只是静默失效 ✗。
# 本脚本把这两点钉死（不需要开游戏）：
#   ① LifeLampRingHandler 必须同时挂 LivingHurtEvent / LivingDamageEvent（LOWEST，最后跑）
#      与 LivingDeathEvent（HIGHEST，最先跑）；
#   ② 三道闸门必须都走同一个真伤判定（TruePierce.isTruePierce）—— 否则"穿透不受慈悲约束"
#      会出现"有时放行、有时被拦"（备忘录 §224 的教训）；
#   ③ 兜底的凭证必须在两关伤害里都记（只记一关 ⇒ 另一关被绕过时兜不住）；
#   ④ 顺带列出全工程所有 LOWEST 伤害监听器（同优先级靠注册顺序，谁在我们之后改数值就看得见）。
#
# 用法： powershell -ExecutionPolicy Bypass -File tools\check-life-lamp-gates.ps1
# 退出码：0 = 全过；1 = 有断言失败。

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$lampPath = Join-Path $root 'src\main\java\com\mofengbaizhi\tinkersnewlife\content\curse\LifeLampRingHandler.java'

$fail = 0
function Ok($m)   { Write-Host "  [OK]   $m" }
function Bad($m)  { Write-Host "  [FAIL] $m"; $script:fail++ }

Write-Host "== 命灯指轮闸门静态自检 =="

if (-not (Test-Path -LiteralPath $lampPath)) { Bad "找不到 $lampPath"; exit 1 }
$lamp = Get-Content -LiteralPath $lampPath -Raw

# --- ① 三道闸门的监听器 ---
Write-Host "`n[1] 三道闸门的事件与优先级"
$gates = @(
    @{ Name = 'onLivingHurt';   Event = 'LivingHurtEvent';   Priority = 'LOWEST'  },
    @{ Name = 'onLivingDamage'; Event = 'LivingDamageEvent'; Priority = 'LOWEST'  },
    @{ Name = 'onLivingDeath';  Event = 'LivingDeathEvent';  Priority = 'HIGHEST' }
)
foreach ($g in $gates) {
    # 匹配：@SubscribeEvent(priority = EventPriority.X)\n public static void NAME(EVENT ...)
    $pattern = '@SubscribeEvent\(priority\s*=\s*EventPriority\.' + $g.Priority + '\)\s*' +
               '(?:\r?\n)\s*public\s+static\s+void\s+' + $g.Name + '\s*\(\s*' + $g.Event + '\s'
    if ($lamp -match $pattern) { Ok "$($g.Name)($($g.Event)) @ $($g.Priority)" }
    else { Bad "$($g.Name) 未按 $($g.Priority) 挂到 $($g.Event)" }
}

# --- ② 真伤口径一致 ---
Write-Host "`n[2] 三道闸门共用同一个真伤判定"
if ($lamp -match 'private static boolean isTruePierce\(DamageSource') {
    Ok '有 isTruePierce(DamageSource) 小工具（口径唯一入口）'
} else { Bad '缺少 isTruePierce(DamageSource) 小工具 —— 口径散落各处，迟早不一致' }
if ($lamp -match 'TruePierce\.isTruePierce\(') { Ok '转发到 util/TruePierce.isTruePierce（与穿透侧同源）' }
else { Bad '没有转发到 TruePierce.isTruePierce —— 可能自己写了一套判定' }
$tpCalls = ([regex]::Matches($lamp, 'if \(isTruePierce\(event\.getSource\(\)\)\) return;')).Count
if ($tpCalls -ge 3) { Ok "三道闸门都有真伤早退（命中 $tpCalls 处，≥3）" }
else { Bad "真伤早退只有 $tpCalls 处，应为 3（Hurt / Damage / Death 各一）" }

# --- ③ 兜底凭证 ---
Write-Host "`n[3] 死亡兜底的凭证记录"
$rememberCalls = ([regex]::Matches($lamp, 'rememberLethal\(target, event\.getAmount\(\)\)')).Count
if ($rememberCalls -ge 2) { Ok "两关伤害都记了致死凭证（命中 $rememberCalls 处）" }
else { Bad "致死凭证只记了 $rememberCalls 处，应 ≥2" }
if ($lamp -match 'recentlyLethallyHit\(victim\)') { Ok '兜底用 recentlyLethallyHit 取凭证' }
else { Bad '兜底没有取凭证 —— 会变成"谁死都救"' }
if ($lamp -match 'event\.setCanceled\(true\)') { Ok '兜底会取消死亡事件' } else { Bad '兜底没有取消死亡事件' }
if ($lamp -match 'victim\.setHealth\(keep\)') { Ok '兜底会把血补回慈悲线（否则怪停在 0 血不会走 die）' }
else { Bad '兜底没有把血补回来 —— 取消死亡后实体会卡在 0 血' }

# --- ④ 全工程 LOWEST 伤害监听器清单（排查"谁可能在我们之后改数值"） ---
Write-Host "`n[4] 全工程 LOWEST 优先级的伤害/死亡监听器（同优先级 = 靠注册顺序）"
$src = Join-Path $root 'src\main\java'
$rows = @()
Get-ChildItem -LiteralPath $src -Recurse -Filter '*.java' | ForEach-Object {
    $t = Get-Content -LiteralPath $_.FullName -Raw
    $ms = [regex]::Matches($t,
        '@SubscribeEvent\(priority\s*=\s*EventPriority\.(?<p>\w+)\)\s*(?:\r?\n)\s*public\s+static\s+void\s+(?<m>\w+)\s*\(\s*(?<e>\w+)\s')
    foreach ($m in $ms) {
        if ($m.Groups['p'].Value -ne 'LOWEST') { continue }
        if ($m.Groups['e'].Value -notmatch 'Living(Hurt|Damage|Death|Attack)Event') { continue }
        $rows += [pscustomobject]@{
            File = $_.Name
            Method = $m.Groups['m'].Value
            Event = $m.Groups['e'].Value
        }
    }
}
$rows | Sort-Object Event, File | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
Write-Host "  共 $($rows.Count) 个；它们之间没有顺序保证 ⇒ 命灯的语义不能只靠"抢在最后" ✓"

# --- 结果 ---
Write-Host ""
if ($fail -eq 0) { Write-Host "全部通过：bad 0"; exit 0 }
else { Write-Host "有 $fail 项未通过"; exit 1 }
