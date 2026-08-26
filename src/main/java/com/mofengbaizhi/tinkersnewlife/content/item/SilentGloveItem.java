package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.GloveWeaponStorage;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveContainer;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SilentGloveItem extends ModifiableItem implements ICurioItem {

    public static final ToolDefinition SILENT_GLOVE_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "silent_glove"));

    private static final String TAG_VAULT_UUID = "vault_uuid";
    private static final String TAG_EXTRA_RINGS = "extra_rings";

    private static final Random RANDOM = new Random();

    private static final UUID RING_MODIFIER_UUID = UUID.nameUUIDFromBytes(
            new ResourceLocation(TinkersNewlife.MOD_ID, "ring_bonus")
                    .toString().getBytes(StandardCharsets.UTF_8)
    );

    public SilentGloveItem(Properties properties) {
        super(properties, SILENT_GLOVE_DEFINITION);
    }

    // ========== NBT 读写 ==========

    public static void setExtraRings(ItemStack stack, int amount) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_EXTRA_RINGS, Math.max(0, amount));
    }

    /**
     * 只读获取额外戒指槽数量。
     * ⭐ 不在 getter 内生成随机值：原实现首次调用（客户端 tooltip）会生成并写 NBT，
     * 而服务端生成的值不同，导致双端不一致。生成逻辑移到服务端
     * {@link #getOrCreateExtraRings(ItemStack)}（装备事件时调用）。
     */
    public static int getExtraRings(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        return tag.getInt(TAG_EXTRA_RINGS);
    }

    /**
     * 获取额外戒指槽数量；若未生成则生成随机值（1~6）并写入 NBT。
     * ⭐ 仅在服务端调用（装备/卸下事件），确保值由服务端权威生成并随物品同步。
     */
    public static int getOrCreateExtraRings(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_EXTRA_RINGS)) {
            int randomValue = RANDOM.nextInt(6) + 1;
            tag.putInt(TAG_EXTRA_RINGS, randomValue);
        }
        return tag.getInt(TAG_EXTRA_RINGS);
    }

    public static UUID getOrCreateVaultUUID(ItemStack stack) {
        var tag = stack.getOrCreateTag();
        if (tag.hasUUID(TAG_VAULT_UUID)) {
            return tag.getUUID(TAG_VAULT_UUID);
        }
        UUID uuid = UUID.randomUUID();
        tag.putUUID(TAG_VAULT_UUID, uuid);
        return uuid;
    }

    public static SilentGloveHandler getHandler(ItemStack stack) {
        return SilentGloveHandler.getOrCreate(getOrCreateVaultUUID(stack));
    }

    // ========== 序列化辅助 ==========
    private static byte[] serializeHandler(SilentGloveHandler handler) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            CompoundTag tag = handler.serializeNBT();
            NbtIo.write(tag, dos);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    // ========== 重新计算所有手套的总增量并应用 ==========

    private static void recalculateRingSlots(LivingEntity living) {
        CuriosApi.getCuriosInventory(living).ifPresent(inventory -> {
            int total = 0;
            var handsHandler = inventory.getStacksHandler("hands");
            if (handsHandler.isPresent()) {
                var stacks = handsHandler.get().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.getItem() instanceof SilentGloveItem) {
                        // ⭐ 服务端生成（若未生成），保证双端一致
                        total += getOrCreateExtraRings(stack);
                    }
                }
            }

            if (total > 0) {
                inventory.addTransientSlotModifier(
                        "ring",
                        RING_MODIFIER_UUID,
                        "ring_bonus",
                        total,
                        AttributeModifier.Operation.ADDITION
                );
            } else {
                inventory.removeSlotModifier("ring", RING_MODIFIER_UUID);
            }
        });
    }

    // ========== ICurioItem 核心 ==========

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return "hands".equals(context.identifier());
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return false;
    }

    @Override
    public void onEquip(SlotContext context, ItemStack prevStack, ItemStack stack) {
        if (context.entity().level().isClientSide()) return;
        recalculateRingSlots(context.entity());
    }

    @Override
    public void onUnequip(SlotContext context, ItemStack newStack, ItemStack stack) {
        if (context.entity().level().isClientSide()) return;
        recalculateRingSlots(context.entity());
    }

    // ========== 右键打开 GUI ==========

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            GloveWeaponStorage.clearPendingRecoveries(serverPlayer);

            UUID vaultUUID = getOrCreateVaultUUID(stack);
            SilentGloveHandler handler = getHandler(stack);
            byte[] dataBytes = serializeHandler(handler);

            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (containerId, inv, p) -> new SilentGloveContainer(containerId, inv, stack),
                            Component.translatable("container.tinkersnewlife.silent_glove")
                    ),
                    buf -> {
                        buf.writeUUID(vaultUUID);
                        buf.writeByteArray(dataBytes);
                    }
            );
        }
        return InteractionResultHolder.success(stack);
    }

    // ========== 工具提示 ==========

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        SilentGloveHandler handler = getHandler(stack);
        int extraRings = getExtraRings(stack);

        tooltip.add(Component.translatable("tooltip.tinkersnewlife.silent_glove.desc",
                handler.getUsedSlots(), handler.getSlots()));
        tooltip.add(Component.translatable("tooltip.tinkersnewlife.silent_glove.curios", extraRings));
        tooltip.add(Component.translatable("tooltip.tinkersnewlife.silent_glove.use"));
    }
}