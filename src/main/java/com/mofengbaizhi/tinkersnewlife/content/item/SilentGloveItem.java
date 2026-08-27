package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.GloveWeaponStorage;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveContainer;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
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
import java.util.ArrayList;
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

    // ========== 回收列表（玩家手动放入的固定物品，忽略耐久） ==========

    private static final String TAG_RECYCLE_LIST = "recycle_list";

    /**
     * 获取回收列表：玩家通过手套 GUI 手动放入过的【固定物品】NBT 快照列表。
     * <p>
     * 匹配规则：物品相同且 NBT 完全一致，仅忽略耐久字段（Damage）。
     * 即玩家放入哪一件具体物品（材质/强化/修饰完全相同），之后只回收
     * 与之匹配的那一件，而非整个物品类型。自动回收不会加入列表；
     * 列表持久保存在手套 NBT，直到玩家下次打开手套放入/取出才更改。
     */
    public static List<CompoundTag> getRecycleList(ItemStack gloveStack) {
        List<CompoundTag> list = new ArrayList<>();
        CompoundTag tag = gloveStack.getTag();
        if (tag != null && tag.contains(TAG_RECYCLE_LIST, Tag.TAG_LIST)) {
            ListTag raw = tag.getList(TAG_RECYCLE_LIST, Tag.TAG_COMPOUND);
            for (int i = 0; i < raw.size(); i++) {
                list.add(raw.getCompound(i));
            }
        }
        return list;
    }

    private static void saveRecycleList(ItemStack gloveStack, List<CompoundTag> list) {
        ListTag raw = new ListTag();
        for (CompoundTag c : list) {
            raw.add(c);
        }
        gloveStack.getOrCreateTag().put(TAG_RECYCLE_LIST, raw);
    }

    /** 手动放入物品时，将该固定物品加入回收列表（去重） */
    public static void addToRecycleList(ItemStack gloveStack, ItemStack item) {
        if (gloveStack.isEmpty() || item.isEmpty()) return;
        CompoundTag identity = saveIdentity(item);
        if (identity.isEmpty()) return;
        List<CompoundTag> list = getRecycleList(gloveStack);
        boolean exists = false;
        for (CompoundTag c : list) {
            if (identityEquals(c, identity)) { exists = true; break; }
        }
        if (!exists) {
            list.add(identity);
            saveRecycleList(gloveStack, list);
        }
    }

    /** 手动取出物品时，将该固定物品从回收列表移除 */
    public static void removeFromRecycleList(ItemStack gloveStack, ItemStack item) {
        if (gloveStack.isEmpty() || item.isEmpty()) return;
        CompoundTag identity = saveIdentity(item);
        if (identity.isEmpty()) return;
        List<CompoundTag> list = getRecycleList(gloveStack);
        boolean removed = list.removeIf(c -> identityEquals(c, identity));
        if (removed) {
            saveRecycleList(gloveStack, list);
        }
    }

    /** 判断该固定物品是否在回收列表中（忽略耐久） */
    public static boolean isInRecycleList(ItemStack gloveStack, ItemStack item) {
        if (gloveStack.isEmpty() || item.isEmpty()) return false;
        CompoundTag identity = saveIdentity(item);
        if (identity.isEmpty()) return false;
        for (CompoundTag c : getRecycleList(gloveStack)) {
            if (identityEquals(c, identity)) return true;
        }
        return false;
    }

    /** 物品身份 NBT：完整 NBT 快照，仅移除耐久字段 Damage */
    private static CompoundTag saveIdentity(ItemStack stack) {
        CompoundTag tag = stack.save(new CompoundTag());
        if (tag.contains("tag", Tag.TAG_COMPOUND)) {
            tag.getCompound("tag").remove("Damage");
        }
        return tag;
    }

    /** 两个身份 NBT 是否表示同一件固定物品（忽略数量与耐久） */
    private static boolean identityEquals(CompoundTag a, CompoundTag b) {
        ItemStack sa = ItemStack.of(a);
        ItemStack sb = ItemStack.of(b);
        if (sa.isEmpty() || sb.isEmpty()) return false;
        return ItemStack.isSameItemSameTags(sa, sb);
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