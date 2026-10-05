package com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook;

// 移植自 TiCEX (MIT): moffy.ticex.lib.hook.TicEXModifierHooks

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.module.ModuleHook;

/**
 * TiCEX {@code TicEXModifierHooks} 的等价物 —— 我们这一轮只搬了拔刀剑用得上的三个钩子 ✓。
 *
 * <p>它只是"钩子持有类" ✓，真正的注册在 {@link #register()} 里（用<b>匠魂自己的</b>
 * {@code ModifierHooks.LOADER} 注册器 ✓，照 TiCEX {@code modules/general/TicEXModule.java} 第 81~120 行的写法 ✓）。
 *
 * <p>⚠ <b>注册时机</b>：必须在修饰符被反序列化（= 修饰符注册表事件）**之前** ✓ ⇒
 * 在模组构造器里、{@code Modifiers.MODIFIERS.register(...)} 之前调用 ✓。
 * 否则修饰符在构造时 {@code entry.getHook(KatanaModifierHooks.EMBOSSMENT)} 会拿到 null ✗。
 *
 * <p>⚠ 与 TiCEX 的差异（如实记录 ✓）：TiCEX 的 {@code TicEXModifierHooks} 还有
 * {@code PROPERTY_PROVIDER}（提供属性）与 {@code ENERGY}（能量）两个钩子 ✗ ——
 * 它们服务于 TiCEX 的护甲/能量体系，**与拔刀剑无关** ⇒ 本轮不搬 ✓。
 */
public class KatanaModifierHooks {

    /** 装裱（把另一个物品的数据贴到工具上） */
    public static ModuleHook<EmbossmentModifierHook> EMBOSSMENT = null;
    /** 改写伤害来源 */
    public static ModuleHook<DamageSourceModifierHook> DAMAGE_SOURCE = null;
    /** 暴击判定与倍率 */
    public static ModuleHook<CriticalModifierHook> CRITICAL = null;

    private KatanaModifierHooks() {
    }

    /**
     * 注册三个钩子（幂等 ✓ 重复调用直接返回 ✓）。
     *
     * @return 是否真的注册了（第一次调用为 true ✓）
     */
    public static boolean register() {
        if (EMBOSSMENT != null) {
            return false;
        }
        EMBOSSMENT = ModifierHooks.LOADER.register(
                new ModuleHook<>(
                        TinkersNewlife.prefix("embossment"),
                        EmbossmentModifierHook.class,
                        EmbossmentModifierHook.AllMerger::new,
                        new EmbossmentModifierHook.DefaultClass()
                )
        );
        DAMAGE_SOURCE = ModifierHooks.LOADER.register(
                new ModuleHook<>(
                        TinkersNewlife.prefix("modify_damage_source"),
                        DamageSourceModifierHook.class,
                        DamageSourceModifierHook.AllMerger::new,
                        new DamageSourceModifierHook.DefaultClass()
                )
        );
        CRITICAL = ModifierHooks.LOADER.register(
                new ModuleHook<>(
                        TinkersNewlife.prefix("critical"),
                        CriticalModifierHook.class,
                        CriticalModifierHook.AllMerger::new,
                        new CriticalModifierHook.DefaultClass()
                )
        );
        TinkersNewlife.LOGGER.info("[拔刀剑] 已注册匠魂修饰符钩子：embossment / modify_damage_source / critical");
        return true;
    }
}
