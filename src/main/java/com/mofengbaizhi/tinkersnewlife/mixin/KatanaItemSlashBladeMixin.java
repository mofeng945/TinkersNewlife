package com.mofengbaizhi.tinkersnewlife.mixin;

import java.util.function.Consumer;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.tools.item.IModifiable;

/**
 * <b>§1020 拔刀剑 × 匠魂：刀"断"的时候别按拔刀剑的规矩来</b>
 * —— <b>逐行照抄 TiCEX</b> 的 {@code moffy.ticex.mixin.slashblade.ItemSlashBladeMixin} ✓。
 *
 * <p>⚠ 注意：本仓此前是**反编译**得到的版本 ✗，反编译器把消费器里的
 * {@code user.broadcastBreakEvent(user.getUsedItemHand())} 错译成了 {@code setItemInHand(...)} ✗；
 * 这里已按 **GitHub 真源码**改正 ✓。
 *
 * <p>⚠ 拔刀剑不在场时本 mixin 静默不生效 ✓。
 */
@Mixin(value = ItemSlashBlade.class, remap = false)
public class KatanaItemSlashBladeMixin {

    @Inject(at = @At("HEAD"), method = "getOnBroken", cancellable = true, remap = false)
    private static void getOnBroken(ItemStack stack, CallbackInfoReturnable<Consumer<LivingEntity>> cb) {
        if (stack.getItem() instanceof IModifiable) {
            cb.setReturnValue(user ->
                    user.broadcastBreakEvent(user.getUsedItemHand())
            );
        }
    }
}
