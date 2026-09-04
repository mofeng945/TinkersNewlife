package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.storage.GloveWeaponStorage;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveContainer;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SilentGloveItem extends ModifiableItem implements ICurioItem {

    public static final ToolDefinition SILENT_GLOVE_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "silent_glove"));

    /** 旧原始标签键（兼容迁移用；updateStack 会整标签替换，旧值仅读一次迁入持久数据） */
    private static final String TAG_VAULT_UUID_LEGACY = "vault_uuid";

    /** ⭐ 持久数据键：存在 ToolStack 持久数据（ToolDataNBT）中，不会被 updateStack 的整标签替换抹掉 */
    private static final ResourceLocation TAG_VAULT_UUID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "vault_uuid");
    private static final ResourceLocation TAG_EXTRA_RINGS =
            new ResourceLocation(TinkersNewlife.MOD_ID, "extra_rings");

    private static final Random RANDOM = new Random();

    private static final UUID RING_MODIFIER_UUID = UUID.nameUUIDFromBytes(
            new ResourceLocation(TinkersNewlife.MOD_ID, "ring_bonus")
                    .toString().getBytes(StandardCharsets.UTF_8)
    );

    public SilentGloveItem(Properties properties) {
        super(properties, SILENT_GLOVE_DEFINITION);
    }

    // ========== NBT 读写（存 ToolStack 持久数据） ==========

    public static void setExtraRings(ItemStack stack, int amount) {
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return;
        tool.getPersistentData().putInt(TAG_EXTRA_RINGS, Math.max(0, amount));
        tool.updateStack(stack);
    }

    /**
     * 只读获取额外戒指槽数量（读取 ToolStack 持久数据，双端一致）。
     * ⭐ 不在此生成随机值：生成逻辑仅在服务端调用
     * {@link #getOrCreateExtraRings(ItemStack)}（生成/装备事件）。
     */
    public static int getExtraRings(ItemStack stack) {
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return 0;
        return tool.getPersistentData().getInt(TAG_EXTRA_RINGS);
    }

    /**
     * 获取额外戒指槽数量；若未生成则生成随机值（1~6）并写入 ToolStack 持久数据。
     * ⭐ 仅在服务端调用（生成/装备事件），确保值由服务端权威生成并随物品同步；
     * 持久数据属于工具自身 NBT，任何 updateStack 都不会抹掉它，因此值一经生成永久固定。
     */
    public static int getOrCreateExtraRings(ItemStack stack) {
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return 0;
        ToolDataNBT persistent = tool.getPersistentData();
        if (!persistent.contains(TAG_EXTRA_RINGS)) {
            persistent.putInt(TAG_EXTRA_RINGS, RANDOM.nextInt(6) + 1);
            // ⭐ 写回物品标签，确保持久化并随物品同步
            tool.updateStack(stack);
        }
        return persistent.getInt(TAG_EXTRA_RINGS);
    }

    /** 只读获取空间奇点库 UUID（无则返回 null，供工具提示等只读场景使用） */
    @Nullable
    public static UUID getVaultUUID(ItemStack stack) {
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return null;
        ToolDataNBT persistent = tool.getPersistentData();
        if (!persistent.contains(TAG_VAULT_UUID)) return null;
        return NbtUtils.loadUUID(persistent.get(TAG_VAULT_UUID));
    }

    public static UUID getOrCreateVaultUUID(ItemStack stack) {
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return null;
        ToolDataNBT persistent = tool.getPersistentData();
        if (persistent.contains(TAG_VAULT_UUID)) {
            return NbtUtils.loadUUID(persistent.get(TAG_VAULT_UUID));
        }

        // 兼容旧存档：旧值写在物品原始标签里（updateStack 会抹掉，读一次迁移到持久数据）
        UUID legacy = null;
        if (stack.getTag() != null && stack.getTag().hasUUID(TAG_VAULT_UUID_LEGACY)) {
            legacy = stack.getTag().getUUID(TAG_VAULT_UUID_LEGACY);
        }
        UUID uuid = legacy != null ? legacy : UUID.randomUUID();
        persistent.put(TAG_VAULT_UUID, NbtUtils.createUUID(uuid));
        tool.updateStack(stack);
        return uuid;
    }

    public static SilentGloveHandler getHandler(ItemStack stack) {
        UUID uuid = getOrCreateVaultUUID(stack);
        return uuid == null ? null : SilentGloveHandler.getOrCreate(uuid);
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

    /**
     * 待重算的玩家（UUID → true）。
     * ⭐ 延迟到下一服务端 tick 再应用：curios 不允许在 GUI/槽位操作进行中直接改槽位大小，
     * 否则打开 curios 物品栏时槽位数同步不一致会触发客户端 IndexOutOfBounds。
     */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Boolean> PENDING_RECALC =
            new java.util.concurrent.ConcurrentHashMap<>();

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

    /** 服务端 tick 统一处理延迟重算 */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class RingSlotHandler {
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (PENDING_RECALC.isEmpty()) return;
            var server = event.getServer();
            if (server == null) return;
            for (UUID playerId : PENDING_RECALC.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive()) {
                    PENDING_RECALC.remove(playerId);
                    continue;
                }
                // 应用戒指槽变化（curios 自身会在槽位变化时同步客户端并触发
                // SlotModifiersUpdatedEvent，客户端据此原地重建打开的 curios 菜单）
                recalculateRingSlots(player);
                PENDING_RECALC.remove(playerId);
            }
        }
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
        // ⭐ 延迟到下一 tick 应用，避免在 curios GUI 操作中改槽位大小
        PENDING_RECALC.put(context.entity().getUUID(), true);
    }

    @Override
    public void onUnequip(SlotContext context, ItemStack newStack, ItemStack stack) {
        if (context.entity().level().isClientSide()) return;
        PENDING_RECALC.put(context.entity().getUUID(), true);
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

        int used = handler != null ? handler.getUsedSlots() : 0;
        int total = handler != null ? handler.getSlots() : 0;
        tooltip.add(Component.translatable("tooltip.tinkersnewlife.silent_glove.desc", used, total));
        tooltip.add(Component.translatable("tooltip.tinkersnewlife.silent_glove.curios", extraRings));
        tooltip.add(Component.translatable("tooltip.tinkersnewlife.silent_glove.use"));
    }
}