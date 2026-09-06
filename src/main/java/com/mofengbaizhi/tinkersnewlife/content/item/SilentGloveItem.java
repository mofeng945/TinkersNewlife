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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
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

    /** 按槽位索引派生的固定 uuid（双手分别生效，互不覆盖；卸下时按各自 uuid 精确移除） */
    private static UUID ringModifierUuid(int index) {
        return UUID.nameUUIDFromBytes((RING_MODIFIER_UUID + "#" + index).getBytes(StandardCharsets.UTF_8));
    }

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

    // ========== 动态戒指槽（官方 ICurioItem 属性槽机制） ==========

    /**
     * 官方推荐做法（Enigmatic Legacy 等成熟模组同款）：
     * 覆写 {@code ICurioItem#getAttributeModifiers(SlotContext, UUID, ItemStack)}，
     * 在返回的属性表中放入 Curios 的槽位修饰符（SlotAttribute 条目）。
     * Curios 会在<b>佩戴时自动加槽、卸下时自动移除、登录/复活时自动恢复</b>，
     * 全程由 Curios 自身的统一流程驱动——不再手工 add/remove transient，
     * 避免与 Curios 内部同步时序冲突造成槽位错位/物品"复制"显示。
     */
    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
                AttributeModifier> attrs = com.google.common.collect.LinkedHashMultimap.create();
        int extra = getExtraRings(stack);
        if (extra <= 0 && slotContext.entity() != null && !slotContext.entity().level().isClientSide) {
            // 服务端装备结算时惰性生成一次随机值（此后固定），保证加成存在
            extra = getOrCreateExtraRings(stack);
        }
        if (extra > 0) {
            // 双手（hands 槽 index 0/1）各用独立 uuid，两只手套的加成可同时叠加
            CuriosApi.getCuriosHelper().addSlotModifier(attrs, "ring",
                    ringModifierUuid(Math.max(0, slotContext.index())),
                    extra, AttributeModifier.Operation.ADDITION);
        }
        return attrs;
    }

    /**
     * 旧版本残留清理：老版本用单一 RING_MODIFIER_UUID 手工 add/remove transient，
     * 可能残留未清除的槽位加成。新机制改用手派生 uuid，登录时清一次旧 key 即可收敛
     * （随后 Curios 槽变化检测会按新机制自动重新应用正确加成）。
     */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class LegacyCleanup {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                CuriosApi.getCuriosInventory(sp).ifPresent(inv ->
                        inv.removeSlotModifier("ring", RING_MODIFIER_UUID));
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