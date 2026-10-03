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
            // ★★ §898 真根因（已核对 1.20.1 源码 ✓）：EntityModel.young **默认就是 true**
            //    ⇒ AgeableListModel.renderToBuffer 会走"幼年体"分支：
            //       scale(1.5/2 = 0.75) ＋ translate(0, 16/16 = 1.0, 0)（= 下移 16px）
            //    ⇒ 挂在 head 下的玩偶被缩小并下移 16px，**直接落进躯干内部** ⇒ 实机"什么都看不到" ✗。
            //    玩家模型/盔甲模型是渲染器每帧给它们赋 young 的 ✓；我们自己 new 出来的这份没人赋 ✗
            //    ⇒ 必须在这里显式关掉 ✓（curios 那条路和盔甲模型那条路共用这一个实例 ✓ 一处修两处好 ✓）。
            MODEL.young = false;
        }
        return MODEL;
    }

    /** 供 curios 之类外部渲染器复用的入口（把玩偶画在当前 PoseStack 的头位置 ✓） */
    public static void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffer,
                              int light, int overlay, net.minecraft.client.model.HumanoidModel<?> playerModel) {
        FumoMoHeadModel model = get();
        model.young = false; // §898 保险：任何路径渲染前都确认一次（幼年体分支会把玩偶塞进身体里 ✗）
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
