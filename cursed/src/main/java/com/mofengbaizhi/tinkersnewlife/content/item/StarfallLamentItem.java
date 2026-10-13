package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.StarfallArrow;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * TNL877 \u661f\u9668\u60b2\u53f9 Starfall Lament\uff08\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff09
 *
 * <p>\u2605\u5f31\u8ffd\u8e2a\u578b\u5f13 \u2713\uff1a\u2605\u53ea\u9884\u6d4b\u4e00\u6b21\u4f4d\u7f6e \u2713\uff1b\u2605\u5c04\u901f **1 \u79d2\u4e00\u53d1** \u2713\u3002
 * \u2605\u5b9e\u4f53\u884c\u4e3a\u5728 {@link StarfallArrow} \u91cc \u2713\u3002
 */
public class StarfallLamentItem extends Item {

    /** \u2605\u5c04\u901f\uff1a1 \u79d2 \u2713 */
    public static final int COOLDOWN_TICKS = 20;
    /** \u2605\u521d\u901f \u2713 */
    public static final float VELOCITY = 2.6F;

    public StarfallLamentItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(0));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            LivingEntity target = null;
            if (player.getLastHurtMob() != null && player.getLastHurtMob().isAlive()) {
                target = player.getLastHurtMob();
            }
            fire(level, player, target);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** \u2605\u5f00\u4e00\u53d1\uff08\u2605\u602a\u7269/Boss \u4ee5\u540e\u53ef\u76f4\u63a5\u8c03\u8fd9\u4e2a \u2713\uff09 */
    public static StarfallArrow fire(Level level, LivingEntity shooter, LivingEntity target) {
        StarfallArrow arrow = new StarfallArrow(ModEntities.STARFALL_ARROW.get(), shooter, level);
        Vec3 eye = shooter.getEyePosition();
        Vec3 dir;
        if (target != null) {
            dir = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(eye).normalize();
            arrow.tnl$setHomingTarget(target);                 // \u2605\u4e00\u6b21\u6027\u9884\u6d4b \u2713
        } else {
            dir = shooter.getLookAngle();
        }
        arrow.setPos(eye.x + dir.x * 0.6D, eye.y + dir.y * 0.6D, eye.z + dir.z * 0.6D);
        arrow.shoot(dir.x, dir.y, dir.z, VELOCITY, 0.0F);
        level.addFreshEntity(arrow);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.7F, 1.4F);
        return arrow;
    }
}
