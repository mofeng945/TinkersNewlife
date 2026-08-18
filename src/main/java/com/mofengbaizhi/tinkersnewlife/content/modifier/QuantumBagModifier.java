package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.storage.BagMenuProvider;
import com.mofengbaizhi.tinkersnewlife.content.storage.StorageManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

public class QuantumBagModifier extends Modifier {

    private static final ResourceLocation KEY_BAG_UUID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "bag_uuid_persistent");

    public QuantumBagModifier() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ============================================================
    //  📦 UUID 管理
    // ============================================================

    public static UUID getOrCreateBagUUID(ItemStack tool) {
        if (tool.isEmpty()) return null;
        ToolStack toolStack = ToolStack.from(tool);
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
        ToolStack toolStack = ToolStack.from(tool);
        if (toolStack == null) return null;

        IModDataView persistentData = toolStack.getPersistentData();
        if (persistentData.contains(KEY_BAG_UUID)) {
            return NbtUtils.loadUUID(persistentData.get(KEY_BAG_UUID));
        }
        return null;
    }

    public static int getBagLevel(ItemStack tool) {
        if (tool.isEmpty()) return 0;
        ToolStack toolStack = ToolStack.from(tool);
        if (toolStack == null) return 0;
        return toolStack.getModifierLevel(Modifiers.QUANTUM_BAG.getId());
    }

    // ============================================================
    //  📦 打开背包（仅通过 B 键调用）
    // ============================================================

    public static boolean tryOpenBag(Player player, ItemStack tool) {
        if (player == null || tool.isEmpty()) return false;
        if (player.level().isClientSide) return false;

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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemPickup(EntityItemPickupEvent event) {
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
        StorageManager.getInstance().sortInventory(bagUUID);
    }
}