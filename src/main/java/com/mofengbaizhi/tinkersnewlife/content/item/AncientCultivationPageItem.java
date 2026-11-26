package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.unlock.FlyingSwordUnlock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AncientCultivationPageItem extends Item {
    public AncientCultivationPageItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack page = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && !player.isSpectator()) {
            if (FlyingSwordUnlock.unlock(serverPlayer)) {
                if (!player.getAbilities().instabuild) page.shrink(1);
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.flying_sword.unlocked"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.flying_sword.already_unlocked"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(page, level.isClientSide);
    }
}
