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
 * 巫师套装特性·<b>万法有道</b>的<b>法力侧</b>：穿着巫师套的玩家"法力不够"时也能施法 ✓，
 * 差额按「咒力 → 灵魂」垫付 ✓（用户口径 ✓）。
 *
 * <h2>为什么挂这两个方法</h2>
 * 铁魔法在许多处直接读 {@code MagicData.getMana()} 判"够不够"（我们自己的
 * {@code IronSpellsReflector.tryCastSpell} ✓、铁魔法 {@code AbstractSpell} 的
 * {@code ui.irons_spellbooks.cast_error_mana} 那一关 ✓），没有统一的"付得起"事件可挂 ✗。所以：
 * <ol>
 *   <li>{@code getMana()}：<b>服务端</b>且<b>手里拿着施法物品</b>时，额外加上"还能垫多少" ✓
 *       ⇒ 各处判定自然通过 ✓；</li>
 *   <li>{@code setMana(float)}：扣费写回时把账做正 ✓（见下）。</li>
 * </ol>
 *
 * <h2>setMana 的统一修正</h2>
 * 铁魔法的写法是 {@code setMana(getMana() - cost)} ✓、回蓝是 {@code setMana(getMana() + regen)} ✓
 * ⇒ 传进来的值里可能含着我们膨胀的部分。记下本 tick 的膨胀量 {@code pool} 后：
 * <pre>
 *   real = 传入值 - pool      // 调用方"本来想要"的真实法力
 *   real &gt;= 0 ⇒ 直接写（正常扣费 / 回蓝都对 ✓ 不会凭空变出法力 ✗）
 *   real &lt; 0 ⇒ 缺口交给「咒力 → 灵魂」垫 ✓ 写回 max(0, 垫到 - 缺口)
 * </pre>
 *
 * <h2>⚠ 安全铁律（§379 / §380）</h2>
 * <ul>
 *   <li>两个处理体全部 <b>try/catch(Throwable)</b> ✓ 出错就<b>原样返回</b>（等于没装 ✓）
 *       —— 绝不允许可选特性把施法流程带崩 ✗；</li>
 *   <li>膨胀只在 {@code serverPlayer != null}（服务端 ✓）且<b>手里拿着施法物品</b>时发生 ✓
 *       ⇒ 客户端法力条 / 非施法场景的服务端读数完全不受影响 ✓。</li>
 * </ul>
 *
 * <p>目标用 {@code targets = "包名"} 字符串 ✓ —— 没装铁魔法时本 mixin 不加载也不会
 * NoClassDefFoundError ✓（config {@code required:false} ✓）。
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
        try {
            this.tnAllPathsPool = 0.0F;
            this.tnAllPathsPoolTick = -1;
            if (AllPathsOneTrait.RAW_MANA.get()) return;          // 我们自己读真实值 ✓
            if (this.serverPlayer == null) return;                // 客户端 ✓ 不碰
            if (!AllPathsOneTrait.active(this.serverPlayer)) return;
            if (!AllPathsOneTrait.holdingCastItem(this.serverPlayer)) return;   // 只在施法场景 ✓
            float pool = AllPathsOneTrait.manaPool(this.serverPlayer);
            if (pool <= 0.0F) return;
            this.tnAllPathsPool = pool;
            this.tnAllPathsPoolTick = this.serverPlayer.tickCount;
            cir.setReturnValue(cir.getReturnValueF() + pool);
        } catch (Throwable ignored) {
            // fail-safe：出错就当没膨胀 ✓（判定按真实法力走 ⇒ 与没装特性时完全一致 ✓）
        }
    }

    @ModifyVariable(method = "setMana", at = @At("HEAD"), argsOnly = true, remap = false)
    private float tnAllPathsSettle(float requested) {
        try {
            if (AllPathsOneTrait.RAW_MANA.get()) return requested;   // 我们自己的写入 ✓ 原样
            if (this.serverPlayer == null) return requested;
            float pool = 0.0F;
            if (this.tnAllPathsPool > 0.0F && this.tnAllPathsPoolTick == this.serverPlayer.tickCount) {
                pool = this.tnAllPathsPool;
            }
            this.tnAllPathsPool = 0.0F;
            this.tnAllPathsPoolTick = -1;

            float real = requested - pool;
            if (real >= 0.0F) return real;                       // 正常（含回蓝 ✓）
            if (!AllPathsOneTrait.active(this.serverPlayer)) return 0.0F;

            int missing = (int) Math.ceil(-real);
            int paid = AllPathsOneTrait.settleManaShortfall(this.serverPlayer, missing);
            return (float) Math.max(0, paid - missing);          // 垫够 ⇒ 0 ✓
        } catch (Throwable ignored) {
            return requested;                                    // fail-safe：原样 ✓
        }
    }
}
