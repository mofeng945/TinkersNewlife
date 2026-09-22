package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

/**
 * <b>两个"可选模组能力"的中立分派点</b>（§559）：
 * 通用机械 Mekanism 的 J、应用能源 AE2 的入网节点。
 *
 * <h2>⚠⚠ 本类存在的唯一理由：保证"没装那家模组的玩家不会崩"</h2>
 * 用户口径（我照做的 ✓）：允许加 {@code compileOnly} 依赖，但
 * <b>运行时必须在 {@code ModList.isLoaded(...)} 之后才碰这些类</b> ✗，
 * 而且<b>把所有引用它们的代码放进独立的类里</b> ✓ —— 否则没装的玩家
 * {@code NoClassDefFoundError} 崩游戏 ✗✗。
 *
 * <p>所以整个架构长这样：
 * <pre>
 *   EeConverterCore                   ← 零可选模组引用 ✓（连 import 都没有 ✗）
 *     └─ 每 tick 调 ──→              本类 EnergyConverterModBridges
 *                                      ├─ isLoaded("mekanism") ⇒ 才碰 MekanismEnergyBridge   ✓
 *                                      ├─ isLoaded("ae2")      ⇒ 才碰 Ae2GridBridge           ✓
 *                                      └─ isLoaded("create")   ⇒ 由 ModBlocks/ModBlockEntities 分派 ✓
 * </pre>
 * <b>本类自己只 import 了 Forge / 原版的东西</b> ✓ —— 真正 import {@code mekanism.*} /
 * {@code appeng.*} / {@code com.simibubi.create.*} 的是
 * {@code integration/mekanism/MekanismEnergyBridge}、{@code integration/ae2/Ae2GridBridge}、
 * {@code CreateEnergyConverterBlock(Entity)} ✓，
 * 它们只会在"那家在场"的分支里被触碰 ✓ ⇒ JVM 那时才加载它们 ✓。
 *
 * <p>⚠ 为什么不是"全反射"✗：这两家都要求我们的方块实体<b>注册一个 capability
 * 或一个接口实现</b> ✗ —— 那是编译期类型（{@code Capability<T>} 的泛型、
 * {@code IInWorldGridNodeHost} 的返回值、Create 的<b>继承</b> ✗）✓ 反射做不到 ✗
 * （§557 试过、已在 557.5③④ 写明原因 ✓）。所以这一节是用户<b>明确授权</b>的破例 ✓。
 */
public final class EnergyConverterModBridges {

    private EnergyConverterModBridges() {
    }

    /** 通用机械在场吗（唯一判定入口 ✓ 与仓库既有口径一致 ✓） */
    public static boolean hasMekanism() {
        return IntegrationLoader.isLoaded(IntegrationLoader.MEKANISM);
    }

    /** 应用能源在场吗 */
    public static boolean hasAe2() {
        return IntegrationLoader.isLoaded(IntegrationLoader.AE2);
    }

    /** 机械动力在场吗（由 {@code ModBlocks} / {@code ModBlockEntities} 用它决定注册哪一支 ✓） */
    public static boolean hasCreate() {
        return IntegrationLoader.isLoaded(IntegrationLoader.CREATE);
    }

    /**
     * 问两个桥"这个 capability 归你管吗"。
     *
     * @return 匹配的桥给出的句柄（**可能是"空的 LazyOptional"** ✓ 那也表示"归它管" ✓）；
     *         {@code null} = 两家都不管这个 capability ⇒ 调用方继续走原来的路 ✓
     *         （⚠ 模组不在场时直接返回 {@code null} ✓ 绝不会去加载它们的类 ✗）
     */
    @Nullable
    public static <T> LazyOptional<T> capability(EeConverterCore core, Capability<T> cap, @Nullable Direction side) {
        // ⚠ 顺序：先问"在不在场"，再进 try —— 这样"不在场"这条路上一个可选模组的类都不会被解析 ✓
        if (hasMekanism()) {
            try {
                LazyOptional<T> m = com.mofengbaizhi.tinkersnewlife.integration.mekanism
                        .MekanismEnergyBridge.capability(core, cap, side);
                if (m != null) return m;
            } catch (Throwable t) {
                // 任何意外都只让这一路失效 ✓ 绝不连累 AE2 / FE ✓ 也绝不崩 ✗
                TinkersNewlife.LOGGER.warn("[§559] Mekanism 能力分派失败（已跳过这一路）: {}", t.toString());
            }
        }
        if (hasAe2()) {
            try {
                LazyOptional<T> a = com.mofengbaizhi.tinkersnewlife.integration.ae2
                        .Ae2GridBridge.capability(core, cap, side);
                if (a != null) return a;
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[§559] AE2 能力分派失败（已跳过这一路）: {}", t.toString());
            }
        }
        return null;
    }

    /** 每 tick 的"外部模组"那一段（AE2 抽电 ✓；Mekanism 是"被推"⇒ 不需要 tick ✓） */
    public static void tick(net.minecraft.world.level.block.entity.BlockEntity host) {
        if (host == null) return;
        if (!hasAe2()) return;
        try {
            EeConverterCore core = coreOf(host);
            if (core != null) {
                com.mofengbaizhi.tinkersnewlife.integration.ae2.Ae2GridBridge.tick(core);
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.debug("[§559] AE2 取能失败（已跳过这一 tick）: {}", t.toString());
        }
    }

    /** 方块被拆 / 能力失效 ⇒ 把两家的句柄作废（AE2 的网格节点要 destroy ✓） */
    public static void invalidate(EeConverterCore core) {
        if (core == null) return;
        if (hasMekanism()) {
            try {
                com.mofengbaizhi.tinkersnewlife.integration.mekanism.MekanismEnergyBridge.invalidate(core);
            } catch (Throwable ignored) {
            }
        }
        if (hasAe2()) {
            try {
                com.mofengbaizhi.tinkersnewlife.integration.ae2.Ae2GridBridge.invalidate(core);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 区块加载（AE2 的网格节点必须在这里 create ✓ 与它的 {@code onLoad} 同一时机 ✓） */
    public static void onLoad(EeConverterCore core) {
        if (core == null || !hasAe2()) return;
        try {
            com.mofengbaizhi.tinkersnewlife.integration.ae2.Ae2GridBridge.onLoad(core);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[§559] AE2 网格节点建立失败（已跳过这一路）: {}", t.toString());
        }
    }

    /** 区块卸载 / 方块被拆（AE2 要 destroy ✓） */
    public static void onUnload(EeConverterCore core) {
        if (core == null || !hasAe2()) return;
        try {
            com.mofengbaizhi.tinkersnewlife.integration.ae2.Ae2GridBridge.onUnload(core);
        } catch (Throwable ignored) {
        }
    }

    /** 从宿主方块实体取到核心（两种壳都实现 {@link ConverterCoreHolder} ✓ 这里只认那个接口 ✓） */
    @Nullable
    private static EeConverterCore coreOf(net.minecraft.world.level.block.entity.BlockEntity host) {
        // ⚠ 只认那个**零可选模组引用**的接口 ✓（不去 instanceof Create 那一支 ✗ 否则隔离就白做了 ✗）
        if (host instanceof ConverterCoreHolder holder) return holder.converterCore();
        return null;
    }
}
