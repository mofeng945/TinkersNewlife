package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;

import javax.annotation.Nullable;
import java.util.List;

public class ModularStaffItem extends ModifiableItem {

    public static final ToolDefinition MODULAR_STAFF_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "modular_staff"));

    public ModularStaffItem(Properties properties) {
        super(properties, MODULAR_STAFF_DEFINITION);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        // 不添加额外描述
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 仅在服务端尝试施法，且铁魔法可用时
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (IronSpellsReflector.isIronSpellsAvailable()) {
                boolean success = IronSpellsReflector.tryCastSpell(serverPlayer, stack, hand);
                if (success) {
                    // 施法成功，消耗动作
                    return InteractionResultHolder.success(stack);
                }
            }
        }

        // 施法失败或铁魔法不可用时，不消耗动作，让其他处理（或什么都不做）
        return InteractionResultHolder.pass(stack);
    }
}