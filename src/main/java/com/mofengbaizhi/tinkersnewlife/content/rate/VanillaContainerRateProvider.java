package com.mofengbaizhi.tinkersnewlife.content.rate;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.Collections;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

/**
 * <b>第一层来源</b>（§735 建 · §737 起<b>物品 / 流体 / 能量三种能力都读</b> ✓）：
 * 普通容器一律走 Forge 的三个能力 ✓
 * —— {@code ITEM_HANDLER} / {@code FLUID_HANDLER} / {@code ENERGY} ✓。
 *
 * <h2>这一层为什么够用（省掉一大堆模组适配 ✓）</h2>
 * <ul>
 *   <li><b>物品</b>：原版箱子/木桶/漏斗 ✓、<b>通用机械的箱柜与机器</b> ✓、精妙存储 / 女仆仓管 / Create 库存 ✓；</li>
 *   <li><b>流体</b>：任何暴露 {@code IFluidHandler} 的方块实体 ✓ —— 储罐 / 冶炼炉一类 /
 *       通用机械的流体箱 / Create 的流体罐 ✓（具体覆盖取决于各模组是否暴露 Forge 能力 ✓）；</li>
 *   <li><b>能量</b>：任何暴露 {@code IEnergyStorage}（FE）的方块实体 ✓ ——
 *       电池 / 电容 / 通用机械的机器 ✓。</li>
 * </ul>
 * <p>⚠ 只有"东西不在方块实体里"的<b>网络式存储</b>（AE2 存储磁盘 / Mekanism QIO 频率）才需要
 * 各自的 {@link RateSourceProvider} ✓。
 *
 * <h2>§736 性能优化（保留 ✓）</h2>
 * <ol>
 *   <li><b>按区块缓存</b>：坐标 → (方块实体, 三个已解析的能力, 来源对象) ✓
 *       ⇒ 能力探测（最贵的一步）与对象分配<b>只在首次</b> ✓；</li>
 *   <li>按方块实体身份校验（{@code cached.entity() != be} ⇒ 重建 ✓）；</li>
 *   <li>每轮 {@code retainAll} 清理 ✓；<b>区块卸载整张丢掉</b>（{@link #dropChunk} ✓）
 *       ⇒ 刻意<b>不用</b> {@code WeakHashMap} ✗（来源对象持有方块实体 ⇒ 弱引用缓存会因"值强引用键"永不回收 ✗）；</li>
 *   <li>取能力顺序：<b>无面优先</b>，取不到才按六面试、<b>只认第一个</b> ✓（绝不六面各读一遍 ✗ 会数 6 倍 ✗）。</li>
 * </ol>
 *
 * <h2>§788 多方块去重（用户实测："我只有 12 桶却算出 216 桶"✓）</h2>
 * 多方块结构（森罗酒馆酒桶 / 动态储罐一类）里**每个方块实体都会把同一份库存报一遍** ✗
 * ⇒ 12 桶 × 结构 18 个方块 ＝ 216 桶 ✗。处理办法见 {@link #ownerOf}（把身份统一到"真正持有库存的方块实体"✓）。
 */
public final class VanillaContainerRateProvider implements RateSourceProvider {

    /** 区块 key（{@code ChunkPos#toLong}）→ （坐标 → 缓存条目）✓ */
    private static final Map<Long, Map<BlockPos, Cached>> CHUNK_CACHE = new HashMap<>();

    /** §788：能力对象（wrapper）的类 → 它字段里那些"直接存着方块实体"的字段 ✓（只解析一次 ✓） */
    private static final Map<Class<?>, List<Field>> WRAPPED_BE_FIELDS = new ConcurrentHashMap<>();

    /** §790：已经警告过的"超堆叠数量"（来源 id ＋ 物品 ⇒ 只报一次 ✓ 不刷屏 ✓） */
    private static final Set<String> OVERSIZE_LOGGED = ConcurrentHashMap.newKeySet();

