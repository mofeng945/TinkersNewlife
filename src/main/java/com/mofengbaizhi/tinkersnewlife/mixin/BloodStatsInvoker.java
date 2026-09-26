package com.mofengbaizhi.tinkersnewlife.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把血族（Vampirism）的 {@code BloodStats#addBlood(int, float)} **暴露出来**的 Mixin 接口。
 *
 * <p>为什么必须走 Mixin：该方法是**包级私有**的（`int addBlood(int, float)`，见反编译 ✓），
 * 血族自己的公开 API {@code IBloodStats} 只有 getter（{@code getBloodLevel/getMaxBlood/...} ✗），
 * 而"贵族餐饮"要往血液值里加东西 ⇒ 只能由 Mixin 生成一个同包内的调用者 ✓。
 *
 * <p>写法口径与 {@code JadeObjectNameMixin} / {@code XaeroRadarMixin} 一致：用**字符串 targets**
 * 指向第三方类（本模组没有它的编译期强绑定问题，也不需要 refmap ✓）；因为血族类名/方法名都不混淆，
 * 所以**一份**就能同时适配开发环境与生产环境 ✓（不像 {@code ServerPlayerDeathMixin} 要写官方名+SRG名两份 ✗）。
 *
 * <p>⚠ 没装血族时：本 Mixin 因 {@code defaultRequire=0} 静默不生效 ⇒ 目标类不存在时不会崩 ✓；
 * 而 {@code stats instanceof BloodStatsInvoker} 会判 false，调用方直接放弃写入 ✓。
 */
@Mixin(targets = "de.teamlapen.vampirism.entity.player.vampire.BloodStats")
public interface BloodStatsInvoker {

    /**
     * 原方法：{@code int addBlood(int amount, float saturationMultiplier)}
     * （内部会把 amount 夹到"上限 − 当前值"，并把 {@code amount × multiplier × 2} 加到饱和度上 ✓）。
     *
     * @param amount               要加的血液值
     * @param saturationMultiplier 饱和度系数
     * @return 实际加进去的血液值
     */
    @Invoker("addBlood")
    int tinkersnewlife$addBlood(int amount, float saturationMultiplier);
}
