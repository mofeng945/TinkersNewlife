package com.mofengbaizhi.tinkersnewlife.integration.ae2;

import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.mofengbaizhi.tinkersnewlife.content.rate.RateSource;
import com.mofengbaizhi.tinkersnewlife.content.rate.RateSourceProvider;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
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
 * <b>AE2 来源提供方</b>（§735 建 · §736 <b>改为"按磁盘"读取</b> ✓）—— 一个<b>存储磁盘（cell）= 一个来源</b> ✓
 *
 * <h2>⚠ 为什么不再查"网格库存"（用户 2026-09-28 指出的真问题 ✓）</h2>
 * 原方案是"找到一个 AE2 网格 ⇒ 读整网聚合库存"✗ —— 用户指出：
 * <b>把驱动器接到主网之后，读到的是<b>整个主网</b>的东西</b> ✗，
 * 于是"本维度这台机器产了多少"就完全测不准了 ✗（而且主网的其它部分可能在别的维度 / 别的地方 ✓）。
 * <p>⇒ 改成：<b>只读"这一维度里、物理插在驱动器槽位上的每一块磁盘自己的内容"</b> ✓✓
 * <ul>
 *   <li><b>完全不查网络</b> ✗ ⇒ 不会串到主网 ✓、不会串到别的维度 ✓；</li>
 *   <li><b>粒度 = 每块磁盘</b> ✓（用户原话：「根据 ae 磁盘驱动器里的每个磁盘去查这个磁盘对应存储的物品」✓）；</li>
 *   <li>磁盘<b>不在本维度</b>（在别的维度 / 玩家背包 / 便携元件里）⇒ <b>不计入</b> ✓（正是想要的口径 ✓）。</li>
 * </ul>
 *
 * <h2>核过的 API（出处：{@code libs/appliedenergistics2-forge-15.4.10.jar}，逐个 javap ✓）</h2>
 * <pre>
 *   appeng.api.storage.StorageCells
 *       static boolean isCellHandled(ItemStack)                        ← 这个物品是不是"磁盘" ✓
 *       static StorageCell getCellInventory(ItemStack, ISaveProvider)  ← 拿"这块磁盘自己的库存" ✓
 *   appeng.api.storage.cells.StorageCell extends MEStorage             ← 所以能 getAvailableStacks ✓
 *   appeng.blockentity.AEBaseInvBlockEntity                            ← 常量池里有 ITEM_HANDLER ✓
 *       ⇒ 驱动器 / ME 箱子这些"有内部物品栏的 AE2 方块实体"**都暴露 Forge 的 IItemHandler** ✓
 *          ⇒ 我们就能读到槽位里的磁盘物品 ✓（不需要碰 AE2 私有字段 ✓）
 * </pre>
 *
 * <h2>两条容易翻车的点</h2>
 * <ol>
 *   <li><b>只认磁盘</b> ✓：先用 {@code isCellHandled} 过滤 ✓ ⇒ 驱动器槽位里的其它东西（升级卡之类）不会被误当成库存 ✓；</li>
 *   <li><b>按磁盘身份去重</b> ✓：同一块磁盘在一次采样里只读一遍 ✓
 *       （身份 = 磁盘物品 id + 它自己 NBT 的哈希 + 主机坐标/槽位 ✓
 *        —— 哈希万一撞了也只是把两块盘合并成一条 ✓ 对"总量差"这个口径**数值仍然正确** ✓）。</li>
 * </ol>
 *
 * <h2>已知偏差（写进备忘录 ✓）</h2>
 * <ul>
 *   <li><b>流体/气体磁盘</b>不算 ✗（本轮只做物品 ✓）；</li>
 *   <li>磁盘被拔走 / 挪到别的槽位 ⇒ 身份变了 ⇒ 按 §735 基线规则**只算"没观测到"** ✓
 *       ⇒ 那次搬动**不会**被算成净减 ✓（也不会算成产出 ✓）；</li>
 *   <li>磁盘本身的**物品**（那块磁盘）仍会被第一层（{@code IItemHandler}）数到 1 个 ✓ 无害 ✓。</li>
 * </ul>
 *
 * <h2>隔离</h2>
 * 本类是 {@code integration/ae2/} 下为产率统计 import {@code appeng.*} 的地方 ✓，
 * 只由 {@code IntegrationLoader} 在 {@code isLoaded("ae2")} 分支里实例化 ✓
 * ⇒ 没装 AE2 的玩家永远加载不到本类 ✓ 不会 {@code NoClassDefFoundError} ✓。
 */
