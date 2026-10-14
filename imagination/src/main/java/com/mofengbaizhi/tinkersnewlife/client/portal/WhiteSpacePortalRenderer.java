package com.mofengbaizhi.tinkersnewlife.client.portal;

import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlock;
import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.joml.Matrix4f;

/**
 * TNL999 \u2605\u2605 \u767d\u8272\u7a7a\u95f4\u4f20\u9001\u95e8\uff1a\u2605\u7b2c \u2463 \u6b65\uff0c\u2605\u628a"\u5bf9\u9762\u4e16\u754c"\u753b\u5728\u95e8\u9762\u4e0a \u2713
 *
 * <p>\u2605\u95e8\u662f**\u4e24\u683c\u9ad8\u7684\u4e00\u7247\u5e73\u9762**\uff08\u2605``HALF`` \u002b \u2605``FACING`` \u2713\uff09\u21d2
 * \u2605\u6bcf\u4e00\u534a\u5404\u753b"\u81ea\u5df1\u90a3\u534a\u5f20"\u56fe \u2713\uff08\u2605v \u53d6 0~0.5 / 0.5~1 \u2713\uff09\u3002
 */
public class WhiteSpacePortalRenderer implements BlockEntityRenderer<WhiteSpacePortalBlockEntity> {

    public WhiteSpacePortalRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(WhiteSpacePortalBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!PortalViewBridge.valid()) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof WhiteSpacePortalBlock)) {
            return;
        }
        Direction facing = state.getValue(WhiteSpacePortalBlock.FACING);
        boolean upper = state.getValue(WhiteSpacePortalBlock.HALF) == DoubleBlockHalf.UPPER;
        float y0 = 0.0F;
        float y1 = 1.0F;
        float v0 = upper ? 0.5F : 0.0F;
        float v1 = upper ? 1.0F : 0.5F;
        // \u2605\u95e8\u9762\u5e73\u9762\uff1a\u2605\u6cd5\u7ebf\u662f facing \u2713 \u21d2 \u2605\u6cbf\u5b83\u7684\u5782\u76f4\u65b9\u5411\u94fa\u5f00 \u2713
        float nx = facing.getStepX() * 0.499F;
        float nz = facing.getStepZ() * 0.499F;
        float ax = facing.getStepZ();
        float az = facing.getStepX();
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(PortalViewRenderType.get());
        quad(vc, m, ax, az, nx, nz, y0, y1, v0, v1);
        if (System.currentTimeMillis() - last > 4000L) {
            last = System.currentTimeMillis();
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL999 drew portal view quad at {} facing={} upper={} tex={}",
                    be.getBlockPos(), facing, upper, PortalViewBridge.textureId());
        }
    }

    private static long last;

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float az, float nx, float nz,
                             float y0, float y1, float v0, float v1) {
        float x0 = 0.5F - ax * 0.5F;
        float z0 = 0.5F - az * 0.5F;
        float x1 = 0.5F + ax * 0.5F;
        float z1 = 0.5F + az * 0.5F;
        vc.vertex(m, x0 + nx, y0, z0 + nz).color(255, 255, 255, 255).uv(0.0F, v1)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x1 + nx, y0, z1 + nz).color(255, 255, 255, 255).uv(1.0F, v1)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x1 + nx, y1, z1 + nz).color(255, 255, 255, 255).uv(1.0F, v0)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x0 + nx, y1, z0 + nz).color(255, 255, 255, 255).uv(0.0F, v0)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(0.0F, 0.0F, 1.0F).endVertex();
    }
}
