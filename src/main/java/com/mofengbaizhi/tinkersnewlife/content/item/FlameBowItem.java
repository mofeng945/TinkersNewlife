package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 火焰弓：灶·开蓄力时临时装备到主手的视觉物品。
 * <p>
 * 仅用于播放原版弓的拉弓动画（UseAnim.BOW + 长持续），不可被玩家主动使用/获取，
 * 蓄力结束后由术式恢复原主手物品。
 */
public class FlameBowItem extends Item {

    public FlameBowItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW; // 拉弓动画
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000; // 蓄力期间不会自然结束
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 附魔光泽，火焰感
    }

    /** 不可主动使用：仅由术式驱动 startUsingItem */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
