package com.mofengbaizhi.tinkersnewlife.client.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.client.render.slashblade.IBladeRenderer

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * "画一遍刀"这个动作的抽象（逐字移植 TiCEX ✓）。
 *
 * <p>{@code BladeRenderStateMixin} 会把本体的 {@code BladeRenderState#renderOverrided(...)} 整段包起来 ✓，
 * 用 {@code original::call} 当这个接口的实现传进来 ✓ —— 于是我们可以在**真正画之前/之后**
 * 按材料换贴图换颜色、把同一份几何体画多遍 ✓，而不用改本体的渲染代码 ✓。
 */
public interface IBladeRenderer {

    void render(ItemStack stack, WavefrontObject model, String target, ResourceLocation texture, PoseStack matrixStackIn,
                MultiBufferSource bufferIn, int packedLightIn, Function<ResourceLocation, RenderType> renderTypeGetter,
                boolean enableEffect);
}
