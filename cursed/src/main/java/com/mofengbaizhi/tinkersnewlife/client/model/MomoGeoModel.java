package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
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

    /**
     * TNL1414 <b>头部跟随视线</b> ✗
     *
     * <p>⚠ 为什么原来"只会朝正前方" ✗：
     * GeckoLib 的 `GeoEntityRenderer` 只把 `netHeadYaw` / `headPitch` 放进 `AnimationState`
     * 的 `EntityModelData` 里 ✓ **并不会**自动旋转骨骼 ✗
     * ⇒ ⭐模型不自己用的话，`head` 骨就永远保持动画里的"正前方" ✓
     *
     * <p>⭐ 做法（与玩家/原版两足模型同一套公式 ✓）：
     * ⭐`head.rotY += wrapDegrees(插值后的 yHeadRot − yBodyRot)`（★限制在 `getMaxHeadYRot` 内 ✓）
     * ⭐`head.rotX += 插值后的 xRot`（★限制在 `getMaxHeadXRot` 内 ✓）
     * `hat` 是 `head` 的子骨 ⇒ 自动跟着转 ✓。
     */
    @Override
    public void setCustomAnimations(KtuluMomo animatable, long instanceId,
                                    AnimationState<KtuluMomo> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone head = this.getAnimationProcessor().getBone("head");
        if (head == null) {
            return;
        }
        float pt = animationState.getPartialTick();
        // 头部相对身体的目标偏转（度）
        float headYaw = Mth.wrapDegrees(
                Mth.lerp(pt, animatable.yHeadRotO, animatable.yHeadRot)
                        - Mth.lerp(pt, animatable.yBodyRotO, animatable.yBodyRot));
        float headPitch = Mth.lerp(pt, animatable.xRotO, animatable.getXRot());
        // 脖子限制（与 Mob 默认一致：左右 75°、上下 40°）
        headYaw = Mth.clamp(headYaw, -animatable.getMaxHeadYRot(), animatable.getMaxHeadYRot());
        headPitch = Mth.clamp(headPitch, -animatable.getMaxHeadXRot(), animatable.getMaxHeadXRot());
        final float deg = (float) Math.PI / 180.0F;
        // Absolute pose: GeckoLib can retain last frame's rotation when no animation
        // writes this bone. Adding to getRot*() accumulates a new turn every frame.
        head.setRotY(head.getInitialSnapshot().getRotY() + headYaw * deg);
        head.setRotX(head.getInitialSnapshot().getRotX() + headPitch * deg);
    }

    @Override
    public RenderType getRenderType(KtuluMomo animatable, ResourceLocation texture) {
        return GHOST_PASS
                ? RenderType.entityTranslucent(texture)
                : super.getRenderType(animatable, texture);
    }
}
