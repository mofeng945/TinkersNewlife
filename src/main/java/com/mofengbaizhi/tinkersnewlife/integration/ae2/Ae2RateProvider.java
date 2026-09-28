package com.mofengbaizhi.tinkersnewlife.integration.ae2;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.inventories.InternalInventory;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.AEBaseInvBlockEntity;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.rate.RateSource;
import com.mofengbaizhi.tinkersnewlife.content.rate.RateSourceProvider;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

/**
 * <b>AE2 来源提供方</b>（§735 建 · §736 改为"按磁盘"读 ✓ · §789 三层扫描 ✓）——
 * 一个<b>存储磁盘（cell）= 一个来源</b> ✓
 *
 * <h2>⚠ 为什么不再查"网格库存"（用户 2026-09-28 指出的真问题 ✓）</h2>
 * 原方案是"找到一个 AE2 网格 ⇒ 读整网聚合库存"✗ —— 用户指出：
 * <b>把驱动器接到主网之后，读到的是<b>整个主网</b>的东西</b> ✗，
 * 于是"本维度这台机器产了多少"就完全测不准了 ✗（而且主网的其它部分可能在别的维度 ✓）。
 * <p>⇒ 只读"这一维度里、物理插在驱动器槽位上的每一块磁盘自己的内容" ✓
 * （磁盘不在本维度 ⇒ 不计入 ✓ 正是想要的口径 ✓）。
 *
 * <h2>§789 三层扫描（用户报「我 AE 磁盘里的东西也没读出来」✗）</h2>
 * 原来只有一条路：Forge 的 {@code IItemHandler} ＋ {@code StorageCells.getCellInventory(stack, null)} ✗，
 * 两个坑都会让**一块盘都读不出来** ✗：
 * <ol>
 *   <li><b>传 {@code null} 当 saveProvider 会让 AE2 抛 NPE</b> ✗ ——
 *       {@code BasicCellInventory} 有 {@code private final ISaveProvider container}，
 *       它的懒加载/持久化路径会调 {@code container.saveChanges()}（字节码实核 ✓）
 *       ⇒ 我们传 null ⇒ NPE ⇒ 被 try/catch 吞掉 ⇒ **整块盘消失** ✗。
 *       ⇒ 现在传一个**空实现的存根**（{@link #NO_SAVE} ✓ 只读、不落盘 ✓）。</li>
 *   <li><b>磁盘未必放在"暴露 Forge 物品栏"的方块里</b> ✗ —— 驱动器/ME 箱子、
 *       以及各路 AE 附属（ExtendedAE 的 ExDrive 之类 ✓）各有各的取法 ✓
 *       ⇒ 现在按三条路依次找 ✓（同一个 id ⇒ 自动去重 ✓）：
 *       <ol type="a">
 *         <li>{@link IChestOrDrive}（AE2 自己的驱动器/箱子 API ✓）
 *             ⇒ {@code getCellItem(slot)} ＋ {@code getOriginalCellInventory(slot)} ✓ 最可靠 ✓；</li>
 *         <li>{@link AEBaseInvBlockEntity#getInternalInventory()}（AE2 方块的**内部**物品栏 ✓
 *             —— 驱动器真正插磁盘的地方 ✓，附属只要是继承 AE2 基类的都覆盖 ✓）；</li>
 *         <li>{@code IItemHandler}（Forge 能力 ✓ 兜底：别的模组 / 把磁盘放进普通容器 ✓）。</li>
 *       </ol></li>
 * </ol>
 * 另加一行诊断（§789 ✓）：认出几块盘、各条路各认出几块 ✓；
 * 若"一个方块实体都没认出盘"⇒ 把扫到的方块实体类型打出来（一眼定位磁盘在哪 ✓）。
 *
 * <h2>核过的 API（出处：{@code libs/appliedenergistics2-forge-15.4.10.jar}，逐个 javap ✓）</h2>
 * <pre>
 *   appeng.api.implementations.blockentities.IChestOrDrive
 *       int getCellCount() / Item getCellItem(int) / StorageCell getOriginalCellInventory(int)  ✓
 *   appeng.blockentity.AEBaseInvBlockEntity implements InternalInventoryHost
 *       InternalInventory getInternalInventory()                                    ✓
 *       getCapability(ITEM_HANDLER, side=null) ⇒ getInternalInventory()             ✓（字节码实核 ✓）
 *   appeng.api.storage.StorageCells
 *       static boolean isCellHandled(ItemStack)                                     ✓
 *       static StorageCell getCellInventory(ItemStack, ISaveProvider)               ✓
 *   appeng.api.storage.cells.ISaveProvider   单一方法 void saveChanges()           ✓（所以能写空实现 ✓）
 *   appeng.me.cells.BasicCellInventory       字段 container:ISaveProvider ＋ saveChanges() 调它 ✓
 * </pre>
 *
 * <h2>已知偏差（写进备忘录 ✓）</h2>
 * <ul>
 *   <li><b>流体/气体磁盘</b>：流体能读 ✓（{@code AEFluidKey} ✓）；**气体/化学品**（附属模组的键）不统计 ✗；</li>
 *   <li>身份 = <b>物品 id ＋ 主机坐标 ＋ 槽位</b> ✓（§788：**不能**掺磁盘 NBT 的哈希 ✗ ——
 *       磁盘内容就在它自己的 NBT 里，一改哈希就变 ⇒ 整块盘每轮被当成"新来源"重算一遍流入 ✗✗）；</li>
 *   <li>磁盘被拔走 / 挪到别的槽位 ⇒ 旧槽位身份消失、新槽位身份出现 ✓（相当于一次"搬运"✓）；
 *       **同型号盘换盘** ⇒ 身份不变 ⇒ 会被算成一次大幅增减 ✗（人工操作、一次性 ✓）；</li>
 *   <li>磁盘本身的**物品**（那块磁盘）仍会被第一层容器统计数到 1 个 ✓ 无害 ✓。</li>
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

    /**
     * §789 <b>空实现的"保存器"</b> ✓ —— ⚠ <b>绝不能传 {@code null}</b> ✗：
     * AE2 的 {@code BasicCellInventory} 在懒加载/持久化时会调 {@code container.saveChanges()}
     * （字节码实核 ✓）⇒ 传 null 直接 NPE ⇒ 磁盘内容一块都读不到 ✗（用户实测 ✓）。
     * <p>我们**只读** ✓ 所以这个存根什么都不做 ✓（也不会把任何东西写回物品/存档 ✓）。
     */
    private static final ISaveProvider NO_SAVE = () -> {
    };

    /** §789 诊断节流：维度 → 上次打过的那行摘要 ✓（数字变了才再打 ✓ 不刷屏 ✓） */
    private static final Map<ResourceKey<Level>, String> LAST_SUMMARY = new ConcurrentHashMap<>();

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public List<RateSource> sourcesFor(ServerLevel level, List<LevelChunk> loadedChunks) {
        Map<String, RateSource> byCell = new HashMap<>();
        Map<String, Integer> beTypes = new HashMap<>();          // 诊断：扫到的方块实体类型 ✓
        int inspected = 0;
        int viaDrive = 0;
        int viaInternal = 0;
        int viaCapability = 0;

        for (LevelChunk chunk : loadedChunks) {
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                if (entity == null || entity.isRemoved()) continue;
                inspected++;
                countType(beTypes, entity);

                // ① 驱动器 / ME 箱子：走 AE2 自己的 API ✓（最可靠 ✓ 不碰槽位物品的 NBT ✓）
                if (entity instanceof IChestOrDrive drive) {
                    viaDrive += scanDrive(level, entity, drive, byCell);
                }
                // ② AE2 方块的**内部**物品栏 ✓（驱动器真正插磁盘的地方 ✓；附属继承基类即覆盖 ✓）
                if (entity instanceof AEBaseInvBlockEntity invHost) {
                    try {
                        viaInternal += scanInventory(level, entity, invHost.getInternalInventory(), byCell);
                    } catch (Throwable ignored) {
                    }
                }
                // ③ Forge 物品栏能力（兜底 ✓：别的模组、或把磁盘放进普通容器 ✓）
                IItemHandler handler = handlerOf(entity);
                if (handler != null) {
                    viaCapability += scanHandler(level, entity, handler, byCell);
                }
            }
        }
        logSummary(level, inspected, byCell.size(), viaDrive, viaInternal, viaCapability, beTypes);
        return new ArrayList<>(byCell.values());
    }

    /** ① {@link IChestOrDrive}：按槽位问出这块盘自己的库存 ✓（返回新增了几块 ✓） */
    private static int scanDrive(ServerLevel level, BlockEntity entity, IChestOrDrive drive,
                                 Map<String, RateSource> byCell) {
        int added = 0;
        int slots;
        try {
            slots = drive.getCellCount();
        } catch (Throwable ignored) {
            return 0;
        }
        for (int slot = 0; slot < slots; slot++) {
            try {
                Item item = drive.getCellItem(slot);
                if (item == null || item == Items.AIR) continue;              // 空槽 ✓
                String key = cellKey(item, entity, slot);
                if (byCell.containsKey(key)) continue;
                MEStorage cell = drive.getOriginalCellInventory(slot);        // 只读 ✓ 这块盘自己的内容 ✓
                if (cell == null) continue;
                byCell.put(key, new CellRateSource(cell, level.dimension(), key, entity.getBlockPos()));
                added++;
            } catch (Throwable ignored) {
                // 某一槽读不了 ⇒ 跳过它 ✓ 不影响别的 ✓
            }
        }
        return added;
    }

    /** ② AE2 内部物品栏：逐槽判"是不是磁盘"✓（返回新增了几块 ✓） */
    private static int scanInventory(ServerLevel level, BlockEntity entity, @Nullable InternalInventory inv,
                                     Map<String, RateSource> byCell) {
        if (inv == null) return 0;
        int added = 0;
        int slots;
        try {
            slots = inv.size();
        } catch (Throwable ignored) {
            return 0;
        }
        for (int slot = 0; slot < slots; slot++) {
            try {
                ItemStack stack = inv.getStackInSlot(slot);                   // 只读 ✓
                if (stack == null || stack.isEmpty()) continue;
                if (!StorageCells.isCellHandled(stack)) continue;             // 只认"磁盘" ✓
                String key = cellKey(stack.getItem(), entity, slot);
                if (byCell.containsKey(key)) continue;
                MEStorage cell = StorageCells.getCellInventory(stack, NO_SAVE);   // ⚠ 不能传 null ✓
                if (cell == null) continue;
                byCell.put(key, new CellRateSource(cell, level.dimension(), key, entity.getBlockPos()));
                added++;
            } catch (Throwable ignored) {
            }
        }
        return added;
    }

    /** ③ Forge 物品栏能力：逐槽判"是不是磁盘"✓（返回新增了几块 ✓） */
    private static int scanHandler(ServerLevel level, BlockEntity entity, IItemHandler handler,
                                   Map<String, RateSource> byCell) {
        int added = 0;
        int slots;
        try {
            slots = handler.getSlots();
        } catch (Throwable ignored) {
            return 0;
        }
        for (int slot = 0; slot < slots; slot++) {
            try {
                ItemStack stack = handler.getStackInSlot(slot);               // 只读 ✓
                if (stack == null || stack.isEmpty()) continue;
                if (!StorageCells.isCellHandled(stack)) continue;
                String key = cellKey(stack.getItem(), entity, slot);
                if (byCell.containsKey(key)) continue;                        // 同一块盘只算一次 ✓
                MEStorage cell = StorageCells.getCellInventory(stack, NO_SAVE);   // ⚠ 不能传 null ✓
                if (cell == null) continue;
                byCell.put(key, new CellRateSource(cell, level.dimension(), key, entity.getBlockPos()));
                added++;
            } catch (Throwable ignored) {
            }
        }
        return added;
    }

    /** 诊断用：方块实体类型计数 ✓（最多记 256 种，纯粹防内存涨 ✓） */
    private static void countType(Map<String, Integer> counts, BlockEntity entity) {
        try {
            if (counts.size() >= 256) return;
            var type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(entity.getType());
            counts.merge(type == null ? "unknown" : type.toString(), 1, Integer::sum);
        } catch (Throwable ignored) {
        }
    }

    /**
     * §789 诊断：认出几块盘、各条路各认出几块 ✓（数字变了才打 ✓）。
     * <p>⚠ 一块都没认出来时额外打一行"扫到的方块实体类型"✓ —— 这样"磁盘到底放在哪个方块里"
     * 一眼就能看出来 ✓（用户报过「AE 磁盘里的东西没读出来」✗，这行就是给那种情况用的 ✓）。
     */
    private static void logSummary(ServerLevel level, int inspected, int cells, int viaDrive, int viaInternal,
                                   int viaCapability, Map<String, Integer> beTypes) {
        try {
            String summary = cells + "/" + inspected + "/" + viaDrive + "/" + viaInternal + "/" + viaCapability;
            if (summary.equals(LAST_SUMMARY.get(level.dimension()))) return;
            LAST_SUMMARY.put(level.dimension(), summary);
            TinkersNewlife.LOGGER.info("[产率] AE2 扫描：方块实体 {} 个 ⇒ 磁盘来源 {} 块"
                            + "（驱动器 API {} / 内部栏 {} / 能力 {}）",
                    inspected, cells, viaDrive, viaInternal, viaCapability);
            if (cells == 0 && inspected > 0) {
                List<Map.Entry<String, Integer>> top = new ArrayList<>(beTypes.entrySet());
                top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                List<String> show = new ArrayList<>();
                for (int i = 0; i < top.size() && i < 8; i++) {
                    show.add(top.get(i).getKey() + "×" + top.get(i).getValue());
                }
                TinkersNewlife.LOGGER.info("[产率] ⚠ AE2 在 {} 没认出任何磁盘 ⇒ 扫到的方块实体类型：{}",
                        level.dimension().location(), String.join(" / ", show));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 磁盘身份 ✓：<b>物品 id ＋ 主机坐标 ＋ 槽位</b>（§788 改 ✓）
     *
     * <h2>⚠ 为什么**不能**再掺进 NBT 的哈希（§788 抓到的实锤 bug ✓）</h2>
     * 本节原实现是「物品 id ＋ <b>磁盘自身 NBT 的哈希</b> ＋ 主机坐标/槽位」✗ —— 但
     * <b>AE2 磁盘的内容就存在磁盘物品自己的 NBT 里</b> ✓ ⇒ <b>内容一变、哈希就变、身份就变</b> ✗
     * ⇒ 统计引擎认不出"这还是同一块盘" ⇒ 把它当成<b>旧来源消失 ＋ 新来源出现</b> ✗
     * ⇒ <b>整块盘的存量每一轮都被重新算成"流入"</b> ✗✗（NL 包日志实证：每轮
     * {@code 新增 1 / 跳过 1 ⇒ 变动 物品 831~844 种} ✓ ⇒ 一片下界岩就刷出 +20360 个/时 ✗）。
     * <p>⇒ 现在只用**位置身份**（哪台机器的哪个槽位 ✓）：内容变化 = 同一个来源的正常增减 ✓。
     * <p>⚠ 代价（如实说 ✓）：把一块盘**换成另一块同型号的盘** ⇒ 身份不变 ⇒ 会被算成一次
     * "取出＋放入"（大幅正/负）✗；换成不同型号 ⇒ 物品 id 变 ⇒ 按"消失＋新增"处理 ✓。
     * 这是人工操作、一次性事件 ✓，比"每轮整块重算"好得多 ✓。
     */
    private static String cellKey(Item item, BlockEntity entity, int slot) {
        return "ae2:cell:" + ForgeRegistries.ITEMS.getKey(item)
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

        private final MEStorage cell;
        private final ResourceKey<Level> dimension;
        private final String id;
        /** 主机（驱动器）坐标 ⇒ 用来报"它在哪个区块"✓（§788 ✓） */
        private final BlockPos hostPos;

        CellRateSource(MEStorage cell, ResourceKey<Level> dimension, String id, BlockPos hostPos) {
            this.cell = cell;
            this.dimension = dimension;
            this.id = id;
            this.hostPos = hostPos;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        /**
         * §788：报出主机所在区块 ✓ ⇒ 引擎才能分清"这块盘被拔了/机器被拆了"（结算净减 ✓）
         * 与"只是区块卸载了"（不结算 ✓ 基线留着 ✓）。
         */
        @Override
        public long chunkKey() {
            return new net.minecraft.world.level.ChunkPos(hostPos).toLong();
        }

        @Override
        public void forEachStored(ObjLongConsumer<ItemStack> consumer) {
            // 只读 ✓：这块磁盘**自己的**内容 ✓（不查网络 ✗ 所以不会带上主网/其它维度的东西 ✓）
            forEachAll(consumer, (stack, amount) -> {
            }, value -> {
            });
        }

        @Override
        public void forEachFluid(ObjLongConsumer<FluidStack> consumer) {
            forEachAll((stack, amount) -> {
            }, consumer, value -> {
            });
        }

        /**
         * 一次读全 ✓（§737）：磁盘里可能同时有<b>物品</b>与<b>流体</b>（流体磁盘 ✓）
         * ⇒ 只调一次 {@code getAvailableStacks} 就分派完 ✓ 省掉第二次全量读取 ✓。
         * <p>⚠ 能量**不在磁盘里** ✗ ⇒ 这里不报能量 ✓。
         */
        @Override
        public void forEachAll(ObjLongConsumer<ItemStack> itemSink, ObjLongConsumer<FluidStack> fluidSink,
                               LongConsumer energySink) {
            KeyCounter counter = new KeyCounter();
            cell.getAvailableStacks(counter);
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0L) continue;
                if (key instanceof AEItemKey itemKey) {
                    ItemStack stack = itemKey.getReadOnlyStack();
                    if (!stack.isEmpty()) itemSink.accept(stack, amount);
                } else if (key instanceof AEFluidKey fluidKey) {
                    FluidStack stack = fluidKey.toStack(1);          // 只取"是哪种流体" ✓ 数量用 amount ✓
                    if (!stack.isEmpty()) fluidSink.accept(stack, amount);
                }
                // 其它键（气体/能量等，来自附属模组）本轮不统计 ✗
            }
        }

        @Override
        public String describe() {
            return "AE2 磁盘@" + dimension.location() + " " + id;
        }
    }
}