    /**
     * §790 诊断：某来源的某个槽位报出**超过单堆上限**的数量 ✓ —— 只打一次 ✓。
     *
     * <p>用途：用户报「凭空冒出 **872 瓶**樱花血酒」✗ 而存档里**只有 47 瓶** ✓
     * ⇒ 那个数只能是"方块自己算出来的虚拟数量"✗。这行日志会直接点名
     * **是哪个方块实体、哪个槽位、报了多少、该物品上限多少** ✓，一眼定位 ✓。
     */
    private static void logOversized(ItemStack stack, int count, int slot) {
        try {
            String key = System.identityHashCode(stack) + ":" + count;      // 粗粒度去重 ✓
            if (!OVERSIZE_LOGGED.add(key)) return;
            if (OVERSIZE_LOGGED.size() > 512) OVERSIZE_LOGGED.clear();      // 防无限涨 ✓
            TinkersNewlife.LOGGER.info(
                    "[产率] ⚠ 某来源第 {} 槽报了 {} 个 {}（该物品单堆上限只有 {}）"
                            + "⇒ 这是「方块自己算出来的虚拟/预测数量」，不是真实存量 ✓",
                    slot, count, ForgeRegistries.ITEMS.getKey(stack.getItem()), stack.getMaxStackSize());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String modId() {
        return "";
    }

    /** 区块卸载时把它的缓存整张丢掉 ✓ */
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
        // §747 大箱子（连体箱子）**两半共用同一份库存** ⇒ 不分去重就会被数两遍 ✗
        //   ① 结构性去重：只保留"坐标较小"的那一半 ✓（两半各自算出的 partner 都指向对方 ✓ 结果一致 ✓）；
        //   ② 保险丝：同一份"底层容器对象"只登记一次 ✓（按**对象身份**比 ✓ 绝不会误合并两个内容相同的箱子 ✗）
        Set<Object> seenInventories = Collections.newSetFromMap(new IdentityHashMap<>());
        // §822 流体侧的同类保险丝 ✓（实测：CGT 的多方块酒桶里，每个部件的能力都指向**同一个**储罐 ✗
        //   ⇒ 只按"每个方块实体一个来源"数 ⇒ 一罐酒被数好几遍 ✗）⇒ 按底层储罐**对象身份**去重 ✓
        Set<Object> seenFluids = Collections.newSetFromMap(new IdentityHashMap<>());
        for (LevelChunk chunk : loadedChunks) {
            long chunkKey = chunk.getPos().toLong();
            Map<BlockPos, Cached> cache = CHUNK_CACHE.computeIfAbsent(chunkKey, key -> new HashMap<>());
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                BlockEntity entity = entry.getValue();
                if (entity == null || entity.isRemoved()) continue;
                BlockPos pos = entry.getKey();
                if (isSecondHalfOfDoubleChest(entity)) continue;      // ① 连体箱子的"后一半" ⇒ 跳过 ✓
                Cached cached = cache.get(pos);
                // §745：新建缓存时判一次"是不是没人碰过的宝箱"✓；
                //   已经记着的条目**每轮再判一次**（宝箱被打开后 LootTable 会被清掉 ✓ 要能及时"转正"✓）
                if (cached != null && cached.entity() == entity && isUntouchedLootContainer(entity)) {
                    continue;                   // 还是没开过的宝箱 ⇒ 这一轮仍然跳过 ✓
                }
                if (cached == null || cached.entity() != entity) {
                    if (isUntouchedLootContainer(entity)) {
                        cache.remove(pos);
                        continue;               // 没人碰过的宝箱 ⇒ 不登记 ✓ 不统计 ✓
                    }
                    IItemHandler items = itemHandlerOf(entity);
                    IFluidHandler fluids = fluidHandlerOf(entity);
                    IEnergyStorage energy = energyStorageOf(entity);
                    if (items == null && fluids == null && energy == null) {
                        cache.remove(pos);
                        continue;                              // 三个能力都没有 ⇒ 不是来源 ✓
                    }
                    // §788：**多方块去重** ✓ —— 结构里每个方块实体的能力都指向**同一个**底层库存
                    //   （实测：森罗酒馆的「酒桶」结构，18 个方块各自报同一罐酒 ⇒ 12 桶被数成 216 桶 ✗）
                    //   ⇒ 把身份统一到"真正持有库存的那个方块实体"（owner ✓）：
                    //     这些方块实体会算出**同一个 id** ✓ ⇒ 采样表里自然只剩一条 ✓（同内容覆盖 ✓）
                    cached = new Cached(entity, items, fluids, energy,
                            new BlockEntityRateSource(level.dimension(), entity, ownerOf(entity, items, fluids, energy),
                                    structureOwnerPos(entity), items, fluids, energy));
                    cache.put(pos, cached);
                }
                // ② 保险丝：底层库存对象已经统计过 ⇒ 这一份跳过 ✓（模组连体容器也吃这条 ✓）
                Object inventoryId = inventoryIdentity(cached.items());
                if (inventoryId != null && !seenInventories.add(inventoryId)) continue;
                Object fluidId = fluidIdentity(cached.fluids());
                if (fluidId != null && !seenFluids.add(fluidId)) continue;
                out.add(cached.source());
            }
            cache.keySet().retainAll(chunk.getBlockEntities().keySet());
        }
        return out;
    }

