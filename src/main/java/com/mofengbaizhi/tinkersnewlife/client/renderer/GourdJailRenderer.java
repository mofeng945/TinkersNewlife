package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.block.GourdJailVisualBlock;
import com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 狱门疆实体渲染器：渲染"狱门疆视觉方块"的方块模型（cube_all，贴图可替换，
 * 用户绘制 {@code textures/block/gourd_jail_idle.png} / {@code gourd_jail_sealed.png} 即可换肤）。
 * <ul>
 *   <li>空闲形态：sealed=false → 暗紫贴图</li>
 *   <li>已封印：sealed=true → 金褐贴图</li>
 *   <li>封印动画中：模型随时间收缩（配合目标处收束粒子），结束定格已封印形态</li>
 *   <li>实体边长 0.25（模型 1×1×1 缩放至四分之一，底立在脚下、水平居中）</li>
 * </ul>
 */
public class GourdJailRenderer extends EntityRenderer<GourdJailEntity> {

    /** 实体边长（模型缩放目标） */
    private static final float EDGE = 0.25F;

    public GourdJailRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GourdJailEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BlockState state = ModBlocks.GOURD_JAIL_VISUAL.get().defaultBlockState()
                .setValue(GourdJailVisualBlock.SEALED, entity.isSealed());

        // 封印动画进度：额外收缩（客户端 anim 由 entityData 同步）
        float animScale = 1.0F;
        int anim = entity.getAnim();
        if (!entity.isSealed() && anim > 0 && anim < GourdJailEntity.SEAL_TICKS) {
            float prog = Math.min(1.0F, anim / (float) GourdJailEntity.SEAL_TICKS);
            // 前 2/3 保持完整，后 1/3 快速收缩至消失
            if (prog > 2.0F / 3.0F) {
                animScale = Math.max(0.05F, 1.0F - (prog - 2.0F / 3.0F) * 3.0F);
            }
        }
        float s = EDGE * animScale;

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        poseStack.pushPose();
        // 实体位置为脚底中心：把 1×1×1 模型缩放至 s×s×s 并水平居中（底 y=0..s）
        poseStack.translate(-s / 2.0D, 0.0D, -s / 2.0D);
        poseStack.scale(s, s, s);
        dispatcher.renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(GourdJailEntity entity) {
        return null;
    }
}
