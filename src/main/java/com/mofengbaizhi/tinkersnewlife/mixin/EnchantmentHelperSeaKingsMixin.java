package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.handler.SeaKingsPowerHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 海王之力（§960）：**无视水中挖掘速度惩罚** ＋ **钓鱼竿自动 海之眷顾 II / 饵钓 I**（§968）。
 *
 * <p>为什么挂这三个静态方法（全部实查过 ✓ 不是猜 ✗）：
 * <ul>
 *   <li>{@code EnchantmentHelper#hasAquaAffinity(LivingEntity)} ✓ —— 原版
 *     {@code Player#getDigSpeed} 里的水中惩罚就是 {@code if (isEyeInFluid(WATER) && !hasAquaAffinity(this)) f /= 5F;} ✓
 *     ⇒ 让它返回 true ＝ **无视水中挖掘惩罚** ✓（和"水下亲和"附魔同一条路径 ✓ 最省 ✓）；</li>
 *   <li>{@code getFishingLuckBonus(ItemStack)} ✓ / {@code getFishingSpeedBonus(ItemStack)} ✓ ——
 *     {@code FishingHook} 就是读这两个值 ✓ ⇒ 直接返回"至少 2 / 至少 1"✓
 *     匠魂工具不能正常附魔 ✗（§960 已记 ✓）所以只能这样**模拟** ✓。</li>
 * </ul>
 * ⚠ 双注解（named ＋ SRG ✓ 本仓无注解处理器 ✗）：SRG 名查自官方映射表 ✓
 * {@code hasAquaAffinity→m_44934_} ✓ {@code getFishingLuckBonus→m_44904_} ✓ {@code getFishingSpeedBonus→m_44916_} ✓。
 */
@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperSeaKingsMixin {

    private static boolean tinkersnewlife$holds(ItemStack stack) {
        return !stack.isEmpty() && SeaKingsPowerHandler.hasTrait(stack);
    }

    private static boolean tinkersnewlife$entityHas(LivingEntity entity) {
        return entity instanceof Player player && SeaKingsPowerHandler.wearsOrHolds(player);
    }

    @Inject(method = "hasAquaAffinity(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void tinkersnewlife$aquaAffinity(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (tinkersnewlife$entityHas(entity)) cir.setReturnValue(true);
    }

    @Inject(method = "m_44934_(Lnet/minecraft/world/entity/LivingEntity;)Z", remap = false, at = @At("HEAD"), cancellable = true)
    private static void tinkersnewlife$aquaAffinitySrg(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (tinkersnewlife$entityHas(entity)) cir.setReturnValue(true);
    }

    @Inject(method = "getFishingLuckBonus(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private static void tinkersnewlife$fishingLuck(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (tinkersnewlife$holds(stack)) cir.setReturnValue(2);
    }

    @Inject(method = "m_44904_(Lnet/minecraft/world/item/ItemStack;)I", remap = false, at = @At("HEAD"), cancellable = true)
    private static void tinkersnewlife$fishingLuckSrg(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (tinkersnewlife$holds(stack)) cir.setReturnValue(2);
    }

    @Inject(method = "getFishingSpeedBonus(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private static void tinkersnewlife$fishingSpeed(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (tinkersnewlife$holds(stack)) cir.setReturnValue(1);
    }

    @Inject(method = "m_44916_(Lnet/minecraft/world/item/ItemStack;)I", remap = false, at = @At("HEAD"), cancellable = true)
    private static void tinkersnewlife$fishingSpeedSrg(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (tinkersnewlife$holds(stack)) cir.setReturnValue(1);
    }
}