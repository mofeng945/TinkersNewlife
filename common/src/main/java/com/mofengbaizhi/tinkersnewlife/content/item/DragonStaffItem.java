package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 驯龙杖（Dragon Staff）
 * <p>
 * 功能说明：
 * 1. 右键切换模式（回收/释放），需潜行 + 右键
 * 2. 右键龙实体回收（模式0）
 * 3. 右键方块释放龙（模式1）
 * 4. 按 G 键执行驯服技能（需消耗经验）
 * </p>
 * <p>
 * 所有逻辑委托给 {@link com.mofengbaizhi.tinkersnewlife.content.handler.DragonStaffHandler}
 * </p>
 */
public class DragonStaffItem extends ModifiableItem {

    // 公开的静态工具定义，供数据生成器使用
    public static final ToolDefinition DRAGON_STAFF_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_staff"));

    public DragonStaffItem(Item.Properties properties) {
        super(properties, DRAGON_STAFF_DEFINITION);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.tinkersnewlife.dragon_staff.desc2"));
        tooltip.add(Component.translatable("item.tinkersnewlife.dragon_staff.desc3"));
    }
}