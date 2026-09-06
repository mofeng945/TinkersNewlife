package com.mofengbaizhi.tinkersnewlife.content.loot;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.item.AncientCursedScrollItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 只给"箱子类"战利品表（id 路径以 chests/ 开头，任意命名空间）注入古代咒术残卷，
 * 概率约 40%（rolls=1，残卷权重 2 / 空权重 3）；怪物掉落、方块掉落不受影响。
 * <p>
 * 实现不依赖任何字段名/布局：运行时 LootTable 的 pools 是可变 List（Forge 运行时布局，
 * 与 mojmap 源码中的数组不同），直接按"字段运行时类型"找到该 List 后原地追加一个残卷
 * pool 即可；若运行时是数组则退回 Builder 重建路径。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChestScrollInjector {

    private ChestScrollInjector() {}

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation id = event.getName();
        if (id == null || !id.getPath().startsWith("chests/")) {
            return;
        }
        try {
            LootTable table = event.getTable();
            LootPool scrollPool = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(LootItem.lootTableItem(ModItems.ANCIENT_CURSED_SCROLL.get())
                            .setWeight(2)
                            .apply(() -> new RollScrollFunction()))
                    .add(EmptyLootItem.emptyItem().setWeight(3))
                    .build();

            if (!appendToPoolsList(table, scrollPool)) {
                // 运行时 pools 不是 List（例如数组）→ 退回 Builder 重建整表
                LootTable rebuilt = rebuildViaBuilder(table, scrollPool);
                if (rebuilt == null) {
                    TinkersNewlife.LOGGER.warn("[TinkersNewlife] 无法定位箱子表 {} 的 pools 字段，跳过注入", id);
                    return;
                }
                event.setTable(rebuilt);
            } else {
                // 原地追加成功后，原对象即生效；setTable 仅作显式声明
                event.setTable(table);
            }
            TinkersNewlife.LOGGER.info("[TinkersNewlife] 已向箱子表 {} 注入古代咒术残卷", id);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[TinkersNewlife] 注入箱子残卷失败 {}: {}", id, t.toString());
        }
    }

    /** 在 LootTable 实例上找"元素为 LootPool 的 List"字段并原地追加，返回是否成功 */
    @SuppressWarnings("unchecked")
    private static boolean appendToPoolsList(LootTable table, LootPool pool) throws IllegalAccessException {
        for (Field f : LootTable.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !List.class.isAssignableFrom(f.getType())) {
                continue;
            }
            f.setAccessible(true);
            Object v = f.get(table);
            if (!(v instanceof List<?> list)) {
                continue;
            }
            if (!list.isEmpty() && list.get(0) instanceof LootPool) {
                ((List<LootPool>) list).add(pool);
                return true;
            }
            // 空 list：按泛型参数判断
            if (isListOf(list, f, LootPool.class)) {
                ((List<LootPool>) list).add(pool);
                return true;
            }
        }
        return false;
    }

    private static boolean isListOf(List<?> list, Field f, Class<?> elementType) {
        if (!list.isEmpty()) {
            return elementType.isInstance(list.get(0));
        }
        Type gt = f.getGenericType();
        if (gt instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
            return pt.getActualTypeArguments()[0] == elementType;
        }
        return false;
    }

    /** Builder 重建：复制原 pools/functions（兼容 List 与数组两种布局）再追加残卷 pool */
    @SuppressWarnings("unchecked")
    private static LootTable rebuildViaBuilder(LootTable old, LootPool extra) throws Exception {
        LootTable.Builder builder = LootTable.lootTable();

        // 找 Builder 内的 pools List 字段
        List<LootPool> bp = null;
        List<LootItemFunction> bf = null;
        for (Field f : LootTable.Builder.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !List.class.isAssignableFrom(f.getType())) {
                continue;
            }
            f.setAccessible(true);
            Object v = f.get(builder);
            if (!(v instanceof List<?> list)) {
                continue;
            }
            if (isListOf(list, f, LootPool.class) && bp == null) {
                bp = (List<LootPool>) list;
            } else if (isListOf(list, f, LootItemFunction.class) && bf == null) {
                bf = (List<LootItemFunction>) list;
            }
        }
        if (bp == null) {
            return null;
        }

        // 原表 pools：兼容 List 或数组
        List<LootPool> oldPools = new ArrayList<>();
        List<LootItemFunction> oldFuncs = new ArrayList<>();
        for (Field f : LootTable.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            Object v = f.get(old);
            if (v instanceof List<?> list) {
                if (isListOf(list, f, LootPool.class)) {
                    oldPools.addAll((List<? extends LootPool>) list);
                } else if (isListOf(list, f, LootItemFunction.class)) {
                    oldFuncs.addAll((List<? extends LootItemFunction>) list);
                }
            } else if (v instanceof LootPool[] arr) {
                for (LootPool p : arr) oldPools.add(p);
            } else if (v instanceof LootItemFunction[] arr) {
                for (LootItemFunction fn : arr) oldFuncs.add(fn);
            }
        }

        bp.addAll(oldPools);
        bp.add(extra);
        if (bf != null) {
            bf.addAll(oldFuncs);
        }
        return builder.build();
    }

    /** 运行时函数：把普通残卷 item 替换成带随机词条内容的残卷 */
    public static final class RollScrollFunction implements LootItemFunction {
        @Override
        public ItemStack apply(ItemStack stack, net.minecraft.world.level.storage.loot.LootContext context) {
            return AncientCursedScrollItem.roll();
        }

        @Override
        public net.minecraft.world.level.storage.loot.functions.LootItemFunctionType getType() {
            // 该函数仅在此处运行时构建注入，不参与原版序列化 → 返回 null 安全
            return null;
        }
    }
}