    /**
     * §788 <b>多方块去重</b>：问出"这些能力背后真正持有库存的那个方块实体" ✓。
     *
     * <p>哪来的问题：多方块结构（酒桶 / 动态储罐 / 流体罐一类）里，<b>每一个方块实体都会把
     * 同一份库存报一遍</b> ✗ —— 实测（NL 包、用户报"我只有 12 桶却算出 216 桶"✓）：
     * 森罗酒馆酒桶结构里 18 个方块实体各自返回一个
     * {@code DelegatingBarrelFluidHandler}，可它们**都包着同一个控制器的罐子** ✓
     * ⇒ 按"每个方块实体一个来源"去数 ⇒ 12 × 18 ＝ 216 ✗。
     *
     * <p>判据（通用 ✓ 不写死模组）：能力对象<b>不是</b>方块实体自己、而它的字段里
     * <b>直接存着一个方块实体引用</b>（字段声明类型就是 {@code BlockEntity} 的子类 ✓）
     * ⇒ 那个方块实体才是库存的归属者 ✓。反射只做一次并**按类缓存** ✓，
     * 拿不到就当"没有 owner"✓（退回原行为 ✓ 绝不因此少统计 ✓）。
     */
    private static BlockEntity ownerOf(BlockEntity self, @Nullable IItemHandler items,
                                       @Nullable IFluidHandler fluids, @Nullable IEnergyStorage energy) {
        BlockEntity owner = wrappedBlockEntity(items);
        if (owner == null) owner = wrappedBlockEntity(fluids);
        if (owner == null) owner = wrappedBlockEntity(energy);
        return owner == null ? self : owner;
    }

