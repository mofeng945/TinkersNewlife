package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

public class DragonStaffTrait extends Modifier implements TooltipModifierHook {

    public static final String MODIFIER_ID = "dragon_staff";
    private static final ResourceLocation KEY_MODE = new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_staff_mode");
    private static final ResourceLocation KEY_SLOTS = new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_staff_slots");
    
    private static final int BASE_SLOTS = 3;
    private static final int SLOTS_PER_LEVEL = 1;

    // ⭐ 攻击加成系数（Trait 与 Handler 共用，避免双份硬编码漂移）
    /** 每存储一条龙提供的攻击伤害加成 */
    public static final float ATTACK_BONUS_PER_DRAGON = 20.0f;

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        if (player == null) return;

        int level = modifier.getLevel();
        int maxSlots = getMaxSlots(level);

        // 模式
        int mode = tool.getPersistentData().getInt(KEY_MODE);
        String modeKey = mode == 0 ?
                "modifier.tinkersnewlife.dragon_staff.mode.recycle" :
                "modifier.tinkersnewlife.dragon_staff.mode.release";
        tooltip.add(Component.translatable(modeKey));

        // 槽位信息
        Tag tag = tool.getPersistentData().get(KEY_SLOTS);
        ListTag slots = tag instanceof ListTag ? (ListTag) tag : new ListTag();
        int storedCount = 0; // 统计已存储的龙
        for (int i = 0; i < maxSlots; i++) {
            if (i < slots.size()) {
                CompoundTag entry = slots.getCompound(i);
                int state = entry.getInt("state");
                String name = entry.getString("name");
                String type = entry.getString("type");
                String displayName = name.isEmpty() ? type : name;
                String statusKey = state == 0 ?
                        "modifier.tinkersnewlife.dragon_staff.slot.tamed" :
                        "modifier.tinkersnewlife.dragon_staff.slot.stored";
                tooltip.add(Component.literal("§7槽位 " + (i + 1) + ": §f" + displayName + " §7- ")
                        .append(Component.translatable(statusKey)));
                if (state == 1) storedCount++;
            } else {
                tooltip.add(Component.literal("§7槽位 " + (i + 1) + ": ")
                        .append(Component.translatable("modifier.tinkersnewlife.dragon_staff.slot.empty")));
            }
        }

        // 显示当前槽位状态
        tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_staff.slots", 
                slots.size(), maxSlots));

        // 显示攻击加成（如果存储了龙）
        if (storedCount > 0) {
            float bonus = storedCount * ATTACK_BONUS_PER_DRAGON;
            tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_staff.attack_bonus",
                    String.format("%.1f", bonus)));
        }

        // 按键提示
        if (tooltipKey == TooltipKey.SHIFT) {
            tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_staff.skill_hint"));
        } else {
            tooltip.add(Component.translatable("modifier.tinkersnewlife.dragon_staff.shift_hint"));
        }
    }

    public static int getMaxSlots(int level) {
        return BASE_SLOTS + (level - 1) * SLOTS_PER_LEVEL;
    }
}