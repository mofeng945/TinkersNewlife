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
 * （照 <b>TiCEX</b> 的 {@code ItemSlashBladeMixin} 移植 ✓）。
 *
 * <p>本体 {@code ItemSlashBlade#getOnBroken(ItemStack)} 返回的消费器会按**它自己的**规矩处理
 * "刀耐久耗尽" ✗（对原生刀没问题 ✓）；但我们的刀耐久是**匠魂工具耐久** ✓
 * （§1007 的 {@code ToolBladeStateCapability} 把那三个方法接到匠魂上 ✓）
 * ⇒ 让本体那套去处理会把匠魂工具的状态搞乱 ✗。
 *
 * <p>所以对匠魂可改造物品，换成"刷新一下手里这把"的消费器 ✓
 * （= TiCEX 的实现 ✓：{@code user -> user.setItemInHand(user.getUsedItemHand(), user.getMainHandItem())} ✓），
 * 让匠魂自己处理破损显示与后续 ✓。
 *
 * <p>⚠ 拔刀剑不在场时本 mixin 静默不生效 ✓。
 */
@Mixin(value = ItemSlashBlade.class, remap = false)
public class KatanaItemSlashBladeMixin {

    /**
     * 在 {@code getOnBroken} 开头插手 ✓（它是静态方法 ✓，所以注入方法也必须 static ✓）。
     *
     * @param stack 刀（物品栈）✓
     * @param cir   返回值回调 ⇒ 塞入我们自己的消费器 ✓
     */
    @Inject(method = "getOnBroken", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tnl$getOnBroken(ItemStack stack, CallbackInfoReturnable<Consumer<LivingEntity>> cir) {
        if (stack.getItem() instanceof IModifiable) {
            cir.setReturnValue(user -> user.setItemInHand(user.getUsedItemHand(), user.getMainHandItem()));
        }
    }
}
