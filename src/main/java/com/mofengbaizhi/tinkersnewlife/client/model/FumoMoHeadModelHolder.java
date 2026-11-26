package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoBlockEntityRenderer;
import net.minecraft.client.model.PlayerModel;

/**
 * <b>头顶用的玩偶模型</b>（§901 改法 —— 用户点出来的正确做法 ✓）：
 * <b>不再手搓第二份模型</b> ✗，而是直接复用方块/物品栏那只 ——
 * 建法用 {@link FumoMoBlockEntityRenderer#buildDollModel()} ✓、
 * 姿势用 {@link FumoMoBlockEntityRenderer#poseDoll} ✓
 * ⇒ "玩偶长什么样"全仓**只有一处定义** ✓（§900 手搓那版就漏了胳膊、身子还被头壳包住 ✗）。
 */
public final class FumoMoHeadModelHolder {

    private static PlayerModel<?> MODEL;

    private FumoMoHeadModelHolder() {}

    public static PlayerModel<?> get() {
        if (MODEL == null) {
            MODEL = FumoMoBlockEntityRenderer.buildDollModel();
            // ★★ §898 真根因（已核对 1.20.1 源码 ✓）：EntityModel.young **默认就是 true**
            //    ⇒ AgeableListModel.renderToBuffer 会走"幼年体"分支：
            //       scale(1.5/2 = 0.75) ＋ translate(0, 16/16 = 1.0, 0)（= 下移 16px）
            //    ⇒ 玩偶被缩小并下移 16px，**直接落进躯干内部** ⇒ 实机"什么都看不到" ✗。
            //    玩家模型/盔甲模型是渲染器每帧给它们赋 young 的 ✓；我们自己 new 出来的这份没人赋 ✗
            //    ⇒ 必须显式关掉 ✓。
            //    ⚠ 这里用的是**独立实例**（不是 BER 那一个）⇒ 不会反过来影响方块/物品栏那只 ✓。
            MODEL.young = false;
        }
        return MODEL;
    }
}
