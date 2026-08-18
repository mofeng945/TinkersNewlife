package com.mofengbaizhi.tinkersnewlife.content.effect;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缴械效果（Disarm Effect）
 * 使用持久化 NBT 存储被缴械的物品，玩家退出再进入也能找回
 */
public class DisarmEffect extends MobEffect {

    // 内存缓存：用于快速访问（效果生效期间的临时存储）
    // 但持久化存储在 NBT 中
    private static final Map<UUID, ItemStack> DISARMED_ITEMS = new ConcurrentHashMap<>();

    public DisarmEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    // ==================== 持久化 NBT 操作 ====================

    private static final String KEY_DISARM_DATA = "tinkersnewlife:disarm_data";
    private static final String KEY_STORED_ITEM = "stored_item";

    /**
     * 从 NBT 读取被缴械的物品
     */
    private static ItemStack getStoredItemFromNBT(LivingEntity entity) {
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(KEY_DISARM_DATA)) return ItemStack.EMPTY;
        CompoundTag data = persistent.getCompound(KEY_DISARM_DATA);
        if (!data.contains(KEY_STORED_ITEM)) return ItemStack.EMPTY;
        return ItemStack.of(data.getCompound(KEY_STORED_ITEM));
    }

    /**
     * 将被缴械的物品存入 NBT
     */
    private static void setStoredItemToNBT(LivingEntity entity, ItemStack stack) {
        CompoundTag persistent = entity.getPersistentData();
        CompoundTag data = persistent.getCompound(KEY_DISARM_DATA);
        data.put(KEY_STORED_ITEM, stack.save(new CompoundTag()));
        persistent.put(KEY_DISARM_DATA, data);
    }

    /**
     * 从 NBT 移除被缴械的物品
     */
    private static void removeStoredItemFromNBT(LivingEntity entity) {
        entity.getPersistentData().remove(KEY_DISARM_DATA);
    }

    /**
     * 检查 NBT 中是否有被缴械的物品
     */
    private static boolean hasStoredItemInNBT(LivingEntity entity) {
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(KEY_DISARM_DATA)) return false;
        CompoundTag data = persistent.getCompound(KEY_DISARM_DATA);
        return data.contains(KEY_STORED_ITEM);
    }

    /**
     * 检查缓存中是否有被缴械的物品
     */
    private static boolean hasStoredItemInCache(LivingEntity entity) {
        return DISARMED_ITEMS.containsKey(entity.getUUID());
    }

    /**
     * 获取被缴械的物品（优先从缓存读取，缓存没有则从 NBT 读取）
     */
    private static ItemStack getStoredItem(LivingEntity entity) {
        UUID uuid = entity.getUUID();
        if (DISARMED_ITEMS.containsKey(uuid)) {
            return DISARMED_ITEMS.get(uuid);
        }
        return getStoredItemFromNBT(entity);
    }

    /**
     * 存储被缴械的物品（同时存入缓存和 NBT）
     */
    private static void setStoredItem(LivingEntity entity, ItemStack stack) {
        DISARMED_ITEMS.put(entity.getUUID(), stack);
        setStoredItemToNBT(entity, stack);
    }

    /**
     * 移除被缴械的物品（同时从缓存和 NBT 移除）
     */
    private static void removeStoredItem(LivingEntity entity) {
        DISARMED_ITEMS.remove(entity.getUUID());
        removeStoredItemFromNBT(entity);
    }

    // ==================== 事件处理器 ====================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class DisarmEventHandler {

        @SubscribeEvent
        public static void onEffectAdded(MobEffectEvent.Added event) {
            LivingEntity living = event.getEntity();
            MobEffectInstance instance = event.getEffectInstance();
            if (instance == null) return;
            if (!instance.getEffect().equals(ModEffects.DISARM.get())) return;

            // 如果已经有被缴械的物品，不重复缴械
            if (hasStoredItemInNBT(living) || hasStoredItemInCache(living)) return;

            ItemStack mainHand = living.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!mainHand.isEmpty()) {
                // 存储到缓存和 NBT
                setStoredItem(living, mainHand.copy());
                living.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
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
         * 玩家登录时：检查是否有被缴械的物品未归还，有则归还
         */
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            LivingEntity entity = event.getEntity();
            // 检查 NBT 中是否有被缴械的物品
            if (hasStoredItemInNBT(entity)) {
                ItemStack stored = getStoredItemFromNBT(entity);
                if (!stored.isEmpty()) {
                    // 归还到主手或脚下
                    returnItemToPlayer(entity, stored);
                    removeStoredItemFromNBT(entity);
                    DISARMED_ITEMS.remove(entity.getUUID());
                }
            }
        }

        /**
         * 玩家退出时：将物品存入 NBT（已经做了），不需要额外操作
         * 但可以清理缓存（可选）
         */
        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            // 缓存会在下一次登录时从 NBT 重新加载
            // 不需要额外操作，NBT 已经持久化了
            DISARMED_ITEMS.remove(event.getEntity().getUUID());
        }

        private static void handleEffectEnd(LivingEntity entity, MobEffectInstance effect) {
            if (effect == null) return;
            if (!effect.getEffect().equals(ModEffects.DISARM.get())) return;

            // 从缓存或 NBT 获取物品
            ItemStack stored = getStoredItem(entity);
            if (stored == null || stored.isEmpty()) {
                // 如果 NBT 中没有，可能已经被其他方式归还
                return;
            }

            // 归还物品
            returnItemToPlayer(entity, stored);
            // 清理存储
            removeStoredItem(entity);
        }

        /**
         * 将物品归还给玩家
         */
        private static void returnItemToPlayer(LivingEntity entity, ItemStack stack) {
            if (stack.isEmpty()) return;
            ItemStack current = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (current.isEmpty()) {
                entity.setItemSlot(EquipmentSlot.MAINHAND, stack);
            } else {
                entity.spawnAtLocation(stack, 0.5f);
            }
        }
    }
}