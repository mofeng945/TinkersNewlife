package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.item.SwordType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>拔刀剑（重锋）物品栏渲染优化</b>（§871 用户口径 C ✓）。
 *
 * <h2>为什么卡（反编译实查 ✓ `SlashBladeTEISR.java` / `BladeRenderState.java` ✓）</h2>
 * GUI 里每渲染一把刀（每帧、每一格）都要：
 * <ol>
 *   <li>{@code SwordType.from(stack)} —— 每次都重新解析栈状态（`SlashBladeTEISR.java:122` ✓）；</li>
 *   <li>两次 {@code stack.getCapability(BLADESTATE)} ＋ Optional 链（`:123` `:125` ✓）；</li>
 *   <li>三次 {@code BladeModelManager.getModel(...)} 查表（`:124` `:130` ✓）；</li>
 *   <li><b>最贵的一条</b>：{@code BladeRenderState.renderOverrided(...)} <b>再</b>
 *       {@code renderOverridedLuminous(...)}（`:127` `:128` ✓）——同一把刀的**几何体画两遍** ✗
 *       （第二遍是刀身发光/流光层 ✓）。</li>
 * </ol>
 * ⇒ 一箱 36 格全是刀时，每帧 ≈ 72 次 Wavefront 绘制 ＋ 上百次 capability/NBT 解析 ⇒ 帧率崩 ✗。
 *
 * <h2>本 mixin 做什么</h2>
 * <ul>
 *   <li><b>缓存</b>（{@link #tnl$swordTypes} ✓）：把 {@code SwordType.from(stack)} 的结果按**栈实例**缓存 ✓
 *       （同一格里的 ItemStack 实例是稳定的 ✓），省掉每帧的重复解析 ✓；</li>
 *   <li><b>拥挤时跳发光层</b>（{@link #tnl$maybeSkipLuminous} ✓）：掐掉 {@code renderOverridedLuminous} 那一句 ✓ ——
 *       当**同一瞬间**正在渲染的刀数超过 {@code luminous_crowd}（默认 6 ✓）时直接不画第二遍 ✓
 *       ⇒ 刀身基本样子不变 ✓ 只是大量刀同屏时不再有那层流光 ✓（阈值 0 = 永不跳 ✓ 恢复原样 ✓）。</li>
 * </ul>
 * <p>拥挤判定用"最近 50 ms 的渲染次数"✓（拿不到稳定的每帧钩子 ✗ 用时间窗更稳 ✓ 代价是判定略糙 ✓ 如实记录 ✓）。
 * <p>⚠ 全程 try/catch ＋ 配置读不到就用默认值 ✓；**只影响客户端画面** ✓ 不影响服务器 ✓。
 * <p>⚠⚠ §874 <b>踩过的坑</b>：两个 {@code @Redirect} <b>处理器不能是 static</b> ✗ ——
 * 目标 {@code renderIcon(...)} 是**实例方法** ✓，处理器写成 static 会让 Mixin 直接
 * {@code InvalidInjectionException: 'static' modifier of handler method does not match target} ✗
 * 而<b>静默失效</b>（`defaultRequire: 0` ⇒ 只打 WARN 不崩 ✓ 但也一点作用都没有 ✗）；
 * 现在两个处理器都是**实例方法** ✓（{@code tnl$report()} 仍是 static ✓ 它不碰实例状态 ✓）。
 */
@Mixin(targets = "mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR", remap = false)
public class SlashBladeRenderOptMixin {

    /** §872 诊断日志（用户口径 ✓ 生效与否必须能看出来 ✓） */
    @Unique
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("TinkersNewlife/SlashBladeOpt");

    /** 栈实例 → 剑类型集合（缓存 ✓ 容量上限兜底 ✓） */
    @Unique
    private static final Map<ItemStack, EnumSet<SwordType>> tnl$swordTypes = new ConcurrentHashMap<>();

    /** 最近 50 ms 里渲染了多少把刀 ✓ */
    @Unique
    private static final java.util.concurrent.atomic.AtomicInteger tnl$recent =
            new java.util.concurrent.atomic.AtomicInteger();

    @Unique
    private static volatile long tnl$windowStart = System.nanoTime();

    // ── §872 诊断（用户口径：不许静默失效 ⇒ 生效／没生效一眼看出来 ✓）──
    /** 两个 redirect 是否都被调用过（＝ mixin 真的 apply 了 ✓） */
    @Unique
    private static volatile boolean tnl$provedTypeCache = false;
    @Unique
    private static volatile boolean tnl$provedLuminous = false;
    @Unique
    private static final java.util.concurrent.atomic.AtomicLong tnl$cacheHit =
            new java.util.concurrent.atomic.AtomicLong();
    @Unique
    private static final java.util.concurrent.atomic.AtomicLong tnl$cacheMiss =
            new java.util.concurrent.atomic.AtomicLong();
    @Unique
    private static final java.util.concurrent.atomic.AtomicLong tnl$lumSkipped =
            new java.util.concurrent.atomic.AtomicLong();
    @Unique
    private static final java.util.concurrent.atomic.AtomicLong tnl$lumKept =
            new java.util.concurrent.atomic.AtomicLong();
    @Unique
    private static volatile long tnl$lastReport = System.nanoTime();

    /** §872：每 5 秒打一行汇总（只在真有渲染时打 ✓ 平时不刷屏 ✓） */
    @Unique
    private static void tnl$report() {
        long now = System.nanoTime();
        if (now - tnl$lastReport < 5_000_000_000L) return;
        tnl$lastReport = now;
        long hit = tnl$cacheHit.getAndSet(0);
        long miss = tnl$cacheMiss.getAndSet(0);
        long skip = tnl$lumSkipped.getAndSet(0);
        long keep = tnl$lumKept.getAndSet(0);
        if (hit + miss + skip + keep == 0) return;            // 这 5 秒没渲染刀 ⇒ 不刷屏 ✓
        int crowd = -1;
        boolean on = true;
        try {
            on = ModConfig.slashbladeRenderOpt();
            crowd = ModConfig.slashbladeLuminousCrowd();
        } catch (Throwable ignored) {
        }
        LOGGER.info("[拔刀剑优化] 近 5 秒：剑类型缓存 命中 {} / 未命中 {}；发光层 跳过 {} / 保留 {}（开关 {}，阈值 {}）",
                hit, miss, skip, keep, on ? "开" : "关", crowd);
    }

    /** 把一个栈算进的"本窗口渲染次数"＋1，并返回当前窗口计数（跨窗口自动归零 ✓） */
    @Unique
    private static int tnl$tickWindow() {
        long now = System.nanoTime();
        if (now - tnl$windowStart > 50_000_000L) {          // 50 ms ⇒ 约 3 帧（20fps）／1~3 帧（60fps）
            tnl$windowStart = now;
            tnl$recent.set(0);
        }
        return tnl$recent.incrementAndGet();
    }

    /** {@code SwordType.from(stack)} ⇒ 缓存版（返回**同一份** EnumSet 的只读拷贝 ✓ 避免调用方改到缓存 ✓） */
    @Redirect(method = "renderIcon(Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFZ)V",
              at = @At(value = "INVOKE",
                      target = "Lmods/flammpfeil/slashblade/item/SwordType;from(Lnet/minecraft/world/item/ItemStack;)Ljava/util/EnumSet;"),
              remap = false)
    private EnumSet<SwordType> tnl$swordTypes(ItemStack stack) {
        try {
            if (!ModConfig.slashbladeRenderOpt()) return SwordType.from(stack);
            if (!tnl$provedTypeCache) {
                tnl$provedTypeCache = true;
                LOGGER.info("[拔刀剑优化] ✓ mixin 生效：SwordType.from 已接管（缓存启用）");
            }
            if (tnl$swordTypes.size() > 512) tnl$swordTypes.clear();      // 兜底：别无限涨 ✗
            tnl$report();
            EnumSet<SwordType> cached = tnl$swordTypes.get(stack);
            if (cached == null) {
                tnl$cacheMiss.incrementAndGet();
                cached = SwordType.from(stack);
                if (cached != null) tnl$swordTypes.put(stack, cached);
            } else {
                tnl$cacheHit.incrementAndGet();
            }
            return cached == null ? SwordType.from(stack) : cached.clone();
        } catch (Throwable ignored) {
            return SwordType.from(stack);
        }
    }

    /**
     * {@code renderOverridedLuminous(...)} ⇒ **拥挤时直接不画**（省掉第二遍几何 ✓）。
     * 阈值来自配置 `slashblade_render_opt.luminous_crowd`（默认 6 ✓ 0 = 永不跳 ✓）。
     */
    @Redirect(method = "renderIcon(Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFZ)V",
              at = @At(value = "INVOKE",
                      target = "Lmods/flammpfeil/slashblade/client/renderer/util/BladeRenderState;renderOverridedLuminous(Lnet/minecraft/world/item/ItemStack;Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
              remap = false)
    private void tnl$maybeSkipLuminous(ItemStack stack, WavefrontObject model, String target,
                                      ResourceLocation texture, PoseStack pose,
                                      MultiBufferSource buffer, int light) {
        int crowd = 6;
        boolean on = true;
        try {
            on = ModConfig.slashbladeRenderOpt();
            crowd = ModConfig.slashbladeLuminousCrowd();
        } catch (Throwable ignored) {
        }
        if (!tnl$provedLuminous) {
            tnl$provedLuminous = true;
            LOGGER.info("[拔刀剑优化] ✓ mixin 生效：发光层调用已接管（阈值 {}，开关 {}）",
                    crowd, on ? "开" : "关");
        }
        tnl$report();
        if (on && crowd > 0 && tnl$tickWindow() > crowd) {
            tnl$lumSkipped.incrementAndGet();
            return;                                          // 拥挤 ⇒ 这一遍发光层省了 ✓
        }
        tnl$lumKept.incrementAndGet();
        BladeRenderState.renderOverridedLuminous(stack, model, target, texture, pose, buffer, light);
    }
}
