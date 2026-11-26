package com.mofengbaizhi.tinkersnewlife.integration.kaleidoscope;

import com.mofengbaizhi.tinkersnewlife.content.rate.RateSource;
import com.mofengbaizhi.tinkersnewlife.content.rate.RateSourceProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

/**
 * <b>「森罗酒馆」系（酒桶 / 酒架 / 酒柜 / 陈列台…）的来源提供方</b>
 * （§791 建「只认酒桶」✗ ⇒ §793 扩成<b>整个酒类家族</b> ✓）
 *
 * <h2>为什么要单独做（用户两次实测 ✓）</h2>
 * <ol>
 *   <li>§791：「没读出<b>酒桶</b>里的流体」✗</li>
 *   <li>§793：「没有读到<b>酒架和酒柜</b>上的酒」✗</li>
 * </ol>
 * 根因同一个：<b>这套模组几乎不给自己的方块注册 Forge 能力</b> ✗ —— 字节码实核
 * （{@code kaleidoscopetavern-1.2.0} ＋ {@code kaleidoscope_world_liquor-1.1.9} ✓）：
 * <pre>
 *   整个 kaleidoscopetavern jar 里，**只有 PressingTubBlockEntity** 提到 ITEM_HANDLER/getCapability ✓
 *   BarrelBlockEntity            implements IBarrel        getFluid() / getIngredient() / getOutput()   ✗ 无能力
 *   StorageBlockEntity（抽象基类）  private ItemStackHandler items;  public getItems()                   ✗ 无能力
 *     ├─ TiltedRackBlockEntity      （陈列架 tilted_rack ✓）
 *     ├─ CircularRackBlockEntity    （圆形酒架 circular_rack ✓）
 *     ├─ CellarCabinetBlockEntity   （酒窖柜 cellar_cabinet ✓）
 *     └─ GlasswareHolderBlockEntity （杯架 ✓）
 *   BarCabinetBlockEntity         private ItemStack leftItem / rightItem;  getLeftItem() / getRightItem() ✗ 无能力
 *   kaleidoscope_world_liquor:BarCellarCabinetBlockEntity **有** ITEM_HANDLER ✓（这一家反倒没问题 ✓）
 * </pre>
 * ⇒ 通用那层（只认 Forge 能力 ✓）**看不见**这些方块 ✗ ⇒ 架子/柜子上的酒永远不进统计 ✗。
 *
 * <h2>做法：按<b>命名空间</b>认人 ＋ 反射扫"能读库存的公开 getter" ✓（无编译期依赖 ✓）</h2>
 * <ul>
 *   <li>只处理这几个命名空间的方块实体 ✓（{@link #NAMESPACES} ✓ 酒类家族 ✓ 别的模组一律不碰 ✗）；</li>
 *   <li>把它的公开<b>无参</b>方法扫一遍（含继承 ✓ 按类缓存 ✓ 只扫一次 ✓），挑出：
 *     <ul>
 *       <li>返回 {@link IItemHandler} / {@link IFluidHandler}（或其子类 ✓ 例如
 *           {@code ItemStackHandler} / {@code FluidTank} ✓）的方法 ✓；</li>
 *       <li>返回 {@link ItemStack} 且名字里带 {@code item} 的方法 ✓
 *           （{@code BarCabinetBlockEntity#getLeftItem/getRightItem} 就是这种 ✗ 没有物品栏、只有两瓶酒 ✓）；</li>
 *     </ul></li>
 *   <li>反射调用它们 ✓ 把结果按类别当成库存读 ✓ —— <b>不引用该模组的任何类型</b> ✓（拿到的本来就是 Forge 接口 ✓）；</li>
 *   <li>拿不到/抛异常 ⇒ 静默跳过 ✗ 绝不影响游戏 ✓。</li>
 * </ul>
 *
 * <h2>⚠ 防双算（很重要 ✓）</h2>
 * 某类别只要<b>已经存在</b>对应的 Forge 能力（例如压榨槽有 ITEM_HANDLER ✓、
 * {@code world_liquor:bar_cellar_cabinet} 有 ITEM_HANDLER ✓）⇒ <b>这一类别交给通用那层</b> ✓
 * 本类不再报 ✗（物品与流体**分别判断** ✓）⇒ 同一条数据绝不会被两份来源各报一次 ✗。
 */
public final class KaleidoscopeRateProvider implements RateSourceProvider {

    /** 与包 id 一致 ✓（挂载门控用 ✓ 只要这一家在就够 ✓） */
    public static final String MOD_ID = "kaleidoscope_tavern";

    /** 只认这几个命名空间 ✓（酒类家族 ✓；`kaleidoscope_compat` 那类纯库不在此列 ✗ 它没有自己的库存方块 ✓） */
    private static final List<String> NAMESPACES = List.of(
            "kaleidoscope_tavern",
            "kaleidoscope_world_liquor",
            "kaleidoscope_bloodwine",
            "kaleidoscope_dim_wine",
            "kaleidoscopecookery",
            "kaleidoscope_nether",
            "kaleidoscope_end");

