package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.OriginMagicModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 材料「源钻合金」特性·<b>奥术始源</b>：<b>无需学习即可使用未学会的邪术</b> ✓。
 *
 * <p>铁魔法的学习判定入口是 {@code AbstractSpell#isLearned(Player)}（反汇编确认 ✓）——
 * 它同时被"能不能施放"和"法术信息是否被打码"两处使用，所以从这一个点入手，
 * 两件事一起解决 ✓：带着奥术始源时，<b>邪术学派的法术一律视为已学会</b> ✓。
 *
 * <p>仅对<b>邪术</b>（{@code irons_spellbooks:eldritch}）放开 ✓，其它学派仍然要正常学习 ✓。
 *
 * <p>⚠ 字符串 target（不需要把铁魔法加进编译期依赖 ✓）；铁魔法不在场时只是"未应用" ✓。
 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell")
public class EldritchLearningMixin {

    @Inject(method = "isLearned", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$originMagicAllowsEldritch(Player player, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (player == null) return;
            if (!OriginMagicModifier.wornBy(player)) return;
            if (IronSpellsSpellAccess.isEldritch(this)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
            // 出错就沿用铁魔法自己的判定
        }
    }
}
