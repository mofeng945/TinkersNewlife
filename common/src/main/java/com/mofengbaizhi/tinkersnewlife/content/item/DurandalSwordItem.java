package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;

import javax.annotation.Nullable;
import java.util.List;

public class DurandalSwordItem extends ModifiableItem {

    public static final ToolDefinition DURANDAL_SWORD_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "durandal_sword"));

    public DurandalSwordItem(Properties properties) {
        super(properties, DURANDAL_SWORD_DEFINITION);
    }
    public static int getHitCount(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.getInt("durandal_hits");
    }

    public static void setHitCount(ItemStack stack, int count) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("durandal_hits", Math.max(0, count));
    }

    // ✅ 不重写任何方法，让父类 ModifiableItem 处理所有工具提示
    // 所有匠魂默认信息都会正常显示
}