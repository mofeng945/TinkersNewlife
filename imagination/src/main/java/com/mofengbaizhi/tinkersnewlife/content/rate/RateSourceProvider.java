package com.mofengbaizhi.tinkersnewlife.content.rate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

/**
 * 「产率来源」的<b>提供方接口</b>（§735）—— 每个联动模组实现一个 ✓
 *
 * <h2>为什么要有这一层</h2>
 * 普通容器走 Forge 的 {@code IItemHandler} 能力就全覆盖了 ✓（原版箱子、通用机械箱柜/机器、
 * 精妙存储、Create 库存……✓）；但 <b>AE2 的物品在"网格"里</b>、
 * <b>Mekanism 的物品在"QIO 频率"里</b> ✗ —— 它们的方块实体<b>不暴露物品栏</b> ✗，
 * 所以必须各自实现一个提供方 ✓，按 {@code IntegrationLoader} 的既有隔离口径
 * 「没装就不加载这个类」✓ 挂进来 ✓。
 *
 * <h2>隔离铁律（与本模组其它联动一致 ✓）</h2>
 * {@code integration/<modid>/} 下的类<b>才</b>允许 import 那个模组的类型 ✓；
 * 公共代码只认本接口 ✓ ⇒ 没装那个模组时 JVM 永远不会加载它的实现类 ✓ 不会 {@code NoClassDefFoundError} ✓。
 */
public interface RateSourceProvider {

    /** 负责的模组 id（空串 = 原版/通用容器 ✓） */
    String modId();

    /**
     * 本次采样为主要这一维度收集来源 ✓（**只读** ✗ 不许改任何容器 ✓）。
     *
     * @param level        目标维度（已加载的那个 {@link ServerLevel} ✓）
     * @param loadedChunks 该维度当前<b>已加载</b>的区块（由 {@code ChunkEvent} 维护 ✓）
     *                     —— 实现方只需在这些区块的方块实体里找自己的东西 ✓
     *                     ⚠ 未加载区块的内容<b>读不到</b> ✗（用户口径：不做离线扫描 ✓）
     */
    List<RateSource> sourcesFor(ServerLevel level, List<LevelChunk> loadedChunks);
}
