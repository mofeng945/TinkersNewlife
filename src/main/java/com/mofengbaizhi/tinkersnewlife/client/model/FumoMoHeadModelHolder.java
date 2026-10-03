package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

/** 懒烘焙并缓存"头上的 fufu"模型（§891），顺手把整体缩放到玩偶大小 ✓ */
public final class FumoMoHeadModelHolder {

    private static FumoMoHeadModel MODEL;

    private FumoMoHeadModelHolder() {}

    public static FumoMoHeadModel get() {
        if (MODEL == null) {
            MODEL = new FumoMoHeadModel(FumoMoHeadModel.create()
                    .bakeRoot());
        }
        return MODEL;
    }

    /** 供 curios 之类外部渲染器复用的入口（把玩偶画在当前 PoseStack 的头位置 ✓） */
    public static void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffer,
                              int light, int overlay, net.minecraft.client.model.HumanoidModel<?> playerModel) {
        FumoMoHeadModel model = get();
        // 把 fufu 的角度与玩家的头同步 ✓（跟着头转 ✓）
        model.head.xRot = playerModel.head.xRot;
        model.head.yRot = playerModel.head.yRot;
        model.head.zRot = playerModel.head.zRot;
        model.renderToBuffer(pose, buffer.getBuffer(net.minecraft.client.renderer.RenderType
                        .entityCutoutNoCull(com.mofengbaizhi.tinkersnewlife.client.renderer
                                .FumoMoBlockEntityRenderer.TEXTURE)),
                light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
