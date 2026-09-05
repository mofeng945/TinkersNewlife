package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 通用 GUI 3D 实体展示类：
 * <p>
 * 各术式选择界面（咒灵操术 / 傀儡操术 / 无为转变 / 十影术式）共用同一种
 * 「背包式」实体渲染——脚底锚点、鼠标跟随轻微转向、绕 X 轴俯仰、渲染后复位姿态。
 * 原先每个界面各自复制一份 drawEntity，现收敛到本类的静态方法。
 */
public final class GuiEntityViewer {

    private GuiEntityViewer() {}

    /**
     * 在 GUI 中渲染一个实体。
     *
     * @param x      展示锚点的水平中心（UI 坐标）
     * @param feetY  实体脚底所在 y（UI 坐标，脚踩在这条线上）
     * @param size   渲染尺寸（像素/格，等价原版背包实体渲染的 scale）
     * @param mouseX 当前鼠标 x（绝对坐标，用于轻微转向）
     * @param mouseY 当前鼠标 y（绝对坐标）
     */
    public static void render(GuiGraphics graphics, LivingEntity entity, int x, int feetY, float size,
                              double mouseX, double mouseY) {
        float yawF = (float) Math.atan((mouseX - x) / 40.0);
        float pitchF = (float) Math.atan((mouseY - feetY) / 40.0);
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf rotPitch = new Quaternionf().rotateX(pitchF * 20.0F * ((float) Math.PI / 180F));
        rot.mul(rotPitch);

        float body = entity.yBodyRot;
        float yr = entity.getYRot();
        float xr = entity.getXRot();
        float hro = entity.yHeadRotO;
        float hr = entity.yHeadRot;
        entity.yBodyRot = 180.0F - yawF * 20.0F;
        entity.setYRot(180.0F - yawF * 40.0F);
        entity.setXRot(-pitchF * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, feetY, 50.0D);
        pose.mulPoseMatrix(new Matrix4f().scaling(size, size, -size));
        pose.mulPose(rot);
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotPitch.conjugate();
        dispatcher.overrideCameraOrientation(rotPitch);
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F,
                pose, graphics.bufferSource(), 15728880));
        graphics.flush();
        dispatcher.setRenderShadow(true);
        dispatcher.overrideCameraOrientation(null);
        pose.popPose();

        entity.yBodyRot = body;
        entity.setYRot(yr);
        entity.setXRot(xr);
        entity.yHeadRotO = hro;
        entity.yHeadRot = hr;
    }

    /**
     * 按实体身高推算合适尺寸：目标渲染高度约 targetPx（对 1 格高生物），
     * 但每格的像素数限制在 [minPxPerBlock, maxPxPerBlock] 内，避免过大/过小。
     */
    public static float fitScale(LivingEntity entity, float targetPx, float minPxPerBlock, float maxPxPerBlock) {
        float bb = Math.max(0.5F, entity.getBbHeight());
        return clamp(targetPx / bb, minPxPerBlock, maxPxPerBlock);
    }

    /** 创建仅供界面展示的客户端实体（不加入世界）；类型为空/创建失败返回 null */
    public static LivingEntity createDummy(EntityType<?> type) {
        if (type == null || Minecraft.getInstance().level == null) return null;
        try {
            if (type.create(Minecraft.getInstance().level) instanceof LivingEntity living) {
                return living;
            }
        } catch (Throwable ignored) {
            // 某些实体类型在纯客户端创建可能抛异常，退回无预览
        }
        return null;
    }

    /**
     * 创建展示实体并套用个体 NBT（剔除世界/坐标相关标签，仅保留外观类数据），
     * 套用失败则退回原样实体。NBT 可为 null。
     */
    public static LivingEntity createDummy(EntityType<?> type, CompoundTag nbt) {
        LivingEntity living = createDummy(type);
        if (living == null || nbt == null) return living;
        CompoundTag clean = nbt.copy();
        clean.remove("UUID");
        clean.remove("Pos");
        clean.remove("Dimension");
        clean.remove("Motion");
        clean.remove("WorldUUIDMost");
        clean.remove("WorldUUIDLeast");
        try {
            living.load(clean);
        } catch (Throwable ignored) {
            // 客户端仅展示，加载失败则退回原样
        }
        return living;
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
