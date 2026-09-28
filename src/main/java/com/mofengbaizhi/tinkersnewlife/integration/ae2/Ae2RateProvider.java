package com.mofengbaizhi.tinkersnewlife.integration.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.capabilities.Capabilities;
import com.mofengbaizhi.tinkersnewlife.content.rate.RateSource;
import com.mofengbaizhi.tinkersnewlife.content.rate.RateSourceProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjLongConsumer;

/**
 * <b>AE2 来源提供方</b>（§735）—— 一个 <b>网格（grid）= 一个来源</b> ✓
 *
 * <h2>为什么不能走物品栏能力 ✗</h2>
 * AE2 的存储元件、驱动器、<b>ME 存储总线里的东西</b>都不在方块实体的物品栏里 ✗ ——
 * 它们属于<b>网格</b> ✓（和 §559 的"AE 电不在方块实体上"是同一件事 ✓）。
 * 所以要"找网格 → 问网格的存储服务" ✓。
 *
 * <h2>核过的 API（出处：{@code libs/appliedenergistics2-forge-15.4.10.jar}，逐个 javap ✓）</h2>
 * <pre>
 *   appeng.api.networking.IInWorldGridNodeHost   IGridNode getGridNode(Direction)          ✓
 *   appeng.capabilities.Capabilities             Capability&lt;IInWorldGridNodeHost&gt; IN_WORLD_GRID_NODE_HOST  ✓
 *   appeng.api.networking.IGridNode              IGrid getGrid() / boolean isEmpty()        ✓
 *   appeng.api.networking.IGrid                  IStorageService getStorageService()       ✓
 *   appeng.api.networking.storage.IStorageService  MEStorage getInventory()                 ✓
 *   appeng.api.storage.MEStorage                 void getAvailableStacks(KeyCounter)        ✓
 *   appeng.api.stacks.KeyCounter                 Iterable&lt;Object2LongMap.Entry&lt;AEKey&gt;&gt;      ✓
 *   appeng.api.stacks.AEItemKey                  ItemStack getReadOnlyStack()               ✓
 * </pre>
 *
 * <h2>两条容易翻车的点</h2>
 * <ol>
 *   <li><b>按网格去重</b> ✗✗：一个网格有几十个节点（驱动器/终端/总线全是节点 ✓）
 *       ⇒ 不去重会把同一份物品数几十遍 ✗（用 {@link IdentityHashMap} 按<b>对象身份</b>去重 ✓）；</li>
 *   <li><b>网格身份没有稳定名字</b> ✗：{@code IGrid} 没有 id/名字 ✓ ⇒ 用<b>会话内发号</b> ✓
 *       （与"上一次快照只在内存、不落盘"的口径一致 ✓ 重启后基线重立 ✓）。</li>
 * </ol>
 *
 * <h2>隔离</h2>
 * 本类是 {@code integration/ae2/} 下**唯一**为产率统计 import {@code appeng.*} 的地方 ✓，
 * 且只由 {@code IntegrationLoader} 在 {@code isLoaded("ae2")} 分支里实例化 ✓
 * ⇒ 没装 AE2 的玩家永远加载不到本类 ✓ 不会 {@code NoClassDefFoundError} ✓。
 */
public final class Ae2RateProvider implements RateSourceProvider {

    /** 与 {@code IntegrationLoader.AE2} 一致 ✓ */
    public static final String MOD_ID = "ae2";

    /** 网格 → 会话内编号 ✓（只在主线程采样时访问 ✓ 仍加锁稳妥 ✓） */
    private static final Map<IGrid, String> GRID_IDS = new IdentityHashMap<>();

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public List<RateSource> sourcesFor(ServerLevel level, List<LevelChunk> loadedChunks) {
        Set<IGrid> grids = Collections.newSetFromMap(new IdentityHashMap<>());
        for (LevelChunk chunk : loadedChunks) {
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (entity == null || entity.isRemoved()) continue;
                IInWorldGridNodeHost host = hostOf(entity);
                if (host == null) continue;
                for (Direction side : Direction.values()) {
                    IGridNode node;
                    try {
                        node = host.getGridNode(side);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (node == null) continue;
                    try {
                        IGrid grid = node.getGrid();
                        if (grid != null && !grid.isEmpty()) grids.add(grid);
                    } catch (Throwable ignored) {
                        // 网格正在重建时会抛 ✗ ⇒ 这一轮跳过它 ✓ 下个 10 分钟再来 ✓
                    }
                }
            }
        }
        List<RateSource> out = new ArrayList<>(grids.size());
        for (IGrid grid : grids) {
            out.add(new GridRateSource(grid, level.dimension()));
        }
        return out;
    }

    /** 先看它自己是不是宿主接口 ✓，不是再问能力 ✓（两种写法 AE2 生态里都有 ✓） */
    private static IInWorldGridNodeHost hostOf(BlockEntity entity) {
        if (entity instanceof IInWorldGridNodeHost host) return host;
        try {
            return entity.getCapability(Capabilities.IN_WORLD_GRID_NODE_HOST, null).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 一个 AE2 网格 = 一个来源 ✓ */
    private static final class GridRateSource implements RateSource {

        private final IGrid grid;
        private final ResourceKey<Level> dimension;

        GridRateSource(IGrid grid, ResourceKey<Level> dimension) {
            this.grid = grid;
            this.dimension = dimension;
        }

        @Override
        public String id() {
            synchronized (GRID_IDS) {
                return GRID_IDS.computeIfAbsent(grid, g -> "ae2:grid#" + (GRID_IDS.size() + 1));
            }
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public void forEachStored(ObjLongConsumer<ItemStack> consumer) {
            // 只读 ✓：拿"这一网格当前可用的物品总表" ✓（AEItemKey 之外的键 —— 流体/气体 —— 本轮不统计 ✗）
            KeyCounter counter = new KeyCounter();
            grid.getStorageService().getInventory().getAvailableStacks(counter);
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0L || !(key instanceof AEItemKey itemKey)) continue;
                ItemStack stack = itemKey.getReadOnlyStack();
                if (stack.isEmpty()) continue;
                consumer.accept(stack, amount);
            }
        }

        @Override
        public String describe() {
            return "AE2 网格@" + dimension.location() + "（物品 " + id() + "）";
        }
    }
}
