package com.mofengbaizhi.tinkersnewlife.integration.kaleidoscope;

import com.mofengbaizhi.tinkersnewlife.content.rate.RateSource;
import com.mofengbaizhi.tinkersnewlife.content.rate.RateSourceProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

/**
 * <b>森罗酒馆「酒桶」来源</b>（§791 新增 ✓）—— 把酒桶里的<b>流体</b>与<b>原料/成品物品</b>报给产率引擎 ✓。
 *
 * <h2>为什么要单独做（用户实测「没读出酒桶里的流体」✗）</h2>
 * 通用那层（{@code VanillaContainerRateProvider}）只认 Forge 的
 * {@code ITEM_HANDLER} / {@code FLUID_HANDLER} / {@code ENERGY} 能力 ✓，
 * 而森罗酒馆的酒桶**没有注册这些能力** ✗ —— 字节码实核
 * （{@code kaleidoscopetavern-1.2.0-forge+mc1.20.1.jar} ✓）：
 * <pre>
 *   BarrelBlockEntity          implements IBarrel                 ← 只有自家 API ✗
 *     private final FluidTank fluid;                 public FluidTank getFluid();          ✓
 *     private final ItemStackHandler ingredient;     public ItemStackHandler getIngredient(); ✓
 *     private final ItemStackHandler output;         public ItemStackHandler getOutput();  ✓
 *   整个 jar 里**只有** PressingTubBlockEntity 提到 FLUID_HANDLER/getCapability ✓
 *   ⇒ 酒桶的罐子和两个物品栏，通用能力扫描**根本看不见** ✗
 * </pre>
 *
 * <h2>做法：反射（**不加编译期依赖** ✓）</h2>
 * 只按**方块实体注册名**认出酒桶（{@code kaleidoscope_tavern:barrel} ✓），
 * 再反射调那三个公开 getter ✓；返回值本来就是 Forge 类型
 * （{@code FluidTank implements IFluidHandler} ✓、{@code ItemStackHandler implements IItemHandler} ✓）
 * ⇒ 直接当 {@link IFluidHandler} / {@link IItemHandler} 用 ✓，**不需要**引用该模组的任何类 ✓。
 * <p>反射结果**按"类名#方法名"缓存** ✓（只解析一次 ✓）——拿不到就静默跳过 ✓ 绝不报错 ✗。
 *
 * <h2>⚠ 防双算（很重要 ✓）</h2>
 * NL 包里 {@code creategearsandtavern} 会给酒桶挂上自己的 {@code barrel_handler} 能力 ✓
 * ⇒ 那一份**已经**能被通用那层读到 ✓。所以本类的规矩是：
 * <b>某类别只要已经存在对应的 Forge 能力 ⇒ 这一类别就交给通用那层 ✓ 本类不再报</b> ✗
 * （物品与流体**分别判断** ✓，避免同一条数据被两份来源各报一次 ✗）。
 */
public final class KaleidoscopeBarrelRateProvider implements RateSourceProvider {

    /** 与包 id 一致 ✓（存档里方块实体 id 就是这个命名空间 ✓） */
    public static final String MOD_ID = "kaleidoscope_tavern";

    /** 只要这一个方块实体类型 ✓ */
    private static final String BE_ID = MOD_ID + ":barrel";

    /** 反射缓存：`类名#方法名` → Method ✓（拿不到就存 null 哨兵 ✓） */
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    private static final Method MISSING;

    static {
        Method sentinel = null;
        try {
            sentinel = KaleidoscopeBarrelRateProvider.class.getDeclaredMethod("missing");
        } catch (Throwable ignored) {
        }
        MISSING = sentinel;
    }

