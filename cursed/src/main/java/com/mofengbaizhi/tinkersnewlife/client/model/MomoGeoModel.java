package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * TNL1293 墨默的 GeckoLib 模型：**全部读文件**
 *
 * <ul>
 *   <li>模型：{@code assets/tinkersnewlife/geo/ktulu_momo.geo.json} ✓</li>
 *   <li>动画：{@code assets/tinkersnewlife/animations/ktulu_momo.animation.json} ✓</li>
 *   <li>贴图：{@code assets/tinkersnewlife/textures/entity/ktulu_momo.png} ✓</li>
 * </ul>
 *
 * <p>⚠ 也就是说：**美术在 BlockBench（装 GeckoLib 插件）里改模型/写动画，
 * 导出这两个 json 覆盖掉即可**，不需要再动 Java ✓
 * <p>骨架名必须和代码一致：`body` / `head` / `hat` / `rightArm` / `leftArm` / `rightLeg` / `leftLeg`
 * （`hat` 在 geo 里是 `head` 的子骨）。
 */
public class MomoGeoModel extends GeoModel<KtuluMomo> {

    private static final ResourceLocation MODEL =
            new ResourceLocation("tinkersnewlife", "geo/ktulu_momo.geo.json");
    private static final ResourceLocation ANIM =
            new ResourceLocation("tinkersnewlife", "animations/ktulu_momo.animation.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/ktulu_momo.png");

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
}