    /** 「能读库存的 getter」按类缓存 ✓（只解析一次 ✓） */
    private static final Map<Class<?>, List<Method>> GETTERS = new ConcurrentHashMap<>();

    /**
     * §794：{@code ItemStack} / {@code List} 型 getter 的<b>名字白名单词根</b>（小写包含匹配 ✓）。
     *
     * <p>为什么要白名单：这一家有一堆"名字长得像库存、其实是算出来的"getter ✗ ——
     * {@code getDrops()}（掉落预览 ✗）、{@code getEffects()}（效果表 ✗）、
     * {@code getStatus()/getColor()/getSeed()/getCookingProgress()}（纯状态 ✗）……
     * 用词根卡住就<b>不会把"算出来的东西"当成库存</b> ✗✓。实测要覆盖的正是这些 ✓：
     * {@code getItems / getInputs / getInput / getResult / getPotionStack / getRecord /
     * getLeftItem / getRightItem / getItemLeft / getItemRight / getLidItem / getCurrentCutStack} ✓。
     */
    private static final List<String> STACK_TOKENS = List.of("item", "stack", "input", "record", "result");

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public List<RateSource> sourcesFor(ServerLevel level, List<LevelChunk> loadedChunks) {
        List<RateSource> out = new ArrayList<>();
        for (LevelChunk chunk : loadedChunks) {
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                BlockEntity entity = entry.getValue();
                if (entity == null || entity.isRemoved()) continue;
                String beId = beId(entity);
                if (beId == null || !relevant(beId)) continue;              // 只碰酒类家族 ✓
                try {
                    List<Object> values = new ArrayList<>(4);
                    for (Method method : gettersOf(entity.getClass())) {
                        try {
                            Object value = method.invoke(entity);           // 只读 getter ✓
                            if (value != null) values.add(value);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (values.isEmpty()) continue;

                    // ① 流体：已有 Forge 能力 ⇒ 交给通用那层 ✓
                    IFluidHandler fluids = hasCap(entity, ForgeCapabilities.FLUID_HANDLER)
                            ? null : firstFluid(values);
                    // ② 物品（物品栏 / List<ItemStack> / 单个 ItemStack）：同上 ✓
                    List<Object> items = new ArrayList<>(2);
                    if (!hasCap(entity, ForgeCapabilities.ITEM_HANDLER)) {
                        for (Object value : values) {
                            if (value instanceof IItemHandler || value instanceof ItemStack
                                    || value instanceof List) {
                                items.add(value);
                            }
                        }
                        // §794 兜底：一个 getter 都没给出物品 ⇒ 它自己若是原版 Container（锅/蒸笼/汤锅/
                        //   切菜板/烤肉架/饮料块/血酒块… ✓）就直接按 Container 读 ✓
                        //   ⚠ 只在"上面一条都没拿到"时才用 ✗ —— 否则同一份库存会被数两遍 ✗
                        if (items.isEmpty() && entity instanceof Container container) {
                            items.add(container);
                        }
                    }
                    if (fluids == null && items.isEmpty()) continue;
                    out.add(new KaleidoscopeSource(level.dimension(), entity, beId, fluids, items));
                } catch (Throwable ignored) {
                    // 某个方块读不了 ⇒ 跳过它 ✓ 不影响别的 ✓
                }
            }
        }
        return out;
    }

    /** 这个方块实体的注册名 ✓ */
    @Nullable
    private static String beId(BlockEntity entity) {
        try {
            var key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(entity.getType());
            return key == null ? null : key.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 命名空间在名单里吗 ✓ */
    private static boolean relevant(String beId) {
        int colon = beId.indexOf(':');
        String namespace = colon < 0 ? beId : beId.substring(0, colon);
        return NAMESPACES.contains(namespace);
    }

    /**
     * 这个类里"能读出库存"的公开无参 getter ✓（含继承 ✓ 按类缓存 ✓）：
     * 返回 {@code IItemHandler}/{@code IFluidHandler} 的 ✓；
     * 返回 {@code ItemStack} 或 {@code List}（{@code NonNullList<ItemStack>} 也算 ✓）
     * 且**名字里带 {@link #STACK_TOKENS} 之一**的 ✓。
     */
    private static List<Method> gettersOf(Class<?> type) {
        return GETTERS.computeIfAbsent(type, cls -> {
            List<Method> found = new ArrayList<>();
            try {
                for (Method method : cls.getMethods()) {                  // getMethods ⇒ 含继承的公开方法 ✓
                    if (method.getParameterCount() != 0) continue;
                    String name = method.getName();
                    if (!name.startsWith("get")) continue;
                    Class<?> returns = method.getReturnType();
                    boolean handler = IItemHandler.class.isAssignableFrom(returns)
                            || IFluidHandler.class.isAssignableFrom(returns);
                    // ⚠ 名字白名单是为了**不把"算出来的东西"当库存** ✗（getDrops/getEffects/… ✓ 见 STACK_TOKENS ✓）
                    boolean stackLike = (ItemStack.class.isAssignableFrom(returns)
                            || List.class.isAssignableFrom(returns)) && hasStackToken(name);
                    if (!handler && !stackLike) continue;
                    try {
                        method.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    found.add(method);
                }
            } catch (Throwable ignored) {
            }
            return found;
        });
    }

    /** 方法名里带白名单词根吗 ✓ */
    private static boolean hasStackToken(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String token : STACK_TOKENS) {
            if (lower.contains(token)) return true;
        }
        return false;
    }

    @Nullable
    private static IFluidHandler firstFluid(List<Object> values) {
        for (Object value : values) {
            if (value instanceof IFluidHandler handler) return handler;
        }
        return null;
    }

    /** 有没有这个 Forge 能力 ✓（无面 ＋ 六面都试 ✓ 有些模组只按面挂 ✓） */
    private static <T> boolean hasCap(BlockEntity entity, Capability<T> capability) {
        try {
            if (entity.getCapability(capability, null).isPresent()) return true;
            for (Direction side : Direction.values()) {
                if (entity.getCapability(capability, side).isPresent()) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 一个酒类方块 = 一个来源 ✓（能报物品/流体就都报 ✓） */
    private static final class KaleidoscopeSource implements RateSource {

        private final ResourceKey<Level> dimension;
        private final BlockEntity entity;
        private final String beId;
        @Nullable
        private final IFluidHandler fluids;
        /** 物品来源：{@link IItemHandler}（有槽位 ✓）或单个 {@link ItemStack}（酒柜那种 ✓） */
        private final List<Object> items;
        private final String id;

        KaleidoscopeSource(ResourceKey<Level> dimension, BlockEntity entity, String beId,
                           @Nullable IFluidHandler fluids, List<Object> items) {
            this.dimension = dimension;
            this.entity = entity;
            this.beId = beId;
            this.fluids = fluids;
            this.items = items;
            this.id = "kaleidoscope:" + beId + "@" + dimension.location()
                    + "@" + entity.getBlockPos().asLong();
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
            for (Object value : items) {
                if (value instanceof IItemHandler handler) {
                    int slots;
                    try {
                        slots = handler.getSlots();
                    } catch (Throwable ignored) {
                        continue;
                    }
                    for (int slot = 0; slot < slots; slot++) {
                        try {
                            ItemStack stack = handler.getStackInSlot(slot);      // 只读 ✓
                            if (stack == null || stack.isEmpty()) continue;
                            consumer.accept(stack, stack.getCount());
                        } catch (Throwable ignored) {
                        }
                    }
                } else if (value instanceof ItemStack stack) {
                    try {
                        if (stack.isEmpty()) continue;
                        consumer.accept(stack, stack.getCount());
                    } catch (Throwable ignored) {
                    }
                } else if (value instanceof List<?> list) {
                    // §794 `getItems()/getInputs()` 这种返回 `NonNullList<ItemStack>` 的 ✓
                    for (Object element : list) {
                        try {
                            if (element instanceof ItemStack stack && !stack.isEmpty()) {
                                consumer.accept(stack, stack.getCount());
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                } else if (value instanceof Container container) {
                    // §794 原版 Container 兜底（锅/蒸笼/汤锅/切菜板/烤肉架/饮料块/血酒块… ✓）
                    int size;
                    try {
                        size = container.getContainerSize();
                    } catch (Throwable ignored) {
                        continue;
                    }
                    for (int slot = 0; slot < size; slot++) {
                        try {
                            ItemStack stack = container.getItem(slot);          // 只读 ✓
                            if (stack == null || stack.isEmpty()) continue;
                            consumer.accept(stack, stack.getCount());
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }

        @Override
        public void forEachFluid(ObjLongConsumer<FluidStack> consumer) {
            if (fluids == null || entity.isRemoved()) return;
            int tanks;
            try {
                tanks = fluids.getTanks();
            } catch (Throwable ignored) {
                return;
            }
            for (int tank = 0; tank < tanks; tank++) {
                try {
                    FluidStack stack = fluids.getFluidInTank(tank);              // 只读 ✓
                    if (stack == null || stack.isEmpty()) continue;
                    consumer.accept(stack, stack.getAmount());
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        public void forEachAll(ObjLongConsumer<ItemStack> itemSink, ObjLongConsumer<FluidStack> fluidSink,
                               LongConsumer energySink) {
            forEachStored(itemSink);
            forEachFluid(fluidSink);
            // 这套方块里没有能量 ✗
        }

        /** §788：报出它所在区块 ✓ —— 引擎靠它分辨"区块卸载"还是"方块真被拆" ✓ */
        @Override
        public long chunkKey() {
            return new net.minecraft.world.level.ChunkPos(entity.getBlockPos()).toLong();
        }

        @Override
        public String describe() {
            return "酒类方块 " + beId + "@" + dimension.location() + " " + entity.getBlockPos().toShortString();
        }
    }
}