    /** 能力对象的字段里直接存着的那个方块实体 ✓（没有 ⇒ null ✓）；结果按类缓存 ✓ */
    @Nullable
    private static BlockEntity wrappedBlockEntity(@Nullable Object handler) {
        if (handler == null || handler instanceof BlockEntity) return null;
        try {
            List<Field> fields = WRAPPED_BE_FIELDS.computeIfAbsent(handler.getClass(), cls -> {
                List<Field> found = new ArrayList<>();
                try {
                    for (Field field : cls.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers())) continue;
                        if (!BlockEntity.class.isAssignableFrom(field.getType())) continue;
                        try {
                            field.setAccessible(true);
                            found.add(field);
                        } catch (Throwable ignored) {
                            // 拿不到访问权就当没有这个字段 ✓
                        }
                    }
                } catch (Throwable ignored) {
                }
                return found;
            });
            for (Field field : fields) {
                Object value = field.get(handler);
                if (value instanceof BlockEntity be && !be.isRemoved()) return be;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * §747 <b>是不是连体箱子的"后一半"</b>（用户实测：一个大箱子被数了两遍 ✓ 1 把剑显示 2 把 ✗）
     *
     * <p>判据全走原版公开 API ✓：方块是 {@link ChestBlock}（含陷阱箱与其子类 ✓）、
     * 方块状态里有 {@link ChestBlock#TYPE} 且不是 {@code SINGLE}、
     * 用 {@link ChestBlock#getConnectedDirection} 找到另一半 ✓
     * ⇒ **只保留坐标较小的那一半** ✓（两半算出来的"较小者"是同一个 ✓ 所以只会跳掉一个 ✓）。
     */
    private static boolean isSecondHalfOfDoubleChest(BlockEntity entity) {
        try {
            BlockState state = entity.getBlockState();
            if (!(state.getBlock() instanceof ChestBlock)) return false;
            if (!state.hasProperty(ChestBlock.TYPE)) return false;
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type == ChestType.SINGLE) return false;
            BlockPos partner = entity.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
            return entity.getBlockPos().asLong() > partner.asLong();
        } catch (Throwable ignored) {
            return false;                   // 判不出来就当普通容器 ✓ 宁可多读也不要漏掉玩家的箱子 ✓
        }
    }

    /**
     * 取"这份物品栏背后是哪个容器对象" ✓（尽量挖到最底层 ✓）：
     * Forge 的 {@code InvWrapper} 会把原版 {@code Container} 包一层 ✓ ⇒ 挖出来按它的<b>身份</b>比 ✓。
     * <p>⚠ 只在**同一个对象**时才认为重复 ✓（按身份 ✓ 不看内容 ✗）⇒ 两个内容一样的箱子不会被误合并 ✓。
     */
    @Nullable
    private static Object inventoryIdentity(@Nullable IItemHandler handler) {
        if (handler == null) return null;
        try {
            if (handler instanceof net.minecraftforge.items.wrapper.InvWrapper wrapper) {
                return wrapper.getInv();
            }
        } catch (Throwable ignored) {
        }
        return handler;
    }

    // ============================================================
    //  三个能力的解析（都遵守"无面优先 ⇒ 第一个有面"✓）
    // ============================================================

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
        }
        return null;
    }

