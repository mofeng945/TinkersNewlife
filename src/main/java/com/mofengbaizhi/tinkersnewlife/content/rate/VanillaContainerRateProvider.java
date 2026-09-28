package com.mofengbaizhi.tinkersnewlife.content.rate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjLongConsumer;

/**
 * <b>第一层来源</b>（§735）：普通容器 —— 一律走 Forge 的
 * {@code ForgeCapabilities.ITEM_HANDLER} 能力 ✓。
 *
 * <h2>这一层为什么够用（省掉一大堆模组适配 ✓）</h2>
 * 只要方块实体暴露物品栏能力就自动覆盖 ✓：原版箱子/木桶/漏斗/发射器 ✓、
 * <b>通用机械的箱柜与机器</b>（普通机器就是这条 ✓）、精妙存储 / 女仆仓管 / Create 库存…… ✓
 * <p>⚠ 只有"物品不在方块实体里"的<b>网络式存储</b>（AE2 网格 / Mekanism QIO 频率）才需要
 * 各自的 {@link RateSourceProvider} ✓。
 *
 * <h2>§736 性能优化（用户口径「全优化」✓）</h2>
 * <ol>
 *   <li><b>按区块缓存</b>：每区块一张 {@code 坐标 → (方块实体, 已解析的物品栏, 来源对象)} 表 ✓
 *       ⇒ 能力探测（最贵的一步）与对象分配<b>只在首次</b>发生 ✓；</li>
 *   <li>缓存<b>按方块实体身份校验</b>（{@code cached.entity() != be} ⇒ 重建 ✓）
 *       ⇒ 玩家把箱子拆了换个新的，也能立刻发现 ✓；</li>
 *   <li>每轮用 {@code retainAll} 清理"这一轮已经不在了"的条目 ✓ ⇒ 缓存不会无限膨胀 ✓；</li>
 *   <li><b>区块卸载 ⇒ 整张表丢掉</b>（{@link #dropChunk}，由管理器在 {@code ChunkEvent.Unload} 调 ✓）
 *       ⇒ 缓存随生命周期存在 ✓ <b>不会有"键被值反向吊住"那种弱引用缓存泄漏</b> ✗（所以这里刻意
 *       不用 {@code WeakHashMap} ✗ —— 来源对象持有方块实体，放进 WeakHashMap 会因强引用链而永不回收 ✗）；</li>
 *   <li>取能力顺序：<b>无面优先</b>，取不到才按六面试、<b>只认第一个</b> ✓
 *       —— 绝不把六面各读一遍 ✗（同一份物品栏会被数 6 次 ✗✗）。</li>
 * </ol>
 */
public final class VanillaContainerRateProvider implements RateSourceProvider {

    /** 区块 key（{@code ChunkPos#toLong}）→ （坐标 → 缓存条目）✓ */
    private static final Map<Long, Map<BlockPos, Cached>> CHUNK_CACHE = new HashMap<>();

    /** 原版/通用 ✓（模组 id 用空串表示"不依赖任何联动模组" ✓） */
    @Override
    public String modId() {
        return "";
    }

    /** 区块卸载时把它的缓存整张丢掉 ✓（由管理器调用 ✓） */
    public static void dropChunk(long chunkKey) {
        CHUNK_CACHE.remove(chunkKey);
    }

    /** 换存档/重启时清空 ✓ */
    public static void clearAll() {
        CHUNK_CACHE.clear();
    }

    @Override
    public List<RateSource> sourcesFor(ServerLevel level, List<LevelChunk> loadedChunks) {
        List<RateSource> out = new ArrayList<>();
        for (LevelChunk chunk : loadedChunks) {
            long chunkKey = chunk.getPos().toLong();
            Map<BlockPos, Cached> cache = CHUNK_CACHE.computeIfAbsent(chunkKey, key -> new HashMap<>());
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                BlockEntity entity = entry.getValue();
                if (entity == null || entity.isRemoved()) continue;
                BlockPos pos = entry.getKey();
                Cached cached = cache.get(pos);
                if (cached == null || cached.entity() != entity) {
                    // 首次见到 / 这个位置换了新方块实体 ⇒ 重新解析一次能力 ✓
                    IItemHandler handler = itemHandlerOf(entity);
                    if (handler == null || handler.getSlots() <= 0) {
                        cache.remove(pos);
                        continue;
                    }
                    cached = new Cached(entity, handler,
                            new BlockEntityRateSource(level.dimension(), entity, handler));
                    cache.put(pos, cached);
                }
                out.add(cached.source());
            }
            // 这一轮没见到的坐标（被拆 / 被换）⇒ 从缓存里清掉 ✓ 缓存不会无限膨胀 ✓
            cache.keySet().retainAll(chunk.getBlockEntities().keySet());
        }
        return out;
    }

    /** 取方块实体上的物品栏能力 ✓（无面优先 ⇒ 再取第一个有面 ✓） */
    @Nullable
    private static IItemHandler itemHandlerOf(BlockEntity entity) {
        try {
            IItemHandler unsided = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            if (unsided != null && unsided.getSlots() > 0) return unsided;
            for (Direction side : Direction.values()) {
                IItemHandler sided = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
                if (sided != null && sided.getSlots() > 0) return sided;
            }
        } catch (Throwable ignored) {
            // 个别模组的方块实体在"还没加载完"时取能力会抛 ✗ ⇒ 当成"没有物品栏" ✓ 不打断整轮采样 ✓
        }
        return null;
    }

    /** 缓存条目 ✓（方块实体 + 已解析的物品栏 + 稳定的来源对象 ✓） */
    private record Cached(BlockEntity entity, IItemHandler handler, RateSource source) {
    }

    /** 一个方块实体 = 一个来源 ✓ */
    private static final class BlockEntityRateSource implements RateSource {

        private final ResourceKey<Level> dimension;
        private final BlockEntity entity;
        private final IItemHandler handler;
        private final String id;

        BlockEntityRateSource(ResourceKey<Level> dimension, BlockEntity entity, IItemHandler handler) {
            this.dimension = dimension;
            this.entity = entity;
            this.handler = handler;
            // 身份 = 维度 + 坐标 + 方块实体类型 ✓（天然稳定 ✓ 两次采样能对得上 ✓）
            ResourceLocation type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(entity.getType());
            this.id = "be:" + dimension.location() + "@" + entity.getBlockPos().asLong()
                    + "#" + (type == null ? "unknown" : type.toString());
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public void forEachStored(ObjLongConsumer<ItemStack> consumer) {
            if (entity.isRemoved()) return;
            int slots = handler.getSlots();
            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack;
                try {
                    stack = handler.getStackInSlot(slot);          // 只读 ✓
                } catch (Throwable ignored) {
                    continue;                                       // 单个槽位炸了跳过它 ✓
                }
                if (stack == null || stack.isEmpty()) continue;
                consumer.accept(stack, stack.getCount());
            }
        }

        @Override
        public String describe() {
            return "容器 " + dimension.location() + " " + entity.getBlockPos().toShortString();
        }
    }
}
