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
 * 供 {@link SuperTierPowerMixin}（范围 ×5）与 {@link SpellDurationMixin}（状态时长 ×3/5）使用。
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
 * <p>⚠ 字符串 target + 省略尾部参数（Mixin 允许回调只接前几个参数 ✓），
 * 这样就不必在编译期引用铁魔法的 {@code CastSource}/{@code MagicData} 类型 ✓。
 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell")
public class SuperTierCastContextMixin {

    /** 即时法术：castSpell 里就会走 onCast */
    @Inject(method = "castSpell", at = @At("HEAD"))
    private void tinkersnewlife$beginCast(Level level, int spellLevel, ServerPlayer player, CallbackInfo ci) {
        SuperTierMagicModifier.beginCast(this, player);
    }

    @Inject(method = "castSpell", at = @At("RETURN"))
    private void tinkersnewlife$endCast(Level level, int spellLevel, ServerPlayer player, CallbackInfo ci) {
        SuperTierMagicModifier.endCast();
    }

    /** 读条法术：效果在 onServerCastComplete 里 */
    @Inject(method = "onServerCastComplete", at = @At("HEAD"))
    private void tinkersnewlife$beginComplete(Level level, int spellLevel, LivingEntity entity, CallbackInfo ci) {
        SuperTierMagicModifier.beginCast(this, entity);
    }

    @Inject(method = "onServerCastComplete", at = @At("RETURN"))
    private void tinkersnewlife$endComplete(Level level, int spellLevel, LivingEntity entity, CallbackInfo ci) {
        SuperTierMagicModifier.endCast();
    }
}
