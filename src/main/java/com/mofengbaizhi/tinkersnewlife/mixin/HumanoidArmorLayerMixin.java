package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.item.ConstructedBlueprintItem;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 拟造蓝本·<b>护甲外观</b>代理。
 *
 * <p>原版 {@code HumanoidArmorLayer#renderArmorPiece} 写死了
 * {@code if (stack.getItem() instanceof ArmorItem)} 才会渲染护甲——蓝本不是 {@code ArmorItem}
 * （虽然属性/防御已经由 {@code getAttributeModifiers} 转发到位），于是会出现"穿着有防御、但看着像没穿"。
 *
 * <p>做法：在该方法入口把参数里的 {@code ItemStack} 换成"真护甲影子栈"（同 NBT、换成目标护甲物品），
 * 后面那条原版渲染链（贴图、模型、盔甲纹饰）就都按目标护甲正常走了。非蓝本栈原样返回，绝不影响正常护甲。
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @ModifyVariable(method = "renderArmorPiece", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack tinkersnewlife$blueprintArmor(ItemStack stack) {
        return ConstructedBlueprintItem.forArmorRender(stack);
    }

    /** 兜底注入：直连 SRG 方法名，绕开 refmap */
    @ModifyVariable(method = "m_117118_", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private ItemStack tinkersnewlife$blueprintArmorSrg(ItemStack stack) {
        return ConstructedBlueprintItem.forArmorRender(stack);
    }
}
