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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缴械效果（Disarm）—— §813 改为**“立刻丢出 ✓ 期间不能捡”**模型。
 *
 * <h2>新口径（★用户口径 ✓）</h2>
 * <pre>
 *   非玩家：主手 + 副手的物品 ⇒ ★**自动丢到地上**；持续时间内 ★**无法再次捡起物品**
 *   玩家  ：主手 + 副手的物品 ⇒ ★**丢进背包**（满了才丢地上✗）；
 *             持续时间内★**无论主副手拿到什么都丢进背包**；★**无法捡起任何物品**
 *   缴械丢出的地物 ⇒ ★**不会被 雷劈 / 爆炸 / 火焰 / 岩浆 / 仙人掌 销毁**（★见 DisarmDropProtectionMixin ✓）
 * </pre>
 *
 * <p>⚠ 旧模型（★收缴进 NBT ⇒ 到期归还 ✗）已废弃；为不丢旧存档，
 * 登录时仍会把**旧 NBT 里的物品如数归还**✓。
 */
public class DisarmEffect extends MobEffect {

    /** 旧版存储用的键（★仅用于登录时归还旧档 ✓） */
    private static final String KEY_DISARM_DATA = "tinkersnewlife:disarm_data";
    private static final String KEY_STORED_ITEMS = "stored_items";
    private static final String KEY_LEGACY_STORED_ITEM = "stored_item";

    public DisarmEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    /** 旧档读取（★仅登录归还用 ✓） */
    private static List<ItemStack> legacyStoredItems(LivingEntity entity) {
        List<ItemStack> items = new ArrayList<>();
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(KEY_DISARM_DATA)) {
            return items;
        }
        CompoundTag data = persistent.getCompound(KEY_DISARM_DATA);
        if (data.contains(KEY_STORED_ITEMS, Tag.TAG_LIST)) {
            ListTag list = data.getList(KEY_STORED_ITEMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.of(list.getCompound(i));
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
        } else if (data.contains(KEY_LEGACY_STORED_ITEM)) {
            ItemStack stack = ItemStack.of(data.getCompound(KEY_LEGACY_STORED_ITEM));
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        return items;
    }

    // ==================== 事件处理 ====================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class DisarmEventHandler {

        /** ★已被缴械丢出的地物标记（★供 DisarmDropProtectionMixin 认领 ✓） */
        public static final String DROP_TAG = "tnl_disarm_drop";

        private static boolean isDisarmEffect(MobEffectInstance instance) {
            return instance != null && instance.getEffect().equals(ModEffects.DISARM.get());
        }

        public static boolean isDisarm(Player player) {
            return player != null && player.hasEffect(ModEffects.DISARM.get());
        }

        /**
         * ★缴械一把手：玩家⇒**先塞背包**（满了才掉地上✗）；
         * 非玩家⇒**直接丢到地上**✓ 并打上“不可销毁”标记 ✓
         */
        private static void disarmSlot(LivingEntity living, EquipmentSlot slot) {
            if (living.level().isClientSide) {
                return;
            }
            ItemStack stack = living.getItemBySlot(slot);
            if (stack.isEmpty()) {
                return;
            }
            living.setItemSlot(slot, ItemStack.EMPTY);
            if (living instanceof Player player) {
                // ★玩家：丢进背包 ✓（★背包满了才掉地上 ✗）
                if (player.getInventory().add(stack)) {
                    return;
                }
            }
            dropProtected(living, stack);
        }

        /** ★丢到地上，并打上“不会被销毁”标记 ✓ */
        private static void dropProtected(LivingEntity living, ItemStack stack) {
            ItemEntity drop = new ItemEntity(living.level(), living.getX(),
                    living.getY() + living.getEyeHeight() * 0.6D, living.getZ(), stack);
            drop.getPersistentData().putBoolean(DROP_TAG, true);
            drop.setPickUpDelay(40);   // ★先给几秒不能捡（★避免刚丢出去就被自己踩回去 ✗）
            living.level().addFreshEntity(drop);
        }

        /**
         * ★每 tick 保持双手空着 ✓ —— “无论主副手拿到什么都丢进背包”靠这里 ✓
         * （★旧模型只在 Added 时收一次 ✗ 所以后来拿到的东西不会被没收 ✗）
         */
        @SubscribeEvent
        public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
            LivingEntity living = event.getEntity();
            if (living.level().isClientSide || !living.hasEffect(ModEffects.DISARM.get())) {
                return;
            }
            disarmSlot(living, EquipmentSlot.MAINHAND);
            disarmSlot(living, EquipmentSlot.OFFHAND);
        }

        /** 旧 NBT 存储的物品：登录时如数归还 ✓（★避免旧档丢物 ✗） */
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            LivingEntity entity = event.getEntity();
            List<ItemStack> old = legacyStoredItems(entity);
            if (old.isEmpty()) {
                return;
            }
            for (ItemStack stack : old) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (entity instanceof Player player && player.getInventory().add(stack)) {
                    continue;
                }
                entity.spawnAtLocation(stack, 0.5F);
            }
            entity.getPersistentData().remove(KEY_DISARM_DATA);
        }

        /** ★缴械期间：玩家**无法捡起任何物品** ✓（★非玩家同理 ✓） */
        @SubscribeEvent
        public static void onItemPickup(EntityItemPickupEvent event) {
            LivingEntity picker = event.getEntity();
            if (picker.level().isClientSide || !picker.hasEffect(ModEffects.DISARM.get())) {
                return;
            }
            event.setCanceled(true);   // ★鼠标/踏上去都捡不起来 ✓
        }

        /** ★死亡时丢出双手物品（★标记保护 ✓）✗ 旧模型的“归还”已不存在 ✓ */
        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide || !entity.hasEffect(ModEffects.DISARM.get())) {
                return;
            }
            disarmSlot(entity, EquipmentSlot.MAINHAND);
            disarmSlot(entity, EquipmentSlot.OFFHAND);
        }

        /** ★转化（闪电把骷髅→响骷等）：缴械效果延续到新实体 ✓ */
        @SubscribeEvent
        public static void onLivingConversion(net.minecraftforge.event.entity.living.LivingConversionEvent.Post event) {
            if (event.getEntity().level().isClientSide) {
                return;
            }
            LivingEntity oldEntity = event.getEntity();
            LivingEntity newEntity = event.getOutcome();
            if (newEntity == oldEntity) {
                return;
            }
            MobEffectInstance inst = oldEntity.getEffect(ModEffects.DISARM.get());
            if (inst != null) {
                newEntity.addEffect(new MobEffectInstance(ModEffects.DISARM.get(),
                        Math.max(60, inst.getDuration()), 0, false, true));
                oldEntity.removeEffect(ModEffects.DISARM.get());
            }
        }
    }
}
