package com.mofengbaizhi.tinkersnewlife.content.gourd;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 狱门疆（咒具）：右键地面放置成实体（空闲形态或已封印形态），右键放置的实体可拾取（仅放置者）。
 * <p>
 * 两种形态（空闲 / 已封印）注册为两个物品 id（gourd_jail / gourd_jail_sealed），
 * 物品栏与手中纹理各自独立；已封印形态物品携带球笼坐标与被封印实体 UUID。
 */
public class GourdJailItem extends Item {

    /** 放置的狱门疆实体是否初始为已封印 */
    private final boolean sealedByDefault;

    public GourdJailItem(Properties properties, boolean sealedByDefault) {
        super(properties);
        this.sealedByDefault = sealedByDefault;
    }

    /** 右键方块：放置狱门疆实体（消耗一个物品） */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        var pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        ServerLevel server = (ServerLevel) level;
        GourdJailEntity jail = new GourdJailEntity(server,
                new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), player.getUUID());
        // 从物品 NBT 恢复已封印状态（生物 NBT / 玩家 UUID + 球笼坐标）
        CompoundTag tag = stack.getTag();
        if (sealedByDefault && tag != null) {
            jail.restoreSealed(player.getUUID(), tag);
        }
        server.addFreshEntity(jail);
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /** 拾取狱门疆实体时生成对应形态的物品（空闲/已封印；生物封印携带完整 NBT，玩家封印携带名字） */
    public static ItemStack makeStack(boolean sealed, BlockPos cagePos, UUID prisoner, UUID owner,
                                      net.minecraft.nbt.CompoundTag mobNbt, @Nullable String prisonerName) {
        ItemStack stack = new ItemStack(sealed ? ModItems.GOURD_JAIL_SEALED.get() : ModItems.GOURD_JAIL.get());
        CompoundTag tag = new CompoundTag();
        if (owner != null) tag.putUUID(GourdJailHandler.KEY_OWNER, owner);
        if (sealed) {
            GourdJailHandler.writePos(tag, GourdJailHandler.KEY_CAGE_POS, cagePos);
            if (prisoner != null) tag.putUUID(GourdJailHandler.KEY_PRISONER, prisoner);
            if (mobNbt != null) tag.put(GourdJailHandler.KEY_MOB_NBT, mobNbt);
            if (prisonerName != null && !prisonerName.isEmpty()) {
                tag.putString(GourdJailHandler.KEY_PRISONER_NAME, prisonerName);
            }
        }
        if (!tag.isEmpty()) stack.setTag(tag);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // ⭐ 未封印形态：不在物品提示里放长篇说明（完整机制见帕秋莉手册）
        if (!sealedByDefault) return;
        // ⭐ 已封印形态：只显示当前封印的生物名字
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tooltip.add(prisonerDisplayName(tag).copy().withStyle(ChatFormatting.GRAY));
    }

    /** 解析已封印狱门疆中被封印生物的名字：生物 NBT（自定义名 → 实体类型）→ 玩家名 → 兜底 */
    private static Component prisonerDisplayName(CompoundTag tag) {
        if (tag.contains(GourdJailHandler.KEY_MOB_NBT)) {
            CompoundTag mob = tag.getCompound(GourdJailHandler.KEY_MOB_NBT);
            if (mob.contains("CustomName", Tag.TAG_STRING)) {
                try {
                    Component custom = Component.Serializer.fromJson(mob.getString("CustomName"));
                    if (custom != null) return custom;
                } catch (Throwable ignored) {
                }
            }
            String id = mob.getString("id");
            if (!id.isEmpty()) {
                return Component.translatable("entity." + id.replace(':', '.'));
            }
        }
        if (tag.contains(GourdJailHandler.KEY_PRISONER_NAME, Tag.TAG_STRING)) {
            return Component.literal(tag.getString(GourdJailHandler.KEY_PRISONER_NAME));
        }
        return Component.translatable("entity.generic.player");
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack));
    }
}
