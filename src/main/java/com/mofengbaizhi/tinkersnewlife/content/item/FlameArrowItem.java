package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 火焰箭（手持蓄力）：灶·开蓄力时临时装备到主手的视觉物品。
 * <p>
 * 纹理为动态（mcmeta 动画，火焰闪烁），蓄力期间拿在手里，
 * 无拉弓动画；不可被玩家主动使用/获取，蓄力结束后由术式恢复原主手物品。
 */
public class FlameArrowItem extends Item {

    public FlameArrowItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /** 不可主动使用：仅由术式驱动换手展示 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