    @Nullable
    private static IFluidHandler fluidHandlerOf(BlockEntity entity) {
        try {
            IFluidHandler unsided = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
            if (unsided != null && unsided.getTanks() > 0) return unsided;
            for (Direction side : Direction.values()) {
                IFluidHandler sided = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
                if (sided != null && sided.getTanks() > 0) return sided;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    private static IEnergyStorage energyStorageOf(BlockEntity entity) {
        try {
            IEnergyStorage unsided = entity.getCapability(ForgeCapabilities.ENERGY, null).orElse(null);
            if (unsided != null && unsided.getMaxEnergyStored() > 0) return unsided;
            for (Direction side : Direction.values()) {
                IEnergyStorage sided = entity.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
                if (sided != null && sided.getMaxEnergyStored() > 0) return sided;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * §745 <b>跳过"还没开过的战利品箱"</b> ✓（用户实测：原先把野外箱子里的东西也统计进来了 ✗）
     *
     * <p>判据：原版给结构宝箱写的是 {@code LootTable} 这个 NBT 标签 ✓
     * （{@code RandomizableContainerBlockEntity#trySaveLootTable} 只在 {@code lootTable != null} 时写 ✓
     *  参见 1.20.1 源码 ✓）。
     * <ul>
     *   <li><b>玩家自己放的箱子</b>没有这个标签 ✓ ⇒ 照常统计 ✓；</li>
     *   <li>结构宝箱<b>第一次被打开</b>时会 {@code unpackLootTable()} 并把 {@code lootTable} 置空 ✓
     *       ⇒ 之后它就"变成普通箱子"了 ✓（本方法每轮会**重新判定** ✓ 所以它会重新被统计 ✓）；</li>
     *   <li>⚠ 也就是说：**已经被人开过的**宝箱算数 ✓（那时它已经在被使用了 ✓），只有"从没人碰过的"跳过 ✓。</li>
     * </ul>
     */
    private static boolean isUntouchedLootContainer(BlockEntity entity) {
        if (!(entity instanceof RandomizableContainerBlockEntity)) return false;
        try {
            net.minecraft.nbt.CompoundTag tag = entity.saveWithoutMetadata();
            return tag != null && tag.contains(RandomizableContainerBlockEntity.LOOT_TABLE_TAG, 8);
        } catch (Throwable ignored) {
            return false;                       // 判定不了就当普通容器 ✓ 宁可统计也不漏掉玩家的箱子 ✓
        }
    }

    /**
     * §822 <b>结构归属（读模组自己写在 NBT 里的"控制器坐标"）</b> ✓
     *
     * <p>用户报（NL 包）：<b>「酒桶的流体又重复计数了」</b> ✗。实测根因（本机 NL 存档 135 个酒桶方块实体）：
     * {@code creategearsandtavern}（CGT，机械动力×酒馆）把酒桶做成了<b>多方块</b> ——
     * 每个部件都是 {@code kaleidoscope_tavern:barrel} ✓，NBT 里带
     * {@code cgt_controller_pos}（<b>同一个控制器坐标</b> ✓）与 {@code cgt_proxy_part}（控制器 = 0 / 部件 = 1 ✓）。
     * 而 {@link #ownerOf} 只认"能力对象里直接存着的方块实体" ✗ ⇒ 每个部件都算成**独立来源** ✗，
     * 可它们报的却是**控制器那一个罐子** ✗ ⇒ 同一罐酒被数 N 遍 ✗（§788 修过一次，CGT 改成多方块后又回来了 ✗）。
     *
     * <p>判据（<b>只信模组自己声明的东西</b> ✓ 不做内容/坐标猜测 ✗）：
     * 方块实体自己的 NBT 里有<b>名字含 {@code controller/master/core/main/owner} 的 long 型字段</b>
     * ⇒ 那就是它声明的"归属坐标" ✓（{@code BlockPos.asLong()} ✓）。
     * 校验：解出来的坐标必须落在 **±64 格**内 ✓（防脏数据 ✓ 拿不准就当没有 ✓ 退回原行为 ✓）。
     *
     * @return 结构归属坐标 ✓；没有 ⇒ {@code null} ✓（退回"每个方块实体自己" ✓ 绝不因此少统计 ✓）
     */
    @Nullable
    private static BlockPos structureOwnerPos(BlockEntity entity) {
        try {
            net.minecraft.nbt.CompoundTag tag = entity.saveWithoutMetadata();
            if (tag == null) return null;
            BlockPos self = entity.getBlockPos();
            for (String key : tag.getAllKeys()) {
                if (!CONTROLLER_KEY.matcher(key).find()) continue;
                if (tag.getTagType(key) != 4) continue;              // 4 = long ✓（BlockPos.asLong ✓）
                long raw = tag.getLong(key);
                BlockPos pos = BlockPos.of(raw);
                if (pos.equals(self)) return null;                   // 自己就是控制器 ⇒ 不需要额外归属 ✓
                if (Math.abs(pos.getX() - self.getX()) > 64
                        || Math.abs(pos.getY() - self.getY()) > 64
                        || Math.abs(pos.getZ() - self.getZ()) > 64) continue;   // 离谱 ⇒ 不认 ✓
                return pos;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** "控制器坐标"风格的 NBT 键名 ✓（只做**归属合并**用 ✓ 认不出来就退回原行为 ✓） */
    private static final java.util.regex.Pattern CONTROLLER_KEY =
            java.util.regex.Pattern.compile("(?i).*(controller|master|core|main|owner).*");

    /**
     * §822 <b>流体侧保险丝</b>：取"这份流体库存背后真正的储罐对象" ✓（对称于 {@link #inventoryIdentity} ✓）。
     * <p>顺序：① 它自己就是 {@code FluidTank} ⇒ 用它 ✓；
     * ② 它有一个返回 {@code IFluidHandler}/{@code FluidTank} 的 <b>无参 getter</b>（名字含 tank/handler/fluid ✓
     *    例如 CGT 的 {@code BarrelFluidHandler#tank()} ✓）或同类型的字段 ⇒ 用**那个对象** ✓（按类缓存 ✓）；
     * ③ 都没有 ⇒ 用它自己 ✓。
     * <p>⚠ 只在<b>同一个对象</b>时才认为重复 ✓（不看内容 ✗）⇒ 两个装了同样酒的桶绝不会被合并 ✓。
     */
    @Nullable
    private static Object fluidIdentity(@Nullable IFluidHandler handler) {
        if (handler == null) return null;
        try {
            if (handler instanceof net.minecraftforge.fluids.capability.templates.FluidTank) return handler;
        } catch (Throwable ignored) {
        }
        Object inner = wrappedFluidHandler(handler);
        return inner != null ? inner : handler;
    }

    /** 能力对象里"真正的储罐" ✓（无参 getter 或字段 ✓；按类缓存 ✓ 拿不到 ⇒ null ✓） */
    @Nullable
    private static Object wrappedFluidHandler(Object handler) {
        try {
            List<java.lang.reflect.Method> getters = FLUID_GETTERS.computeIfAbsent(handler.getClass(), cls -> {
                List<java.lang.reflect.Method> found = new ArrayList<>();
                try {
                    for (java.lang.reflect.Method m : cls.getMethods()) {
                        if (m.getParameterCount() != 0) continue;
                        if (m.getDeclaringClass() == Object.class) continue;
                        String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                        if (!(n.contains("tank") || n.contains("handler") || n.contains("fluid"))) continue;
                        Class<?> r = m.getReturnType();
                        if (IFluidHandler.class.isAssignableFrom(r)
                                || net.minecraftforge.fluids.capability.templates.FluidTank.class.isAssignableFrom(r)) {
                            found.add(m);
                        }
                    }
                } catch (Throwable ignored) {
                }
                return found;
            });
            for (java.lang.reflect.Method m : getters) {
                try {
                    Object v = m.invoke(handler);
                    if (v instanceof IFluidHandler && v != handler) return v;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** {@link #wrappedFluidHandler} 的按类缓存 ✓ */
    private static final Map<Class<?>, List<java.lang.reflect.Method>> FLUID_GETTERS = new ConcurrentHashMap<>();

    /** 缓存条目 ✓ */
    private record Cached(BlockEntity entity, IItemHandler items, IFluidHandler fluids,
                          IEnergyStorage energy, RateSource source) {
    }

    /** 一个方块实体 = 一个来源 ✓（能报几类就报几类 ✓） */
    private static final class BlockEntityRateSource implements RateSource {

        private final ResourceKey<Level> dimension;
        private final BlockEntity entity;
        /** §788：真正持有库存的那个方块实体 ✓（多方块结构里 = 控制器 ✓ 自己也可能是它 ✓） */
        private final BlockEntity owner;
        /** §822：身份坐标 —— 优先用"模组自己在 NBT 里声明的结构归属坐标" ✓ 否则用 owner 的坐标 ✓ */
        private final BlockPos ownerPos;
        @Nullable
        private final IItemHandler items;
        @Nullable
        private final IFluidHandler fluids;
        @Nullable
        private final IEnergyStorage energy;
        private final String id;

        BlockEntityRateSource(ResourceKey<Level> dimension, BlockEntity entity, BlockEntity owner,
                              @Nullable BlockPos declaredOwnerPos,
                              @Nullable IItemHandler items, @Nullable IFluidHandler fluids,
                              @Nullable IEnergyStorage energy) {
            this.dimension = dimension;
            this.entity = entity;
            this.owner = owner;
            this.ownerPos = declaredOwnerPos != null ? declaredOwnerPos : owner.getBlockPos();
            this.items = items;
            this.fluids = fluids;
            this.energy = energy;
            ResourceLocation type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(owner.getType());
            // ⚠ 身份用 **owner** 的坐标/类型 ✓：多方块结构里所有方块实体会算出同一个 id ✓
            //   ⇒ 采样表里自然只剩一条 ✓（同内容覆盖 ✓）—— 既不会把 12 桶数成 216 桶 ✓，
            //   也不会因为"这一轮先扫到的是哪一个方块"而在两轮之间换身份 ✗（那会刷假流入 ✗）
            this.id = "be:" + dimension.location() + "@" + ownerPos.asLong()
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
            if (items == null || entity.isRemoved()) return;
            int slots = items.getSlots();
            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack;
                try {
                    stack = items.getStackInSlot(slot);               // 只读 ✓
                } catch (Throwable ignored) {
                    continue;
                }
                if (stack == null || stack.isEmpty()) continue;
                int count = stack.getCount();
                // §790 诊断：某个槽位报出**超过该物品单堆上限**的数量 ⇒
                //   这是"方块在算虚拟数量"的典型特征 ✗（真实存量不可能一槽 872 个 ✗）。
                //   用户报过「凭空冒出 872 瓶樱花血酒」✓ —— 这行日志就是为了点名是哪个方块实体在报 ✓。
                if (count > 0 && count > stack.getMaxStackSize()) {
                    logOversized(stack, count, slot);
                }
                consumer.accept(stack, count);
            }
        }

        @Override
        public void forEachFluid(ObjLongConsumer<FluidStack> consumer) {
            if (fluids == null || entity.isRemoved()) return;
            int tanks = fluids.getTanks();
            for (int tank = 0; tank < tanks; tank++) {
                FluidStack stack;
                try {
                    stack = fluids.getFluidInTank(tank);              // 只读 ✓
                } catch (Throwable ignored) {
                    continue;
                }
                if (stack == null || stack.isEmpty()) continue;
                consumer.accept(stack, stack.getAmount());
            }
        }

        @Override
        public long energyStored() {
            if (energy == null || entity.isRemoved()) return 0L;
            try {
                return Math.max(0, energy.getEnergyStored());          // 只读 ✓
            } catch (Throwable ignored) {
                return 0L;
            }
        }

        @Override
        public void forEachAll(ObjLongConsumer<ItemStack> itemSink, ObjLongConsumer<FluidStack> fluidSink,
                               LongConsumer energySink) {
            if (entity.isRemoved()) return;
            forEachStored(itemSink);
            forEachFluid(fluidSink);
            energySink.accept(energyStored());
        }

        /** §749：所在区块 key ✓（来源"消失"时用来分辨"区块卸载"还是"方块被拆" ✓）
         *  <p>§788：用 **owner** 的区块 ✓（多方块结构里所有方块实体都报同一个区块 ✓ 一致 ✓）。 */
        @Override
        public long chunkKey() {
            return new net.minecraft.world.level.ChunkPos(ownerPos).toLong();
        }

        @Override
        public String describe() {
            return "容器 " + dimension.location() + " " + ownerPos.toShortString()
                    + (ownerPos.equals(entity.getBlockPos()) ? ""
                            : "（多方块部件 " + entity.getBlockPos().toShortString() + "）");
        }
    }
}
