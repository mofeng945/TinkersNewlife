package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * TNL1293/TNL1297 墨默的 GeckoLib 模型：**全部读文件**
 *
 * <ul>
 *   <li>模型：{@code assets/tinkersnewlife/geo/ktulu_momo.geo.json}</li>
 *   <li>动画：{@code assets/tinkersnewlife/animations/ktulu_momo.animation.json}</li>
 *   <li>贴图：{@code assets/tinkersnewlife/textures/entity/ktulu_momo.png}</li>
 * </ul>
 *
 * <p>⚠ 美术在 BlockBench（装 GeckoLib 插件）里改模型/写动画，导出这两个 json 覆盖即可。
 * <p>骨架名必须和代码一致：`body` / `head` / `hat`（head 子骨）/ `rightArm` / `leftArm` /
 * `rightLeg` / `leftLeg`。
 */
public class MomoGeoModel extends GeoModel<KtuluMomo> {

    private static final ResourceLocation MODEL =
            new ResourceLocation("tinkersnewlife", "geo/ktulu_momo.geo.json");
    private static final ResourceLocation ANIM =
            new ResourceLocation("tinkersnewlife", "animations/ktulu_momo.animation.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/ktulu_momo.png");

    /**
     * TNL1297 冲刺残影用：为 true 时把渲染类型换成**半透明**的 ✗
     * 不换的话 `entityCutoutNoCull` **不做混合** ✗ ⭐`setShaderColor` 的 alpha 根本无效 ✓
     */
    public static boolean GHOST_PASS = false;

    @Override
    public ResourceLocation getModelResource(KtuluMomo animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(KtuluMomo animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(KtuluMomo animatable) {
        return ANIM;
    }

    @Override
    public RenderType getRenderType(KtuluMomo animatable, ResourceLocation texture) {
        return GHOST_PASS
                ? RenderType.entityTranslucent(texture)
                : super.getRenderType(animatable, texture);
    }
}
