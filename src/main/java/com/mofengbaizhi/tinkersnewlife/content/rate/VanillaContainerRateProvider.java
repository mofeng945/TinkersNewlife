package com.mofengbaizhi.tinkersnewlife.content.rate;

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
import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * <b>第一层来源</b>（§735）：普通容器 —— 一律走 Forge 的
 * {@code ForgeCapabilities.ITEM_HANDLER} 能力 ✓。
 *
 * <h2>这一层为什么够用（省掉一大堆模组适配 ✓）</h2>
 * 只要方块实体暴露物品栏能力，就自动被覆盖 ✓：原版箱子/木桶/漏斗/发射器 ✓、
 * <b>通用机械的箱柜与机器</b>（用户要的"mek"里，普通机器就是这一条 ✓）、
 * 精妙存储 / 女仆仓管 / Create 库存…… ✓
 * <p>⚠ 只有"物品不在方块实体里"的<b>网络式存储</b>（AE2 网格 / Mekanism QIO 频率）才需要
 * 各自的 {@link RateSourceProvider} ✓（见 {@code integration/ae2} 与 {@code integration/mekanism} ✓）。
 *
 * <h2>取能力的顺序（防重复计数 ✗）</h2>
 * 先取<b>无面</b>（{@code null}）那份 ✓；取不到再按六个方向逐个试、<b>只认第一个</b> ✓
 * —— 绝不能把六面各读一遍 ✗（同一份物品栏会被数 6 次 ✗✗）。
 */
public final class VanillaContainerRateProvider implements RateSourceProvider {

    /** 原版/通用 ✓（模组 id 用空串表示"不依赖任何联动模组" ✓） */
    @Override
    public String modId() {
        return "";
    }

    @Override
    public List<RateSource> sourcesFor(ServerLevel level, List<LevelChunk> loadedChunks) {
        List<RateSource> out = new ArrayList<>();
        for (LevelChunk chunk : loadedChunks) {
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (entity == null || entity.isRemoved()) continue;
                IItemHandler handler = itemHandlerOf(entity);
                if (handler == null || handler.getSlots() <= 0) continue;
                out.add(new BlockEntityRateSource(level.dimension(), entity, handler));
            }
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
