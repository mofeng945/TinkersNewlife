package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.storage.BagMenuProvider;
import com.mofengbaizhi.tinkersnewlife.content.storage.StorageManager;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class QuantumBagModifier extends Modifier {

    private static final ResourceLocation KEY_BAG_UUID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "bag_uuid_persistent");

    /** 打开冷却（tick）：按住按键时客户端会持续发包，服务端需节流避免重复 openScreen 导致界面闪烁 */
    private static final int OPEN_COOLDOWN_TICKS = 10;
    private static final java.util.Map<UUID, Long> OPEN_COOLDOWNS = new ConcurrentHashMap<>();

    // ============================================================
    //  📦 UUID 管理
    // ============================================================

    public static UUID getOrCreateBagUUID(ItemStack tool) {
        if (tool.isEmpty()) return null;
        // ✅ 使用 ToolHelper 安全获取
        ToolStack toolStack = ToolHelper.getToolStack(tool);
        if (toolStack == null) return null;

        IModDataView persistentData = toolStack.getPersistentData();

        if (persistentData.contains(KEY_BAG_UUID)) {
            return NbtUtils.loadUUID(persistentData.get(KEY_BAG_UUID));
        }

        UUID newUUID = UUID.randomUUID();
        if (persistentData instanceof ToolDataNBT toolData) {
            toolData.put(KEY_BAG_UUID, NbtUtils.createUUID(newUUID));
        }
        return newUUID;
    }

    public static UUID getBagUUID(ItemStack tool) {
        if (tool.isEmpty()) return null;
        // ✅ 使用 ToolHelper 安全获取
        ToolStack toolStack = ToolHelper.getToolStack(tool);
        if (toolStack == null) return null;

        IModDataView persistentData = toolStack.getPersistentData();
        if (persistentData.contains(KEY_BAG_UUID)) {
            return NbtUtils.loadUUID(persistentData.get(KEY_BAG_UUID));
        }
        return null;
    }

    public static int getBagLevel(ItemStack tool) {
        if (tool.isEmpty()) return 0;
        // ✅ 使用 ToolHelper 安全获取
        ToolStack toolStack = ToolHelper.getToolStack(tool);
        if (toolStack == null) return 0;
        return toolStack.getModifierLevel(Modifiers.QUANTUM_BAG.getId());
    }

    // ============================================================
    //  📦 打开背包（仅通过 B 键调用）
    // ============================================================

    public static boolean tryOpenBag(Player player, ItemStack tool) {
        if (player == null || tool.isEmpty()) return false;
        if (player.level().isClientSide) return false;

        // ⭐ 打开冷却：防止按住按键时客户端每 tick 发包导致 openScreen 重复触发（界面闪烁）
        long now = player.level().getGameTime();
        Long lastOpen = OPEN_COOLDOWNS.get(player.getUUID());
        if (lastOpen != null && now - lastOpen < OPEN_COOLDOWN_TICKS) {
            return false;
        }
        OPEN_COOLDOWNS.put(player.getUUID(), now);

        int level = getBagLevel(tool);
        if (level <= 0) return false;

        UUID uuid = getOrCreateBagUUID(tool);
        if (uuid == null) return false;

        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        ItemStackHandler handler = StorageManager.getInstance().getOrCreate(uuid, level);
        CompoundTag dataTag = handler.serializeNBT();
        byte[] serializedData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            NbtIo.write(dataTag, dos);
            serializedData = baos.toByteArray();
        } catch (IOException e) {
            serializedData = new byte[0];
        }

        final byte[] finalData = serializedData;
        final UUID finalUUID = uuid;
        final int finalLevel = level;

        NetworkHooks.openScreen(serverPlayer, new BagMenuProvider(finalUUID, finalLevel), (FriendlyByteBuf buf) -> {
            buf.writeUUID(finalUUID);
            buf.writeInt(finalLevel);
            buf.writeByteArray(finalData);
        });
        return true;
    }

    // ============================================================
    //  🧹 自动拾取：优先进入背包，放入后自动整理
    // ============================================================

    /**
     * 静态事件订阅者：Modifier 是注册表单例，若在构造器里注册 Forge 事件总线，
     * 实例被重建（数据包/注册表重载）时会重复注册导致拾取重复入库。
     * 改为静态订阅类，全局只注册一次。
     */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class PickupHandler {

        /** 每个背包 UUID 的待整理计数器，用于节流排序（避免每次拾取都全量排序） */
        private static final java.util.Map<UUID, AtomicInteger> SORT_COUNTERS = new ConcurrentHashMap<>();
        /** 每累计多少次拾取触发一次排序 */
        private static final int SORT_INTERVAL = 5;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onItemPickup(EntityItemPickupEvent event) {
            Player player = event.getEntity();
            if (player.level().isClientSide) return;

            ItemEntity itemEntity = event.getItem();
            if (itemEntity == null) return;

            ItemStack pickedUpStack = itemEntity.getItem();
            if (pickedUpStack.isEmpty()) return;

            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();

            UUID bagUUID = null;
            int bagLevel = 0;

            if (getBagLevel(mainHand) > 0) {
                bagUUID = getOrCreateBagUUID(mainHand);
                bagLevel = getBagLevel(mainHand);
            } else if (getBagLevel(offHand) > 0) {
                bagUUID = getOrCreateBagUUID(offHand);
                bagLevel = getBagLevel(offHand);
            }

            if (bagUUID == null || bagLevel <= 0) return;

            StorageManager.BigStackHandler bagInventory = StorageManager.getInstance().getOrCreate(bagUUID, bagLevel);

            int remainingCount = pickedUpStack.getCount();

            for (int slot = 0; slot < bagInventory.getSlots(); slot++) {
                if (remainingCount <= 0) break;

                ItemStack toInsert = pickedUpStack.copy();
                toInsert.setCount(remainingCount);

                ItemStack remaining = bagInventory.insertItem(slot, toInsert, false);

                if (remaining.isEmpty()) {
                    remainingCount = 0;
                } else {
                    int inserted = remainingCount - remaining.getCount();
                    remainingCount = remaining.getCount();
                    if (inserted == 0) continue;
                }
            }

            if (remainingCount > 0) {
                itemEntity.getItem().setCount(remainingCount);
                return;
            }

            event.setCanceled(true);
            itemEntity.discard();

            StorageManager.getInstance().markDirty(bagUUID);

            // ⭐ 节流：每 SORT_INTERVAL 次拾取才排序一次（背包刚被填满大量物品时尤其受益）
            AtomicInteger counter = SORT_COUNTERS.computeIfAbsent(bagUUID, k -> new AtomicInteger());
            if (counter.incrementAndGet() >= SORT_INTERVAL) {
                counter.set(0);
                StorageManager.getInstance().sortInventory(bagUUID);
            }
        }
    }
}