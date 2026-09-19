package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.AllPathsOneTrait;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 巫师套装特性·<b>万法有道</b>的<b>灵魂侧</b>：穿着巫师套的玩家"灵魂不够"时也能放诡厄法术 ✓，
 * 差额按「咒力 → 法力」垫付 ✓（用户口径 ✓）。
 *
 * <h2>为什么是"检查 + 扣除"两个点</h2>
 * 诡厄的写法是<b>先问够不够、再扣</b>（{@code SEHelper.getSoulsAmount(player, cost)} 返回 boolean ✓，
 * 见它自己的 {@code DarkWand} / {@code ItemHelper}）⇒ 只在"检查"里垫费不行 ✗（可能查了却不扣 ✗，
 * 罗袍/护符那些阈值检查会被白蹭 ✓）。所以：
 * <ul>
 *   <li><b>检查</b>（{@code getSoulsAmount}）：只<b>预测</b>能不能垫 ✓ 不扣任何资源 ✓ ——
 *       且限定"手上拿着诡厄物品"（施法场景 ✓），把罗袍 / 护符 / 灵魂收集事件那些非施法检查排除掉 ✓；</li>
 *   <li><b>扣除</b>（{@code decreaseSESouls} / {@code decreaseSouls}）：这时才真扣咒力/法力 ✓，
 *       并把垫到的灵魂<b>加回去</b> ⇒ 诡厄自己那次扣除就能正常成功 ✓（净效果：灵魂用光 + 咒力/法力被扣 ✓）。</li>
 * </ul>
 *
 * <p>方法名用<b>字面名 + {@code remap = false}</b> ✓ —— 诡厄是普通 mod（没有混淆 ✓），
 * 名字就是 {@code getSoulsAmount} / {@code decreaseSESouls} / {@code decreaseSouls}
 * （已用 {@code javap} 核对过签名 ✓）。目标同样写成 {@code targets="包名"} 字符串 ✓（软依赖 ✓）。
 */
@Mixin(targets = "com.Polarice3.Goety.utils.SEHelper")
public class AllPathsOneSoulMixin {

    /** 灵魂够不够（预测：够垫就直接放行 ✓ 不扣费 ✓） */
    @Inject(method = "getSoulsAmount", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tnAllPathsSoulCheck(Player player, int amount, CallbackInfoReturnable<Boolean> cir) {
        if (player == null || amount <= 0) return;
        if (player.level().isClientSide) return;
        if (AllPathsOneTrait.soulsOf(player) >= amount) return;      // 本来够 ⇒ 交给原方法 ✓
        if (!AllPathsOneTrait.active(player)) return;
        if (!AllPathsOneTrait.holdingGoetyItem(player)) return;      // 只在施法场景 ✓
        double cover = AllPathsOneTrait.soulsOf(player)
                + AllPathsOneTrait.curseAsSouls(AllPathsOneTrait.curseOf(player))
                + AllPathsOneTrait.manaAsSouls(Math.max(0, AllPathsOneTrait.realManaOf(player)));
        if (cover >= amount) cir.setReturnValue(Boolean.TRUE);
    }

    /** 真扣灵魂前先垫够（咒力 → 法力 ✓） */
    @Inject(method = "decreaseSESouls", at = @At("HEAD"), remap = false)
    private static void tnAllPathsTopUp(Player player, int amount, CallbackInfoReturnable<Boolean> cir) {
        AllPathsOneTrait.topUpSoulsFor(player, amount);
    }

    /** 同上，void 版（另一条扣灵魂的入口 ✓） */
    @Inject(method = "decreaseSouls", at = @At("HEAD"), remap = false)
    private static void tnAllPathsTopUpVoid(Player player, int amount, CallbackInfo ci) {
        AllPathsOneTrait.topUpSoulsFor(player, amount);
    }
}