public final class Ae2RateProvider implements RateSourceProvider {

    /** 与 {@code IntegrationLoader.AE2} 一致 ✓ */
    public static final String MOD_ID = "ae2";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public List<RateSource> sourcesFor(ServerLevel level, List<LevelChunk> loadedChunks) {
        Map<String, RateSource> byCell = new HashMap<>();
        for (LevelChunk chunk : loadedChunks) {
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (entity == null || entity.isRemoved()) continue;
                IItemHandler handler = handlerOf(entity);
                if (handler == null) continue;
                int slots = handler.getSlots();
                for (int slot = 0; slot < slots; slot++) {
                    ItemStack stack;
                    try {
                        stack = handler.getStackInSlot(slot);          // 只读 ✓
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (stack == null || stack.isEmpty()) continue;
                    if (!StorageCells.isCellHandled(stack)) continue;  // ① 只认"磁盘" ✓
                    String key = cellKey(stack, entity, slot);
                    if (byCell.containsKey(key)) continue;             // ② 同一块盘只算一次 ✓
                    try {
                        StorageCell cell = StorageCells.getCellInventory(stack, null);   // 只读 ✓（不传 saveProvider ⇒ 不落盘 ✓）
                        if (cell == null) continue;
                        byCell.put(key, new CellRateSource(cell, level.dimension(), key));
                    } catch (Throwable ignored) {
                        // 某块磁盘读不了（损坏 / 版本不兼容 ✓）⇒ 跳过它 ✓ 不影响别的 ✓
                    }
                }
            }
        }
        return new ArrayList<>(byCell.values());
    }

    /** 磁盘身份 ✓：物品 id + 磁盘自身 NBT 的哈希（里面含它的存储 ID ✓）+ 主机坐标/槽位（便于排查 ✓） */
    private static String cellKey(ItemStack stack, BlockEntity entity, int slot) {
        CompoundTag tag = stack.getTag();
        return "ae2:cell:" + ForgeRegistries.ITEMS.getKey(stack.getItem())
                + ":" + (tag == null ? "-" : Integer.toHexString(tag.hashCode()))
                + "@" + entity.getBlockPos().asLong() + "#" + slot;
    }

    /** 取方块实体的物品栏能力 ✓（无面优先 ⇒ 再取第一个有面 ✓；照抄第一层那套，防六面重复读 ✗） */
    @Nullable
    private static IItemHandler handlerOf(BlockEntity entity) {
        try {
            IItemHandler unsided = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            if (unsided != null && unsided.getSlots() > 0) return unsided;
            for (Direction side : Direction.values()) {
                IItemHandler sided = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
                if (sided != null && sided.getSlots() > 0) return sided;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 一块磁盘 = 一个来源 ✓ */
    private static final class CellRateSource implements RateSource {

        private final StorageCell cell;
        private final ResourceKey<Level> dimension;
        private final String id;

        CellRateSource(StorageCell cell, ResourceKey<Level> dimension, String id) {
            this.cell = cell;
            this.dimension = dimension;
            this.id = id;
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
            // 只读 ✓：这块磁盘**自己的**内容 ✓（不查网络 ✗ 所以不会带上主网/其它维度的东西 ✓）
            KeyCounter counter = new KeyCounter();
            cell.getAvailableStacks(counter);
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0L || !(key instanceof AEItemKey itemKey)) continue;   // 流体/气体磁盘跳过 ✓
                ItemStack stack = itemKey.getReadOnlyStack();
                if (stack.isEmpty()) continue;
                consumer.accept(stack, amount);
            }
        }

        @Override
        public String describe() {
            return "AE2 磁盘@" + dimension.location() + " " + id;
        }
    }
}
