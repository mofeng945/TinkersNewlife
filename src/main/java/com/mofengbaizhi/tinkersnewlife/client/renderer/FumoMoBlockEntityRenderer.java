package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * <b>fufu 玩偶的渲染器</b>（§880 用户口径 ✓）：直接用**玩家模型**渲染 ——
 * 好处是<b>六个面的 UV 天然是标准玩家皮肤布局</b> ✓ 不可能像我手写方块那样错位 ✗。
 * <ul>
 *   <li><b>幼年体比例</b>：整体缩到 <b>0.5</b> ✓（玩家模型 2 格高 ⇒ 玩偶 1 格高 ✓）；</li>
 *   <li><b>双腿前伸坐姿</b>：两条腿的 {@code xRot} 转到 ≈ −1.5 rad（≈86°）✓ 手臂微微前垂 ✓；</li>
 *   <li>贴图就是她的 64×64 模型皮肤 {@code textures/entity/momo_common.png} ✓。</li>
 * </ul>
 * <p>⚠ 这是一个**静态姿势**：不调 {@code setupAnim}（不依赖真实实体 ✓）✓ 只摆部件角度 ✓。
 */
public class FumoMoBlockEntityRenderer implements BlockEntityRenderer<FumoMoBlockEntity> {

    /** 她的模型皮肤（64×64 ✓ 也是方块贴图那一张 ✓） */
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/entity/momo_common.png");

    private final PlayerModel<?> model;

    public FumoMoBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);   // false = 非细手臂 ✓
    }

    @Override
    public void render(FumoMoBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));      // 面朝玩家（方块正面 = -Z ✓）
        renderDoll(model, pose, buffer, light, overlay);
        pose.popPose();
    }

    /**
     * <b>玩偶姿势的唯一实现</b>（§881 用户口径：「物品栏显示和方块显示统一」✓）——
     * 方块渲染器与<b>物品栏渲染器</b>都调这一个方法 ✓ ⇒ 两边长得一模一样 ✓，
     * 以后调姿势只改这里一处 ✓。
     */
    public static void renderDoll(PlayerModel<?> model, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        pose.pushPose();
        // ① 幼年体比例 ✓
        pose.scale(0.5F, 0.5F, 0.5F);
        // ② ⚠ 实体模型的**标准翻转**（§882 修的 bug）：MC 的实体模型是 **Y 轴向下**的（root 在 y=24＝脚下 ✓），
        //    原版 LivingEntityRenderer 靠 `scale(-1,-1,1) + translate(0,-1.501,0)` 把它翻成"站在地面上" ✓
        //    —— 我 §880 漏了这一步 ⇒ 玩偶是**倒着**的 ✗（用户实测 ✓）。
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0D, -1.501D, 0.0D);
        // ③ 坐姿微调（注意：这一步之后 y 仍是"模型空间"的向下 ✓ 减 y = 抬高 ✓）
        pose.translate(0.0D, -0.06D, 0.0D);

        // ④ 双腿前伸（坐在地上 ✓）
        model.rightLeg.xRot = -1.5F;
        model.leftLeg.xRot = -1.5F;
        model.rightLeg.yRot = 0.06F;
        model.leftLeg.yRot = -0.06F;
        // ⑤ ⚠ 腿部**外层**（裤子）是**独立部件**，不跟着腿转 ✗ ⇒ 必须手动同步 ✓（§882 用户实测 ✓）
        syncLeg(model.rightLeg, model.rightPants);
        syncLeg(model.leftLeg, model.leftPants);
        // ⑥ 手自然垂在前侧 ✓（袖子若是独立部件也一并同步 ✓）
        model.rightArm.xRot = 0.18F;
        model.leftArm.xRot = 0.18F;
        model.rightArm.zRot = 0.08F;
        model.leftArm.zRot = -0.08F;
        syncArm(model.rightArm, model.rightSleeve);
        syncArm(model.leftArm, model.leftSleeve);
        // ⑦ 头略微抬起（看着你 ✓）；帽子是头的子部件 ⇒ 自动跟随 ✓
        model.head.xRot = -0.12F;

        model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    /** 把腿的角度同步给"裤子"外层 ✓（外层是独立部件 ✓ 不同步就会留在原位 ✗） */
    private static void syncLeg(net.minecraft.client.model.geom.ModelPart leg, net.minecraft.client.model.geom.ModelPart pants) {
        try {
            pants.xRot = leg.xRot;
            pants.yRot = leg.yRot;
            pants.zRot = leg.zRot;
        } catch (Throwable ignored) {
        }
    }

    /** 把手臂的角度同步给"袖子"外层 ✓ */
    private static void syncArm(net.minecraft.client.model.geom.ModelPart arm, net.minecraft.client.model.geom.ModelPart sleeve) {
        try {
            sleeve.xRot = arm.xRot;
            sleeve.yRot = arm.yRot;
            sleeve.zRot = arm.zRot;
        } catch (Throwable ignored) {
        }
    }

    /** 给物品栏渲染器用：自己按需烘焙一个玩家模型 ✓（只在客户端 ✓） */
    public static PlayerModel<?> newModel() {
        return new PlayerModel<>(net.minecraft.client.Minecraft.getInstance().getEntityModels()
                .bakeLayer(ModelLayers.PLAYER), false);
    }
}
