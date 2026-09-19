package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.AllPathsOneTrait;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 巫师套装特性·<b>万法有道</b>的<b>法力侧</b>：让穿着巫师套的玩家"法力不够"时也能施法 ✓，
 * 差额按「咒力 → 灵魂」垫付 ✓（用户口径 ✓）。
 *
 * <h2>为什么挂这两个方法</h2>
 * 铁魔法在<b>多处</b>直接读 {@code MagicData.getMana()} 判"够不够"（`CastingItem` 的客户端预判、
 * {@code AbstractSpell} 的 {@code cast_error_mana} 那一关 ✓），并没有一个统一的
 * "能不能付得起"事件可挂 ✗。所以：
 * <ol>
 *   <li>{@code getMana()}：在<b>服务端</b>额外加上"还能垫出多少" ✓ ⇒ 所有判定自然通过 ✓；</li>
 *   <li>{@code setMana(float)}：扣费写回时把账做正 ✓ —— 见下 ✓。</li>
 * </ol>
 *
 * <h2>setMana 的统一修正（关键 ✓）</h2>
 * 铁魔法的写法是 {@code setMana(getMana() - cost)} ✓、回蓝是 {@code setMana(getMana() + regen)} ✓
 * ⇒ 传进来的值里可能<b>含着我们膨胀的那部分</b>。记下膨胀量 {@code pool} 后：
 * <pre>
 *   real = 传入值 - pool            // 调用方"本来想要"的真实法力 ✓
 *   real >= 0 ⇒ 直接写（正常扣费/回蓝都走这条 ✓ 不会再凭空变出法力 ✗）
 *   real &lt; 0 ⇒ 缺口 = -real，交给「咒力→灵魂」垫 ✓，写回 max(0, 垫到的 - 缺口)
 * </pre>
 * ⚠ 膨胀量只在<b>同一 tick</b>内认账 ✓（读条法术的"检查"与"扣费"可能隔几十 tick ✗，
 * 那时 pool 已作废 ⇒ 走上面 real&lt;0 那条路 ✓ 两条路都算对 ✓）。
 * ⚠ {@code @Shadow serverPlayer} 为 null 的实例是<b>客户端</b>的 MagicData ✓ ⇒ 客户端
 * 法力条/客户端预判完全不受影响 ✓（显示永远是真的 ✓）。
 *
 * <p>目标用 {@code targets = "包名"} 字符串而不是类字面量 ✓ —— 没装铁魔法时本 mixin
 * 不加载也不会 NoClassDefFoundError ✓（config {@code required:false} ✓）。
 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.magic.MagicData")
public class AllPathsOneManaMixin {

    @Shadow
    private float mana;

    @Shadow
    private ServerPlayer serverPlayer;

    /** 上一次 {@code getMana()} 膨胀了多少（同一 tick 内有效 ✓） */
    @Unique
    private float tnAllPathsPool;

    @Unique
    private int tnAllPathsPoolTick = -1;

    @Inject(method = "getMana", at = @At("RETURN"), cancellable = true, remap = false)
    private void tnAllPathsInflate(CallbackInfoReturnable<Float> cir) {
        this.tnAllPathsPool = 0.0F;
        this.tnAllPathsPoolTick = -1;
        if (AllPathsOneTrait.RAW_MANA.get()) return;          // 我们自己读真实值 ✓
        if (this.serverPlayer == null) return;                // 客户端 ✓ 不碰
        if (!AllPathsOneTrait.active(this.serverPlayer)) return;
        float pool = AllPathsOneTrait.manaPool(this.serverPlayer);
        if (pool <= 0.0F) return;
        this.tnAllPathsPool = pool;
        this.tnAllPathsPoolTick = this.serverPlayer.tickCount;
        cir.setReturnValue(cir.getReturnValueF() + pool);
    }

    @ModifyVariable(method = "setMana", at = @At("HEAD"), argsOnly = true, remap = false)
    private float tnAllPathsSettle(float requested) {
        if (AllPathsOneTrait.RAW_MANA.get()) return requested;   // 我们自己的写入 ✓ 原样
        if (this.serverPlayer == null) return requested;
        float pool = 0.0F;
        if (this.tnAllPathsPool > 0.0F && this.tnAllPathsPoolTick == this.serverPlayer.tickCount) {
            pool = this.tnAllPathsPool;
        }
        this.tnAllPathsPool = 0.0F;
        this.tnAllPathsPoolTick = -1;

        float real = requested - pool;                        // 调用方意图的真实值 ✓
        if (real >= 0.0F) return real;                        // 正常（含回蓝 ✓）
        if (!AllPathsOneTrait.active(this.serverPlayer)) return 0.0F;   // 没特性 ⇒ 夹 0（原本也不会为负 ✓）

        int missing = (int) Math.ceil(-real);
        int paid = AllPathsOneTrait.payManaShortfall(this.serverPlayer, missing);
        return (float) Math.max(0, paid - missing);           // 垫够了 ⇒ 0 ✓ 垫多了 ⇒ 余数 ✓
    }
}
