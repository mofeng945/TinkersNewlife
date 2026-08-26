package com.mofengbaizhi.tinkersnewlife.content.effect;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缴械效果（Disarm Effect）
 * <p>
 * 使用持久化 NBT 存储被缴械的物品列表，玩家退出再进入也能找回。
 * <p>
 * 特性：
 * <ul>
 *   <li><b>多物品叠加</b>：缴械未结束时再次获得缴械（时长被刷新），若手中拿着新工具，
 *       不删除之前的物品记录，继续追加 —— 直到缴械结束一次性归还所有被缴械的物品。</li>
 *   <li><b>死亡掉落</b>：若携带缴械效果死亡（缴械未解除），直接掉落所有被缴械的物品。</li>
 * </ul>
 */
public class DisarmEffect extends MobEffect {

    // 内存缓存：用于快速访问（效果生效期间的临时存储），持久化存储在 NBT 中
    private static final Map<UUID, List<ItemStack>> DISARMED_ITEMS = new ConcurrentHashMap<>();

    public DisarmEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    // ==================== 持久化 NBT 操作 ====================

    private static final String KEY_DISARM_DATA = "tinkersnewlife:disarm_data";
    private static final String KEY_STORED_ITEMS = "stored_items";
    /** 旧版单物品键（兼容旧存档） */
    private static final String KEY_LEGACY_STORED_ITEM = "stored_item";

    /**
     * 从 NBT 读取被缴械的物品列表
     */
    private static List<ItemStack> getStoredItemsFromNBT(LivingEntity entity) {
        List<ItemStack> items = new ArrayList<>();
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(KEY_DISARM_DATA)) return items;

