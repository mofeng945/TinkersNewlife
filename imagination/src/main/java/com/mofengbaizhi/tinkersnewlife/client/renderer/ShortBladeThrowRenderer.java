package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.ShortBladeThrowEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * ⭐ §1164 <b>短刀投掷的渲染器</b>（用户口径 ✓ 2026-10-09：
 * 「**看我追踪飞剑的角度是怎么写的，仿照那个写短刀发射出去的角度**」✓）。
 *
 * <h2>⭐ 照搬的是哪一段（⭐ `FlyingSwordRenderer` ✓ 一字未改其骨架 ✓）</h2>
 * <pre>
 *   Quaternionf quat = new Quaternionf().rotateTo(DEFAULT_FORWARD, targetDir);
 *   poseStack.mulPose(quat);
 * </pre>
 * ⭐ 意思：⭐ 模型在**自己本地坐标系**里"刀尖指向"某个固定轴 ✗ ⇒
 * ⭐ 用 `rotateTo` 把它**旋到速度方向上** ✓ ⇒ ⭐ 刀就顺着飞行方向躺着飞 ✓。
 * ⭐ `FlyingSwordRenderer` 里那个轴是 ⭐ `(0.707f, 0.707f, 0f)` ✗
 * —— ⭐ 因为它的贴图里刀身就是 ⭐ **45° 斜在 XY 平面** ✓ ⇒ ⭐ 沿"左上→右下" ✓。
 * ⭐ 长短刃的短刀贴图 ⭐ **同样是 45° 斜在 XY 平面** ✓ ⇒ ⭐ **同一个轴** ✓ ✓。
 *
 * <h2>⚠ 为什么必须自己写渲染器（⭐ 不能再用 `ThrownItemRenderer` ✗）</h2>
 * ⚠ 原版 `ThrownItemRenderer` 会把投掷物 ⭐ **画成一个"面朝摄像机"的平面** ✗
 * ⇒ ⭐ 就是用户说的「**横向面对着我飞的，很怪**」✓ —— ⭐ 它**不跟随速度方向** ✗ ✓
 * （⚠ 我上一版试图用 ⭐ `ground` 里加 90° 旋转 来蒙过去 ✗ ⭐ 那是取巧且会连带掉落物 ✓ 已弃用 ✓）。
 *
 * <h2>⚠ 为什么用 {@code ItemDisplayContext.NONE} ✗</h2>
 * ⭐ `NONE` ＝ ⭐ **不套用任何 display 变换** ✓ ⇒ ⭐ 姿势完全由本渲染器掌控 ✓
 * （⭐ 那个 90° 之类的 `ground` 设置对本渲染器**不再有影响** ✓）。
 */
public class ShortBladeThrowRenderer extends EntityRenderer<ShortBladeThrowEntity> {

    /**
     * ⭐ 模型"刀尖"在本地坐标系里的方向 ✓ ——
     * ⚠ 与 {@code FlyingSwordRenderer.DEFAULT_FORWARD} **同值** ✗：⭐ 两边的贴图都是 45° 斜在 XY 平面 ✓
     * （⭐ 若以后短刀贴图改了朝向 ⇒ ⭐ 改这一个向量即可 ✓）。
     */
    private static final Vector3f BLADE_FORWARD = new Vector3f(0.707f, 0.707f, 0f);

    /** ⭐ 缩放（⭐ 让短刀在飞行时大小正常 ✓ 照飞剑的 `0.4, 0.4, 1.5` 口径略调 ✓） */
    private static final float SCALE_X = 0.55f;
    private static final float SCALE_Y = 0.55f;
    private static final float SCALE_Z = 0.55f;

    public ShortBladeThrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ShortBladeThrowEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack stack = entity.getItem();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        try {
            poseStack.pushPose();

            // ⭐ 方向＝速度方向 ✓（⭐ 拿不到速度就朝 +Z ✓ 与飞剑同一兜底 ✓）
            Vector3f targetDir = null;
            Vec3 motion = entity.getDeltaMovement();
            if (motion != null && motion.lengthSqr() > 1.0E-4D) {
                targetDir = new Vector3f((float) motion.x, (float) motion.y, (float) motion.z).normalize();
            }
            if (targetDir == null) {
                targetDir = new Vector3f(0f, 0f, 1f);
            }

            // ⭐⭐ **核心一行**（⭐ 照搬飞剑 ✓）：把"刀尖轴"旋到速度方向 ✓
            poseStack.mulPose(new Quaternionf().rotateTo(BLADE_FORWARD, targetDir));
            poseStack.scale(SCALE_X, SCALE_Y, SCALE_Z);

            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack, ItemDisplayContext.NONE, packedLight, 0,
                    poseStack, buffer, entity.level(), 0);

            poseStack.popPose();
        } catch (Throwable ignored) {
            // ⭐ 渲染出错绝不能崩 ✗
        }
    }

    /** ⭐ 本渲染器不用贴图 ✓（⭐ 画的是物品模型 ✓）—— ⚠ 照 {@code FlyingSwordRenderer} 返回 null ✓ 它已实证可用 ✓ */
    @Override
    public ResourceLocation getTextureLocation(ShortBladeThrowEntity entity) {
        return null;
    }
}
