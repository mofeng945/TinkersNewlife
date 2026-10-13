package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.StarfallArrow;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * TNL879 \u661f\u9668\u60b2\u53f9 Starfall Lament\uff08\u2605\u7167\u642c\u53c2\u8003\u5b9e\u73b0 HomingBowItem \u2713\uff09
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u5b8c\u5168\u6a21\u4eff\u53c2\u8003\u5b9e\u73b0\u505a\u5f13\uff0c\u53ea\u662f\u628a\u4ed6\u90a3\u4e2a\u5929\u964d\u7684\u7bad\u6362\u6210\u4ece\u5f13\u5c04\u51fa\u53bb" \u2713
 * \u21d2 \u2605\u672c\u7c7b\u5b8c\u5168\u7167\u642c\u53c2\u8003\uff1a\u2605\u84c4\u529b\u5f13 \u2713\u3001\u2605``AutoFire`` NBT \u6807\u7b7e\u81ea\u52a8\u8fde\u53d1 \u2713\u3001
 * \u2605Multishot \u4e09\u7bad \u2713\u3001\u2605**\u6d41\u52a8\u6e10\u53d8\u540d\u5b57** \u2713\u3002
 * \u2605\u4e0e\u53c2\u8003\u7684\u5dee\u522b\u53ea\u6709\uff1a\u2605\u7bad\u662f**\u4ece\u5f13\u5c04\u51fa**\uff08\u53c2\u8003\u91cc\u53e6\u6709\u4e00\u5957\u5929\u964d\u903b\u8f91 \u2717 \u6211\u4eec\u4e0d\u505a \u2713\uff09\u3002
 */
public class StarfallLamentItem extends BowItem {

    private static final int NAME_COLOR_START = 0x66CCFF;
    private static final int NAME_COLOR_END = 0xFFB9E8;
    private static final float FLOW_SPEED = 1.0F;

    public static final List<Enchantment> EXTRA_WHITELIST = new ArrayList<>();

    public StarfallLamentItem() {
        super(new Properties().stacksTo(1).durability(384).rarity(Rarity.EPIC));
    }

    @Override
    public Component getName(ItemStack stack) {
        return buildFlowGradient(super.getName(stack));
    }

    private static Component buildFlowGradient(Component base) {
        String text = base.getString();
        if (text.isEmpty()) {
            return base;
        }
        int len = text.length();
        double timeSec = System.currentTimeMillis() / 1000.0D;
        MutableComponent result = Component.literal("");
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                result.append(Component.literal(" "));
                continue;
            }
            double phase = i * 0.25D + timeSec * FLOW_SPEED;
            double t = (Math.sin(phase * Math.PI * 2.0D) + 1.0D) / 2.0D;
            int color = lerpColor(NAME_COLOR_START, NAME_COLOR_END, (float) t);
            result.append(Component.literal(String.valueOf(c)).setStyle(Style.EMPTY.withColor(color).withBold(true)));
        }
        return result;
    }

    private static int lerpColor(int c1, int c2, float f) {
        if (f < 0) {
            f = 0;
        }
        if (f > 1) {
            f = 1;
        }
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * f);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * f);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * f);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment == Enchantments.MULTISHOT || EXTRA_WHITELIST.contains(enchantment);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        var enchants = EnchantmentHelper.getEnchantments(book);
        for (Enchantment e : enchants.keySet()) {
            if (e != Enchantments.MULTISHOT && !EXTRA_WHITELIST.contains(e)) {
                return false;
            }
        }
        return true;
    }

    /** TNL882 \u2605\u2605\u81ea\u52a8\u5c04\u51fb**\u9ed8\u8ba4\u63a5\u4e0a** \u2713
     *
     * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u628a\u81ea\u52a8\u5c04\u51fb\u63a5\u4e0a" \u2713
     * \u21d2 \u2605\u6ca1\u6709 NBT \u65f6**\u9ed8\u8ba4\u5f00\u542f** \u2713\uff08\u2605\u53c2\u8003\u5b9e\u73b0\u662f\u9ed8\u8ba4\u5173 \u2717\uff09\uff1b
     * \u2605\u60f3\u5173\u6389\uff1a\u2605\u7ed9\u5f13\u52a0 NBT ``AutoFire: 0b`` \u2713\u3002
     */
    public static boolean isAutoFire(ItemStack stack) {
        return !stack.hasTag() || stack.getTag().getBoolean("AutoFire");
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) {
            return;
        }
        boolean autoFire = isAutoFire(stack);
        if (autoFire) {
            int useTime = this.getUseDuration(stack) - remainingTicks;
            if (useTime >= 10) {
                if (!level.isClientSide) {
                    fireArrows(player, stack, level, 1.0F);
                }
                player.stopUsingItem();
                player.startUsingItem(player.getUsedItemHand());
            }
        } else {
            super.onUseTick(level, entity, stack, remainingTicks);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        boolean autoFire = isAutoFire(stack);
        if (!autoFire && entity instanceof Player player) {
            int useTime = this.getUseDuration(stack) - timeLeft;
            float power = getPowerForTime(useTime);
            if (power < 0.1F) {
                return;
            }
            if (!level.isClientSide) {
                fireArrows(player, stack, level, power);
            }
        }
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return !isAutoFire(stack);
    }

    /** \u2605\u5f00\u4e00\u7ec4\u7bad\uff08\u2605\u4e0e\u53c2\u8003\u4e00\u81f4\uff1a\u2605\u4e3b\u7bad \u002b Multishot \u5219\u4e09\u7bad \u2713\uff09 */
    private void fireArrows(Player player, ItemStack stack, Level level, float power) {
        float velocity = power;
        boolean hasMultishot = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, stack) > 0;
        StarfallArrow main = new StarfallArrow(ModEntities.STARFALL_ARROW.get(), player, level);
        main.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity * 3.0F, 1.0F);
        main.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(main);
        if (hasMultishot) {
            float off = 5.0F;
            StarfallArrow left = new StarfallArrow(ModEntities.STARFALL_ARROW.get(), player, level);
            left.shootFromRotation(player, player.getXRot(), player.getYRot() + off, 0.0F, velocity * 3.0F, 1.0F);
            left.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(left);
            StarfallArrow right = new StarfallArrow(ModEntities.STARFALL_ARROW.get(), player, level);
            right.shootFromRotation(player, player.getXRot(), player.getYRot() - off, 0.0F, velocity * 3.0F, 1.0F);
            right.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(right);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static float getPowerForTime(int useTime) {
        float f = (float) useTime / 10.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }
        return f;
    }

    /** \u2605\u4f9b\u4ee5\u540e\u5199 Boss AI \u7528\uff08\u2605\u73b0\u5728\u6ca1\u7528\u4e0a \u2717\uff09 */
    public static StarfallArrow fireAt(Level level, LivingEntity shooter, LivingEntity target) {
        StarfallArrow arrow = new StarfallArrow(ModEntities.STARFALL_ARROW.get(), shooter, level);
        net.minecraft.world.phys.Vec3 eye = shooter.getEyePosition();
        net.minecraft.world.phys.Vec3 dir = target != null
                ? target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(eye).normalize()
                : shooter.getLookAngle();
        if (target != null) {
            arrow.tnl$setHomingTarget(target);
        }
        arrow.setPos(eye.x + dir.x * 0.6D, eye.y + dir.y * 0.6D, eye.z + dir.z * 0.6D);
        arrow.shoot(dir.x, dir.y, dir.z, 3.0F, 0.0F);
        level.addFreshEntity(arrow);
        return arrow;
    }
}