        CompoundTag data = persistent.getCompound(KEY_DISARM_DATA);
        if (data.contains(KEY_STORED_ITEMS, Tag.TAG_LIST)) {
            ListTag list = data.getList(KEY_STORED_ITEMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.of(list.getCompound(i));
                if (!stack.isEmpty()) items.add(stack);
            }
        } else if (data.contains(KEY_LEGACY_STORED_ITEM)) {
            // ⭐ 旧版单物品存档兼容：读取后由调用方迁移到列表格式
            ItemStack stack = ItemStack.of(data.getCompound(KEY_LEGACY_STORED_ITEM));
            if (!stack.isEmpty()) items.add(stack);
        }
        return items;
    }

    /**
     * 将被缴械的物品列表写入 NBT
     */
    private static void setStoredItemsToNBT(LivingEntity entity, List<ItemStack> items) {
        CompoundTag persistent = entity.getPersistentData();
        CompoundTag data = persistent.getCompound(KEY_DISARM_DATA);
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                list.add(stack.save(new CompoundTag()));
            }
        }
        data.put(KEY_STORED_ITEMS, list);
        data.remove(KEY_LEGACY_STORED_ITEM); // 迁移后移除旧键
        persistent.put(KEY_DISARM_DATA, data);
    }

    /**
     * 从 NBT 移除全部被缴械的物品
     */
    private static void removeStoredItemsFromNBT(LivingEntity entity) {
        entity.getPersistentData().remove(KEY_DISARM_DATA);
    }

    /**
     * 检查 NBT 中是否有被缴械的物品
     */
    private static boolean hasStoredItemsInNBT(LivingEntity entity) {
        return !getStoredItemsFromNBT(entity).isEmpty();
    }

    /**
     * 获取被缴械的物品列表（优先缓存，缓存没有则从 NBT 读取并载入缓存）
     */
    private static List<ItemStack> getStoredItems(LivingEntity entity) {
        UUID uuid = entity.getUUID();
        List<ItemStack> items = DISARMED_ITEMS.get(uuid);
        if (items == null) {
            items = getStoredItemsFromNBT(entity);
            DISARMED_ITEMS.put(uuid, items);
        }
        return items;
    }

    /**
     * 追加一件被缴械的物品（缓存 + NBT 同步）
     */
    private static void addStoredItem(LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) return;
        List<ItemStack> items = getStoredItems(entity);
        items.add(stack.copy());
        setStoredItemsToNBT(entity, items);
    }

    /**
     * 清空所有被缴械的物品（缓存 + NBT）
     */
    private static void clearStoredItems(LivingEntity entity) {
        DISARMED_ITEMS.remove(entity.getUUID());
        removeStoredItemsFromNBT(entity);
    }

    // ==================== 事件处理器 ====================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class DisarmEventHandler {

        private static boolean isDisarmEffect(MobEffectInstance instance) {
            return instance != null && instance.getEffect().equals(ModEffects.DISARM.get());
        }

        /**
         * 尝试缴械当前主手物品（若主手非空则记录并清空）。
         * <p>
         * ⭐ 多物品叠加：无论之前是否已记录过物品，只要主手有工具就追加记录 ——
         * 缴械未结束时再次获得缴械（Changed 事件）会继续收集新工具。
         */
        private static void tryDisarmHand(LivingEntity living) {
            if (living.level().isClientSide) return;
            ItemStack mainHand = living.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!mainHand.isEmpty()) {
                addStoredItem(living, mainHand);
                living.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }

        /**
         * 缴械效果被施加/重新施加时。
         * <p>
         * ⭐ 已确认 Forge 1.20.1 语义：{@code MobEffectEvent.Added} 在 addEffect 的
         * {@code map.get()} 之后无条件触发（构造参数含 prev 效果实例）—— 因此缴械未结束时
         * 再次获得缴械（时长刷新）同样触发 Added。在此继续收集新工具，实现多物品叠加：
         * 不删除之前的记录，主手有新工具则追加。
         * <p>
         * 注意：不要用 {@code MobEffectEvent.Applicable} —— 它属于 canBeAffected 判定事件，
         * 在效果实际未施加时也可能触发，会造成误缴械。
         */
        @SubscribeEvent
        public static void onEffectAdded(MobEffectEvent.Added event) {
            if (!isDisarmEffect(event.getEffectInstance())) return;
            tryDisarmHand(event.getEntity());
        }

        @SubscribeEvent
        public static void onEffectRemoved(MobEffectEvent.Remove event) {
            handleEffectEnd(event.getEntity(), event.getEffectInstance());
        }

        @SubscribeEvent
        public static void onEffectExpired(MobEffectEvent.Expired event) {
            handleEffectEnd(event.getEntity(), event.getEffectInstance());
        }

        /**
         * 玩家死亡时：若缴械效果尚未解除，直接掉落所有被缴械的物品。
         */
        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide) return;
            // 只有缴械效果还在（未解除）时才掉落；已解除（效果消失）由 handleEffectEnd 归还
            if (!entity.hasEffect(ModEffects.DISARM.get())) return;

            List<ItemStack> items = new ArrayList<>(getStoredItems(entity));
            if (items.isEmpty()) return;

            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    entity.spawnAtLocation(stack, 0.5f);
                }
            }
            clearStoredItems(entity);
        }

        /**
         * 玩家登录时：检查是否有被缴械的物品未归还，有则全部归还
         */
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            LivingEntity entity = event.getEntity();
            if (!hasStoredItemsInNBT(entity)) return;

            List<ItemStack> items = getStoredItemsFromNBT(entity);
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    returnItemToPlayer(entity, stack);
                }
            }
            removeStoredItemsFromNBT(entity);
            DISARMED_ITEMS.remove(entity.getUUID());
        }

        /**
         * 玩家退出时：物品已持久化在 NBT，仅清理缓存
         */
        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            DISARMED_ITEMS.remove(event.getEntity().getUUID());
        }

        /** 缴械结束：归还所有被缴械的物品 */
        private static void handleEffectEnd(LivingEntity entity, MobEffectInstance effect) {
            if (!isDisarmEffect(effect)) return;

            List<ItemStack> items = new ArrayList<>(getStoredItems(entity));
            if (items.isEmpty()) return;

            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    returnItemToPlayer(entity, stack);
                }
            }
            clearStoredItems(entity);
        }

        /**
         * 将物品归还给实体（主手空则放主手；玩家优先放入物品栏，放不下再掉落）
         */
        private static void returnItemToPlayer(LivingEntity entity, ItemStack stack) {
            if (stack.isEmpty()) return;
            ItemStack current = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (current.isEmpty()) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, stack);
                return;
            }
            // 玩家：优先放入物品栏，避免直接掉地上
            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                if (player.getInventory().add(stack)) {
                    return;
                }
            }
            entity.spawnAtLocation(stack, 0.5f);
        }
    }
}
