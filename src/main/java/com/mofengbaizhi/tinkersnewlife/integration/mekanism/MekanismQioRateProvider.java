package com.mofengbaizhi.tinkersnewlife.integration.mekanism;

import com.mofengbaizhi.tinkersnewlife.content.rate.RateSource;
import com.mofengbaizhi.tinkersnewlife.content.rate.RateSourceProvider;
import mekanism.api.inventory.qio.IQIOComponent;
import mekanism.api.inventory.qio.IQIOFrequency;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjLongConsumer;

/**
 * <b>通用机械（Mekanism）QIO 来源提供方</b>（§735 建 · §737 复核范围 ✓）—— 一个 <b>QIO 频率 = 一个来源</b> ✓
 *
 * <h2>范围（§737 明确 ✓）</h2>
 * 本类<b>只报物品</b> ✓ —— Mekanism 的 {@code IQIOFrequency} 只提供
 * {@code forAllStored(ObjLongConsumer<ItemStack>)} / {@code getStored(ItemStack)} 这两个<b>物品</b>口子 ✓
 * ⇒ <b>QIO 里的化学品/气体不在本轮统计范围</b> ✗（用户口径只要求"能量或流体"✓ 且 Mekanism 的化学品
 * 不是 Forge 流体 ✗）；Mekanism 的<b>普通流体箱</b>走第一层的 {@code IFluidHandler} ✓ 已覆盖 ✓。
 *
 * <h2>用户说的"mek 的磁盘"就是这一条 ✓</h2>
 * 通用机械的 <b>QIO 磁盘阵列 / QIO 仪表盘</b>把物品存在<b>频率</b>里 ✓，
 * 方块实体本身**不暴露物品栏** ✗ ⇒ 走不了 {@code IItemHandler} ✗（第一层覆盖不到 ✓）。
 * <p>⚠ 普通通用机械箱柜/机器（不是 QIO）**能**走第一层 ✓ ⇒ 本类只管 QIO ✓ 不重复统计 ✓。
 *
 * <h2>核过的 API（出处：{@code libs/Mekanism-1.20.1-10.4.16.80.jar}，javap ✓）</h2>
 * <pre>
 *   mekanism.api.inventory.qio.IQIOComponent     IQIOFrequency getQIOFrequency()                  ✓
 *   mekanism.api.inventory.qio.IQIOFrequency     void forAllStored(ObjLongConsumer&lt;ItemStack&gt;)  ✓
 *                                                long getStored(ItemStack)                       ✓
 *   mekanism.api.IFrequency（父接口）             String getName() / UUID getOwner() / boolean isValid()  ✓
 * </pre>
 *
 * <h2>两条容易翻车的点</h2>
 * <ol>
 *   <li><b>按频率去重</b> ✗✗：同一频率可能有好几个阵列/仪表盘（同一个方块实体也可能被多处引用 ✓）
 *       ⇒ 不去重会重复计数 ✗（按<b>对象身份</b>去重 ✓）；</li>
 *   <li><b>QIO 频率可以跨维度</b> ✗：本类只能从"当前维度里加载到的驱动器"发现频率 ✓
 *       ⇒ 频率的归属维度记"发现它的那个维度" ✓（跨维度时会在每个维度各报一份 ⇒ 已知偏差 ✓
 *       已写进备忘录与规划书 ✓）。</li>
 * </ol>
 *
 * <h2>隔离</h2>
 * 本类是 {@code integration/mekanism/} 下为产率统计 import {@code mekanism.*} 的地方 ✓，
 * 只由 {@code IntegrationLoader} 在 {@code isLoaded("mekanism")} 分支里实例化 ✓
 * ⇒ 没装通用机械时永远加载不到本类 ✓。
 */
public final class MekanismQioRateProvider implements RateSourceProvider {

    /** 与 {@code IntegrationLoader.MEKANISM} 一致 ✓ */
    public static final String MOD_ID = "mekanism";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public List<RateSource> sourcesFor(ServerLevel level, List<LevelChunk> loadedChunks) {
        Map<IQIOFrequency, RateSource> byFrequency = new IdentityHashMap<>();
        for (LevelChunk chunk : loadedChunks) {
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (entity == null || entity.isRemoved()) continue;
                if (!(entity instanceof IQIOComponent component)) continue;
                IQIOFrequency frequency;
                try {
                    frequency = component.getQIOFrequency();
                } catch (Throwable ignored) {
                    continue;                                   // 频率还没绑定好 ✓ 下一轮再来 ✓
                }
                if (frequency == null) continue;
                try {
                    if (!frequency.isValid()) continue;         // 无效频率（比如被删了 ✓）跳过 ✓
                } catch (Throwable ignored) {
                    // 判定不了就当无效 ✓ 宁可少统计也不重复/报错 ✓
                    continue;
                }
                byFrequency.computeIfAbsent(frequency, f -> new QioRateSource(f, level.dimension()));
            }
        }
        return new ArrayList<>(byFrequency.values());
    }

    /** 一个 QIO 频率 = 一个来源 ✓ */
    private static final class QioRateSource implements RateSource {

        private final IQIOFrequency frequency;
        private final ResourceKey<Level> dimension;
        private final String id;

        QioRateSource(IQIOFrequency frequency, ResourceKey<Level> dimension) {
            this.frequency = frequency;
            this.dimension = dimension;
            // 频率自带 owner + name ✓ ⇒ 这个身份**跨重启也稳定** ✓（比 AE2 网格那份强 ✓）
            String owner;
            String name;
            try {
                owner = String.valueOf(frequency.getOwner());
                name = String.valueOf(frequency.getName());
            } catch (Throwable t) {
                owner = "?";
                name = "?";
            }
            this.id = "qio:" + owner + ":" + name;
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
            // 只读 ✓：一次拿全这个频率里存的所有物品 ✓（第二参 = 数量 ✓ 以它为准 ✓）
            frequency.forAllStored((stack, count) -> {
                if (count <= 0L || stack == null || stack.isEmpty()) return;
                consumer.accept(stack, count);
            });
        }

        @Override
        public String describe() {
            return "QIO 频率 " + id() + "@" + dimension.location();
        }
    }
}
