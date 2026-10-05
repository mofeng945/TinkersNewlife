package com.mofengbaizhi.tinkersnewlife.client.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.client.render.slashblade.TicEXSBRenderers

import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ContextFrame;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ItemRenderContext;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.KatanaContexts;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.Optional;
import java.util.function.Function;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>把"匠魂材料"翻译成刀身外观</b> —— 移植 TiCEX {@code TicEXSBRenderers}（MIT）。
 *
 * <p>做法（与 TiCEX 完全同思路 ✓）：把本体那次 {@code BladeRenderState#renderOverrided(...)}
 * 按**材料个数各画一遍** ✓ —— 第 i 遍用第 i 个部件（刀鞘/刀身/手柄 ✓）的材料贴图 ✓
 * 并用该材料的 {@code vertexColor} 覆盖颜色 ✓（没有按材料的贴图时就退回基础贴图 ✓）。
 * 挂了「拵」特性时**不**做这套替换 ✓（那种刀要用它自己的模型/贴图 ✓ —— 与 TiCEX 一致 ✓）。
 *
 * <h3>⚠ 与 TiCEX 的差异（如实记录 ✓）</h3>
 * <ol>
 *   <li><b>没有搬 shader 分支</b> ✗：TiCEX 还有一整套"按材料上自定义 shader"的体系
 *       （{@code ShaderProvider}／{@code ToolShaderMap}／{@code TicEXRenders}／{@code DecoratedRenderType}／
 *       {@code TicEXConfig.USE_SHADER} ✗）—— 那是**它整个渲染体系的通用件** ✓ 不属于拔刀剑专属内容 ✓
 *       ⇒ 本轮按用户口径（"贴图只搬基础"）**只保留"按材料换贴图 + 换颜色"这一支** ✓；
 *       shader 那一支在 TiCEX 里也要配置打开才走 ✓ 默认关闭 ✓ ⇒ 默认行为与原文一致 ✓。</li>
 *   <li><b>多了兜底 try/catch</b> ✓：渲染线程里任何异常都**回落成"原样画一遍"** ✓ ——
 *       宁可少一层材料外观，也不能让客户端崩 ✗（本仓一贯口径 ✓）。</li>
 * </ol>
 */
public class TicEXSBRenderers {

    @OnlyIn(Dist.CLIENT)
    public static void renderWrapped(IBladeRenderer renderer, ItemStack stack, WavefrontObject model, String target,
                                     ResourceLocation texture, PoseStack matrixStackIn, MultiBufferSource bufferIn,
                                     int packedLightIn, Function<ResourceLocation, RenderType> renderTypeGetter,
                                     boolean enableEffect) {
        ItemRenderContext itemRenderContext = KatanaContexts.SB_RENDERING_CONTEXT.get();

        if (itemRenderContext == null || !(stack.getItem() instanceof IModifiable)) {
            renderer.render(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, renderTypeGetter, enableEffect);
            return;
        }

        try {
            ToolStack tool = ToolStack.from(stack);

            // 「拵」= 用刀自己的模型/贴图 ⇒ 不做按材料的替换（与 TiCEX 一致 ✓）
            if (Modifiers.KOSHIRAE != null && tool.getModifierLevel(Modifiers.KOSHIRAE.getId()) > 0) {
                renderer.render(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, renderTypeGetter, enableEffect);
                return;
            }

            MaterialNBT materials = tool.getMaterials();
            for (int i = 0; i < materials.size(); i++) {
                MaterialVariant material = materials.get(i);
                SBToolRenderType.PartType partType = SBToolRenderType.PartType.byIndex(i);
                if (partType == null) continue;

                Mutable<Color> color = new MutableObject<>(null);

                ResourceLocation bladeTexture = partType.tryTexture(material.getVariant(), () -> {
                    Optional<MaterialRenderInfo> optional = MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material.getVariant());
                    optional.ifPresent(materialRenderInfo -> color.setValue(new Color(materialRenderInfo.vertexColor())));
                });

                RenderType renderType = renderTypeGetter.apply(bladeTexture);
                Function<ResourceLocation, RenderType> paintedRenderTypeGetter = loc -> renderType;

                try (ContextFrame<Color> frame = KatanaContexts.SB_COLOR_OVERRIDE.open(color.getValue())) {
                    renderer.render(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, paintedRenderTypeGetter, enableEffect);
                }
            }
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.debug("[拔刀剑] 按材料渲染失败，回落为原样渲染：{}", t.toString());
            renderer.render(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn, renderTypeGetter, enableEffect);
        }
    }
}
