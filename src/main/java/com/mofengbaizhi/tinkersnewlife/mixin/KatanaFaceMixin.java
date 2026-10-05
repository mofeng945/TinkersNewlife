package com.mofengbaizhi.tinkersnewlife.mixin;

// 移植自 TiCEX (MIT): moffy.ticex.mixin.slashblade.FaceMixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.KatanaContexts;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.Color;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 刀身 OBJ 的"逐顶点"改写 —— 移植 TiCEX {@code FaceMixin}（MIT）。
 *
 * <ul>
 *   <li>{@code uv(u, v)} ⇒ 若上下文里有"花纹图集精灵"就把 UV 映射到它上面 ✓；</li>
 *   <li>{@code color(r,g,b,a)} ⇒ 若上下文里有"颜色覆盖"就用它替换 RGB（透明度 a 不动 ✓）。</li>
 * </ul>
 * <p>⇒ 这两处配合 {@code TicEXSBRenderers} 的"按材料换色" ✓。
 * 上下文为空时**恒走原方法** ✓（与原版行为完全一致 ✓）。
 *
 * <p>⚠ 已核对字节码：{@code Face#putVertex(VertexConsumer,int,Matrix4f,float,float,float,int,int)} 存在 ✓
 * （`javap -p` 实查 ✓ 它是包级私有方法，但 mixin 是以目标类身份注入 ⇒ 没问题 ✓）。
 *
 * <p>⚠ <b>与原版的差异（本仓口径，必要 ✓）</b>：两个 {@code @At} 目标是**原版方法**
 * （{@code VertexConsumer#uv}／{@code #color} ✗）—— 原文写 MCP 名 ＋ {@code remap = true}
 * （靠 Mixin 注解处理器的 refmap ✗），而本仓禁用了那个处理器、refmap 是手写的且没有这两条 ✗
 * ⇒ 照本仓既有写法改成 <b>SRG 名 ＋ {@code remap = false}</b> ✓
 * （`m_7421_` = uv ✓ `m_6122_` = color ✓，取自本仓 {@code build/reobfJar/mappings.tsrg} 实查 ✓）。
 */
@Mixin(value = Face.class, remap = false)
public class KatanaFaceMixin {

    @WrapOperation(method = "putVertex", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;m_7421_(FF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false))
    public VertexConsumer modifyUV(VertexConsumer instance, float u, float v, Operation<VertexConsumer> original) {
        TextureAtlasSprite sprite = KatanaContexts.SB_FACE_SPRITE.get();
        if (sprite == null) {
            return original.call(instance, u, v);
        }

        return original.call(
                instance,
                sprite.getU0() + u * (sprite.getU1() - sprite.getU0()),
                sprite.getV0() + v * (sprite.getV1() - sprite.getV0())
        );
    }

    @WrapOperation(method = "putVertex", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;m_6122_(IIII)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = false))
    public VertexConsumer modifyColor(VertexConsumer instance, int r, int g, int b, int a, Operation<VertexConsumer> original) {
        Color color = KatanaContexts.SB_COLOR_OVERRIDE.get();
        if (color == null) {
            return original.call(instance, r, g, b, a);
        }

        return original.call(instance, color.getRed(), color.getGreen(), color.getBlue(), a);
    }
}