    /** 只当哨兵用 ✓ 永不调用 ✓ */
    private static void missing() {
    }

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
                if (!BE_ID.equals(beId(entity))) continue;                    // 只认酒桶 ✓
                try {
                    // ① 流体：有 Forge 能力 ⇒ 交给通用那层 ✓ 本类不报 ✓
                    IFluidHandler fluids = hasCap(entity, ForgeCapabilities.FLUID_HANDLER)
                            ? null : asFluidHandler(invoke(entity, "getFluid"));
                    // ② 物品（原料 ＋ 成品）：同上 ✓
                    List<IItemHandler> items = new ArrayList<>(2);
                    if (!hasCap(entity, ForgeCapabilities.ITEM_HANDLER)) {
                        addHandler(items, invoke(entity, "getIngredient"));
                        addHandler(items, invoke(entity, "getOutput"));
                    }
                    if (fluids == null && items.isEmpty()) continue;
                    out.add(new BarrelSource(level.dimension(), entity, fluids, items));
                } catch (Throwable ignored) {
                    // 某个酒桶读不了 ⇒ 跳过它 ✓ 不影响别的 ✓
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

    /** 有没有这个 Forge 能力 ✓（无面 ＋ 六面都试一遍 ✓ 因为有些模组只按面挂 ✓） */
    private static <T> boolean hasCap(BlockEntity entity, Capability<T> capability) {
        try {
            if (entity.getCapability(capability, null).isPresent()) return true;
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                if (entity.getCapability(capability, side).isPresent()) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 按方法名反射调用公开无参方法 ✓（缓存 ✓ 失败 = null ✓） */
    @Nullable
    private static Object invoke(BlockEntity entity, String name) {
        try {
            Method method = METHODS.computeIfAbsent(entity.getClass().getName() + "#" + name, key -> {
                try {
                    Method found = entity.getClass().getMethod(name);
                    found.setAccessible(true);
                    return found;
                } catch (Throwable ignored) {
                    return MISSING;                                    // 拿不到 ⇒ 记哨兵 ✓ 下次不再找 ✓
                }
            });
            if (method == null || method == MISSING) return null;
            return method.invoke(entity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static IFluidHandler asFluidHandler(@Nullable Object value) {
        return value instanceof IFluidHandler handler ? handler : null;
    }

    private static void addHandler(List<IItemHandler> out, @Nullable Object value) {
        if (value instanceof IItemHandler handler) out.add(handler);
    }

    /** 一个酒桶 = 一个来源 ✓（罐子 ＋ 原料栏 ＋ 成品栏，能报什么都报 ✓） */
    private static final class BarrelSource implements RateSource {

        private final ResourceKey<Level> dimension;
        private final BlockEntity barrel;
        @Nullable
        private final IFluidHandler fluids;
        private final List<IItemHandler> items;
        private final String id;

        BarrelSource(ResourceKey<Level> dimension, BlockEntity barrel,
                     @Nullable IFluidHandler fluids, List<IItemHandler> items) {
            this.dimension = dimension;
            this.barrel = barrel;
            this.fluids = fluids;
            this.items = items;
            this.id = "kaleidoscope:barrel@" + dimension.location()
                    + "@" + barrel.getBlockPos().asLong();
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
            if (barrel.isRemoved()) return;
            for (IItemHandler handler : items) {
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
            }
        }

        @Override
        public void forEachFluid(ObjLongConsumer<FluidStack> consumer) {
            if (fluids == null || barrel.isRemoved()) return;
            int tanks;
            try {
                tanks = fluids.getTanks();
            } catch (Throwable ignored) {
                return;
            }
            for (int tank = 0; tank < tanks; tank++) {
                try {
                    FluidStack stack = fluids.getFluidInTank(tank);          // 只读 ✓
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
            // 酒桶里没有能量 ✗
        }

        /** §788：报出它所在区块 ✓ —— 引擎靠它分辨"区块卸载"还是"方块真被拆" ✓ */
        @Override
        public long chunkKey() {
            return new net.minecraft.world.level.ChunkPos(barrel.getBlockPos()).toLong();
        }

        @Override
        public String describe() {
            return "森罗酒馆酒桶@" + dimension.location() + " " + barrel.getBlockPos().toShortString();
        }
    }
}
