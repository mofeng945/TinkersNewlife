package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.FocusModifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 材料「秘银」特性·<b>专注</b>：手持带专注的工具时，<b>吟唱不会被打断</b>。
 *
 * <p><b>为什么要 mixin</b>：打断判定完全在铁魔法内部 ——
 * （反汇编）{@code AbstractSpell#canBeInterrupted(Player)}：
 * <pre>
 *   return getCastType() == CastType.LONG
 *       &amp;&amp; !ItemRegistry.CONCENTRATION_AMULET.get().isEquippedBy(player);
 * </pre>
 * 也就是说"长吟唱法术可被打断，除非戴着专注护符" ✗ —— 光靠事件拦不住（调用方是
 * ISS 自己的 {@code player.ServerPlayerEvents}），所以只能从这个方法的入口下手 ✓：
 * 玩家手持带专注的物品 → 直接返回 {@code false}（不可打断）✓
 *
 * <p>⚠ <b>用的是字符串 target</b>（{@code @Mixin(targets = "…")}）：这样不需要把铁魔法加进编译期依赖 ✓，
 * 铁魔法不在场时该 mixin 只会"未应用"（mixin 配置是 {@code required:false} + {@code defaultRequire:0} ✓），
 * 不会影响游戏 ✓。目标方法名是 **ISS 自己的方法名**（不被 SRG 重映射 ✓），所以不需要 refmap 条目 ✓。
 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell")
public class SpellInterruptFocusMixin {

    @Inject(method = "canBeInterrupted", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$focusPreventsInterrupt(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && FocusModifier.heldBy(player)) {
            cir.setReturnValue(false);
        }
    }
}
