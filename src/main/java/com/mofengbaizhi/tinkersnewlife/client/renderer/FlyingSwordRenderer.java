package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public class FlyingSwordRenderer extends EntityRenderer<FlyingSwordEntity> {

    private static final Vector3f DEFAULT_FORWARD = new Vector3f(0.707f, 0.707f, 0f);

    public FlyingSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FlyingSwordEntity entity, float entityYaw, float partialTicks,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        var stack = entity.getItemStack();
        if (stack.isEmpty()) return;

        poseStack.pushPose();

        Vector3f targetDir = null;

        if (entity.isChaseMode()) {
            // 追击模式：指向目标（实时更新）
            LivingEntity target = getTarget(entity);
            if (target != null && target.isAlive()) {
                Vec3 toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0)
                        .subtract(entity.position());
                if (toTarget.lengthSqr() > 0.0001) {
                    targetDir = new Vector3f((float) toTarget.x, (float) toTarget.y, (float) toTarget.z).normalize();
                }
            }
        } else {
            // ✅ 普通模式：使用发射时存储的固定方向
            Vec3 launchDir = entity.getLaunchDirection();
            if (launchDir != null && launchDir.lengthSqr() > 0.0001) {
                targetDir = new Vector3f((float) launchDir.x, (float) launchDir.y, (float) launchDir.z).normalize();
            }
        }

        // 如果仍然没有方向，使用运动方向或默认
        if (targetDir == null) {
            Vec3 motion = entity.getDeltaMovement();
            if (motion.lengthSqr() > 0.0001) {
                targetDir = new Vector3f((float) motion.x, (float) motion.y, (float) motion.z).normalize();
            } else {
                targetDir = new Vector3f(0, 0, 1);
            }
        }

        Quaternionf quat = new Quaternionf().rotateTo(DEFAULT_FORWARD, targetDir);
        poseStack.mulPose(quat);

        poseStack.scale(0.4f, 0.4f, 1.5f);

        var itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, 0,
                poseStack, buffer, entity.level(), 0);

        poseStack.popPose();
    }

    private LivingEntity getTarget(FlyingSwordEntity entity) {
        String uuidStr = entity.getTargetUUID();
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            UUID uuid = UUID.fromString(uuidStr);
            AABB searchBox = entity.getBoundingBox().inflate(64);
            for (LivingEntity living : entity.level().getEntitiesOfClass(LivingEntity.class, searchBox)) {
                if (living.getUUID().equals(uuid) && living.isAlive()) {
                    return living;
                }
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    @Override
    public ResourceLocation getTextureLocation(FlyingSwordEntity entity) {
        return null;
    }
}