package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.SuperTierMagicModifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 材料「魔金」特性·<b>超位魔法</b>：给"这一发是不是超位魔法"打上下文标记，
 * 供 {@link SuperTierPowerMixin}（范围 ×3）与 {@link SuperTierEffectMixin}（状态时长 ×2/3、治疗 ÷3）使用。
 *
 * <p><b>为什么挂在 {@code castSpell} / {@code onServerCastComplete} 而不是 {@code onCast}</b>：
 * 各法术会<b>重写 {@code onCast}</b>，而且不少是"先上效果、最后才调 {@code super.onCast}" ✗
 * （反汇编：深渊庇佑就是这样）—— 挂在 {@code onCast} 上会漏掉真正的效果结算时刻 ✗。
 * 而这两个方法是框架入口，实测<b>只有 {@code HeatSurgeSpell} 重写了 {@code onServerCastComplete}</b>、
 * <b>没有任何法术重写 {@code castSpell}</b> ✓（扫过全部 130+ 法术类）。
 *
 * <p>即时法术的效果发生在 {@code castSpell} 里、读条法术发生在 {@code onServerCastComplete} 里，
 * 两边都包上就全覆盖 ✓。标记是 {@code ThreadLocal}（服务端主线程结算）✓。
 *
 * <p>⚠⚠ <b>回调必须声明"完整的参数表"</b> ✗ —— Mixin 不接受省略尾部参数：
 * <pre>
 *   Mixin apply failed … InvalidInjectionException: Invalid descriptor
 *   Expected (Level;I;ServerPlayer;CastSource;Z;CallbackInfo)V
 *   but found (Level;I;ServerPlayer;CallbackInfo)V
 * </pre>
 * 而 {@code CastSource}/{@code MagicData} 是铁魔法的类型（没有编译期依赖 ✗）→
 * <b>用 {@code Object} 接</b> ✓（Mixin 允许回调参数取目标参数的超类型 ✓）。这两个参数我们本来也不用 ✓。
 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell")
public class SuperTierCastContextMixin {

    /** 即时法术：castSpell(Level, int, ServerPlayer, CastSource, boolean) 里就会走 onCast */
    @Inject(method = "castSpell", at = @At("HEAD"))
    private void tinkersnewlife$beginCast(Level level, int spellLevel, ServerPlayer player,
                                          Object castSource, boolean consumeMana, CallbackInfo ci) {
        SuperTierMagicModifier.beginCast(this, player);
    }

    @Inject(method = "castSpell", at = @At("RETURN"))
    private void tinkersnewlife$endCast(Level level, int spellLevel, ServerPlayer player,
                                        Object castSource, boolean consumeMana, CallbackInfo ci) {
        SuperTierMagicModifier.endCast();
    }

    /** 读条法术：onServerCastComplete(Level, int, LivingEntity, MagicData, boolean) 里执行效果 */
    @Inject(method = "onServerCastComplete", at = @At("HEAD"))
    private void tinkersnewlife$beginComplete(Level level, int spellLevel, LivingEntity entity,
                                              Object magicData, boolean cancelled, CallbackInfo ci) {
        SuperTierMagicModifier.beginCast(this, entity);
    }

    @Inject(method = "onServerCastComplete", at = @At("RETURN"))
    private void tinkersnewlife$endComplete(Level level, int spellLevel, LivingEntity entity,
                                            Object magicData, boolean cancelled, CallbackInfo ci) {
        SuperTierMagicModifier.endCast();
    }
}
