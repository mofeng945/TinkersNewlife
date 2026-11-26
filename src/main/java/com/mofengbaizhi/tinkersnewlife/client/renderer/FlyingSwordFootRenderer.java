package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordFootEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class FlyingSwordFootRenderer extends EntityRenderer<FlyingSwordFootEntity> {

    // 模型默认前向：Y偏X 45°，即 (0.707, 0.707, 0)
    private static final Vector3f DEFAULT_FORWARD = new Vector3f(0.707f, 0.707f, 0f);

    public FlyingSwordFootRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FlyingSwordFootEntity entity, float entityYaw, float partialTicks,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        var stack = entity.getItemStack();
        if (stack.isEmpty()) return;

        UUID ownerId = entity.getOwnerUUID();
        if (ownerId == null) return;
        Player owner = entity.level().getPlayerByUUID(ownerId);
        if (owner == null) return;

        poseStack.pushPose();

        // --- 计算玩家水平方向向量（忽略俯仰） ---
        Vec3 lookAngle = owner.getLookAngle();
        Vector3f horizontalDir = new Vector3f((float) lookAngle.x, 0, (float) lookAngle.z);
        if (horizontalDir.lengthSquared() < 0.0001) {
            horizontalDir.set(0, 0, -1); // 备选朝北
        }
        horizontalDir.normalize();

        // --- 1. 修正模型倾斜：将 (0.707,0.707,0) 旋转到 (0,1,0) ---
        Quaternionf fixQuat = new Quaternionf().rotateTo(DEFAULT_FORWARD, new Vector3f(0, 1, 0));

        // --- 2. 平躺：将 (0,1,0) 旋转到 (0,0,1) 即绕X轴-90° ---
        Quaternionf layQuat = new Quaternionf().rotateTo(new Vector3f(0, 1, 0), new Vector3f(0, 0, 1));

        // --- 3. 水平旋转：将 (0,0,1) 旋转到 horizontalDir ---
        Quaternionf horizontalQuat = new Quaternionf().rotateTo(new Vector3f(0, 0, 1), horizontalDir);

        // --- 组合：先修正 → 再平躺 → 最后水平旋转 ---
        // 注意：组合顺序是 right-multiply，所以最终旋转 = horizontalQuat * layQuat * fixQuat
        Quaternionf finalQuat = new Quaternionf(horizontalQuat).mul(layQuat).mul(fixQuat);

        poseStack.mulPose(finalQuat);

        // 缩放
        poseStack.scale(1.2f, 1.2f, 1.2f);

        var itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, 0,
                poseStack, buffer, entity.level(), 0);

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(FlyingSwordFootEntity entity) {
        return null;
    }
}