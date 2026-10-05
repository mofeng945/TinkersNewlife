package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;

/**
 * <b>§1025 临时调试开关</b>（排查"打不到怪 / 只有第一段"用 ✓）。
 *
 * <p>为什么需要它：已确认 ①六个 mixin 都注入了 ✓ ②标签齐全 ✓ ③物品/能力/属性/同步都照 TiCEX 抄了 ✓，
 * 但实测仍不行 ✗ ⇒ 必须用**实测数据**定位链路到底断在哪一环 ✗✓。
 * 于是沿"玩家攻击 → 本体结算"这条链埋三个点 ✓，打一次怪看日志即可判断：
 * <ol>
 *   <li>{@code KatanaItem.onLeftClickEntity} ⇒ 本体到底认不认这把刀（返回值/状态是否存在 ✓）；</li>
 *   <li>{@code KatanaAttackManagerMixin.doAttackWith} ⇒ 本体的范围/技能结算有没有走到 ✓；</li>
 *   <li>{@code KatanaAttackHelperMixin.applyAttackDamage} ⇒ 本体那一击的伤害有没有经过我们（匠魂数值）✓；</li>
 *   <li>{@link KatanaBladeSyncEvents#syncState} ⇒ 同步时机有没有触发 ✓（受伤/击杀/刀动作 ✓）。</li>
 * </ol>
 * ⚠ <b>排查完把 {@link #ON} 改回 false（或整类删掉）</b> ✓ —— 它只是临时诊断 ✗。
 */
public final class KatanaDebug {

    /**
     * 总开关 ✓：排查期间为 true ✓（改回 false 即可静默 ✓）。
     *
     * <p>§1034（拔刀剑整体搁置）：已改回 {@code false} ✓ —— 类与全部埋点**保留** ✗ 不删 ✓，
     * 后续要接着排查时把这里改回 {@code true} 即可 ✓（恢复清单见备忘录 §1034）。
     */
    public static final boolean ON = false;

    private KatanaDebug() {
    }

    /**
     * 打一条带统一前缀的调试日志 ✓（便于在日志里一把搜 `[拔刀剑调试]` ✓）。
     *
     * @param message 内容 ✓
     */
    public static void log(String message) {
        if (!ON) {
            return;
        }
        try {
            TinkersNewlife.LOGGER.info("[拔刀剑调试] " + message);
        } catch (Throwable ignored) {
            // 连日志都写不出去就算了 ✓ 绝不能因为调试代码把游戏搞崩 ✗
        }
    }
}
