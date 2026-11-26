package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.renderer.BlueprintModels;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * 构筑术式·拟造蓝本的<b>图标代理</b>：在 {@code ItemRenderer#getModel} 头部拦截，
 * 蓝本栈直接返回"目标物品的模型"（含目标自己的 overrides）。
 *
 * <p>选这一层的原因：{@code getModel} 是物品渲染的唯一入口，在这里换模型，
 * 相机变换、渲染层、弓的拉弦/指南针指向等全部自然沿用目标物品的行为。
 *
 * <p>注入器与 {@code EntityRenderDispatcherMixin} 同套路：MCP 名（靠手写 refmap 解析）＋ SRG 名直连兜底。
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$blueprintModel(ItemStack stack, @Nullable Level level,
                                              @Nullable LivingEntity entity, int seed,
                                              CallbackInfoReturnable<BakedModel> cir) {
        BakedModel model = BlueprintModels.resolve(stack, (ItemRenderer) (Object) this, level, entity, seed);
        if (model != null) cir.setReturnValue(model);
    }

    /** 兜底注入：直连 SRG 方法名，绕开 refmap */
    @Inject(method = "m_174264_", at = @At("HEAD"), cancellable = true, remap = false)
    private void tinkersnewlife$blueprintModelSrg(ItemStack stack, @Nullable Level level,
                                                 @Nullable LivingEntity entity, int seed,
                                                 CallbackInfoReturnable<BakedModel> cir) {
        BakedModel model = BlueprintModels.resolve(stack, (ItemRenderer) (Object) this, level, entity, seed);
        if (model != null) cir.setReturnValue(model);
    }
}
