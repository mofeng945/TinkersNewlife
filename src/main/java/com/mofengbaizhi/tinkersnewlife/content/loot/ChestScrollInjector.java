package com.mofengbaizhi.tinkersnewlife.content.loot;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.item.AncientCursedScrollItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 只给"箱子类"战利品表（id 路径以 chests/ 开头，任意命名空间）注入古代咒术残卷，
 * 概率约 40%；怪物掉落、方块掉落不受影响。
 * <p>
 * 利用 {@link LootTableLoadEvent} 重建表：反射把原表 pools/functions 放入新 Builder，
 * 再追加一个残卷 pool（自定义运行时函数生成随机词条 NBT）。
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
            LootTable old = event.getTable();
            // 反射读取原表 pools 与 functions
            Field poolsField = LootTable.class.getDeclaredField("f_79109_");
            Field funcsField = LootTable.class.getDeclaredField("f_79110_");
            poolsField.setAccessible(true);
            funcsField.setAccessible(true);
            LootPool[] oldPools = (LootPool[]) poolsField.get(old);
            LootItemFunction[] oldFuncs = (LootItemFunction[]) funcsField.get(old);

            // 新 Builder 并反射放入原 pools/functions
            LootTable.Builder builder = LootTable.lootTable();
            Field bPools = builder.getClass().getDeclaredField("f_79156_");
            Field bFuncs = builder.getClass().getDeclaredField("f_79157_");
            bPools.setAccessible(true);
            bFuncs.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<LootPool> bp = (List<LootPool>) bPools.get(builder);
            @SuppressWarnings("unchecked")
            List<LootItemFunction> bf = (List<LootItemFunction>) bFuncs.get(builder);
            if (oldPools != null) {
                for (LootPool p : oldPools) bp.add(p);
            }
            if (oldFuncs != null) {
                for (LootItemFunction f : oldFuncs) bf.add(f);
            }
            // 残卷 pool：rolls=1，残卷权重 2 / 空权重 3 → 40% 概率出 1 张残卷
            LootPool scrollPool = LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(LootItem.lootTableItem(ModItems.ANCIENT_CURSED_SCROLL.get())
                            .setWeight(2)
                            .apply(() -> new RollScrollFunction()))
                    .add(net.minecraft.world.level.storage.loot.entries.EmptyLootItem.emptyItem()
                            .setWeight(3))
                    .build();
            bp.add(scrollPool);

            // 替换表
            LootTable rebuilt = builder.build();
            event.setTable(rebuilt);
            TinkersNewlife.LOGGER.info("[TinkersNewlife] 已向箱子表 {} 注入古代咒术残卷", id);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[TinkersNewlife] 注入箱子残卷失败 {}: {}", id, t.toString());
        }
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
