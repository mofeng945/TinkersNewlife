package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoBlockEntity;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoDoll;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.core.Direction;
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
        this.model = buildDollModel();   // §887 自建网格（帽子层加厚 ✓）
    }

    /**
     * §887：<b>自己建玩家模型</b> —— 原版帽子层只外扩 0.25px（0.015 格），
     * 玩偶整体 0.5 倍缩放后只剩 ≈0.008 格 ⇒ **亚像素、看不见** ✗（这就是"外层不在"的真因 ✓）。
     * <p>做法：拿 {@code PlayerModel.createMesh(...)} 的网格 ⇒ 在**烘焙之前**
     * 用 {@code PartDefinition#addOrReplaceChild} 把 {@code hat} 换成**外扩 0.6px** 的版本 ✓
     * ⇒ 再自己 {@code bakeRoot()} ✓。UV 仍是标准帽子层（32,0 起 ✓）⇒ 不会错位 ✓。
     * <p>§901：改成 <b>public</b> —— 头顶那条路（原版头盔槽 / Curios）也用它建**同一只**玩偶 ✓
     * ⇒ "玩偶长什么样"从此只有一处定义 ✓（别再手搓第二份模型 ✗ 用户点出来的 ✓）。
     */
    public static PlayerModel<?> buildDollModel() {
        net.minecraft.client.model.geom.builders.MeshDefinition mesh =
                PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false);
        try {
            net.minecraft.client.model.geom.builders.PartDefinition head = mesh.getRoot().getChild("head");
            head.addOrReplaceChild("hat",
                    net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                            .texOffs(32, 0)
                            .addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8,
                                    new net.minecraft.client.model.geom.builders.CubeDeformation(0.6F)),
                    net.minecraft.client.model.geom.PartPose.ZERO);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/FumoMo")
                    .warn("[fufu] 帽子层加厚失败（用原版厚度继续）：{}", t.toString());
        }
        return new PlayerModel<>(net.minecraft.client.model.geom.builders.LayerDefinition
                .create(mesh, 64, 64).bakeRoot(), false);
    }

    /**
     * §902 <b>贴地补偿</b>：方块里的玩偶会**悬空**（用户实测「放在地上是飘起来的」✓），
     * 原因是两件事叠在一起：
     * <ol>
     *   <li><b>玩偶是坐姿</b>（两条腿前伸 ✗）⇒ 它的最低点不是"脚底 1.5 格"那处，
     *       而是**裤子外层的下缘**：腿箱局部 y∈[0,12]、z∈[−2.25,2.25]（外层 +0.25 膨胀 ✓）
     *       绕 xRot=−1.5 转过来之后 y′ ≈ 0.0707y + 0.997z ⇒ 最高 ≈ 3.09px
     *       ⇒ 最低点 ≈ (12+3.09)/16 ≈ <b>0.943 格</b>（腿本体因为 ×0.92 只到 0.913 格 ✓ 比裤子高 ✗）；</li>
     *   <li><b>幼年体分支</b>：{@code PlayerModel} 从没人给它赋 {@code young} ⇒ 用的是
     *       {@code EntityModel.young} 的默认值 <b>true</b>（已核对 1.20.1 源码 ✓）
     *       ⇒ 身体那些部件先被 {@code scale(1/2) + translate(0, 24/16 格, 0)} 抬了一截 ✓。</li>
     * </ol>
     * 两件事合起来 ⇒ 玩偶净悬空约 <b>0.17 格</b>（≈2.7px，肉眼看得出来 ✗）。
     * <p>修法：本方法所在的空间是**世界方块空间**（y 向上、单位=格 ✓，BER 没有翻转 ✓）
     * ⇒ 直接 `translate(0, -值, 0)` 就是"往下挪这么多格" ✓（放在 {@link #renderDoll} 之前 ⇒ 不受它 0.5 缩放影响 ✓）。
     * 取 <b>0.19 格</b>：比算出来的 0.17 略多一点点 ⇒ 留一丁点下沉（≈0.3px，看不出来 ✓），
     * 免得还留一条缝 ✗。想微调就改这一个数 ✓。
     */
    public static final double GROUND_SINK = 0.19D;

    /**
     * §907 <b>世界里的整体放大倍数</b>（用户口径：「维持这个比例，让方块模型放大一点**填满整个方块**」✓）。
     * <p>算一下实际大小：净缩放 = 我们自己那 0.5 × **幼年体分支**的 0.5（身体件）= **0.25**
     * （{@code PlayerModel} 没人赋 {@code young} ⇒ 默认 true ⇒ 走 {@code AgeableListModel} 的幼年体分支 ✓，
     * 已核对 1.20.1 源码 ✓；{@code PlayerModel} 还重写了 {@code bodyParts()} 把裤子/袖子/夹克也拉进那一支 ✓）
     * ⇒ 玩偶只有 **≈0.49 格高 × 0.20 格宽**（在一格方块里显得很小 ✗）。
     * <p>乘 <b>2.0</b> ⇒ ≈**0.98 格高 × 0.40 格宽** ⇒ 高度基本填满一个方块 ✓，比例完全不变 ✓。
     * <p>⚠ 这个倍数**只作用于世界（方块）那条路** ✓ —— 物品栏/手持有自己的 zoom（用户调过 ✓）✗ 不动它 ✓。
     * 想再大/再小就改这一个数 ✓（1.0 = §907 之前的大小 ✓）。
     */
    public static final float WORLD_SCALE = 2.0F;

    @Override
    public void render(FumoMoBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        // §906 按放置朝向转：玩家模型（以及这只玩偶）的**正面 = −Z** ✓
        //   ⇒ 要让正面指向 facing，yaw = 180 − facing.toYRot()
        //   （facing=south（toYRot=0）⇒ 仍然 180°，与 §880 起的写法一致 ✓ 不会突变 ✓）
        Direction facing = be.getBlockState().hasProperty(FumoMoDoll.FumoMoBlock.FACING)
                ? be.getBlockState().getValue(FumoMoDoll.FumoMoBlock.FACING)
                : Direction.SOUTH;
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        // §907 放大：放在 GROUND_SINK **之前** ⇒ 那个下沉量会被一起按比例放大 ✓
        //   （几何整体的悬空量也随之放大 ⇒ 下沉量同样放大 ⇒ 玩偶最低点仍然贴着方块底面 ✓ 正好 ✓）
        pose.scale(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE);
        pose.translate(0.0D, -GROUND_SINK, 0.0D);           // §902 坐到地面上 ✓（悬空 0.17 格 ⇒ 补 0.19 ✓）
        renderDoll(model, pose, buffer, light, overlay);
        pose.popPose();
    }

    /**
     * <b>玩偶"长什么样"的唯一实现</b>（§881 用户口径：「物品栏显示和方块显示统一」✓，§901 再扩到头顶 ✓）——
     * 方块渲染器、物品栏渲染器、<b>头顶（Curios ＋ 原版头盔槽）</b> 全都调这一个方法 ✓，
     * 以后调玩偶形象只改这里一处 ✓。
     * <p>⚠ 本方法只管<b>摆姿势</b>（头身比 / 坐姿 / 外层可见），<b>不管"摆在哪"</b> ——
     * 方块那条路的"摆在哪"在 {@link #renderDoll} 里（0.5 缩放 ＋ y 翻转 ＋ 落到方块上 ✓），
     * 头顶那条路的"摆在哪"在 {@link FumoMoHeadRender} 里（落到玩家头顶 ✓）。
     */
    public static void poseDoll(PlayerModel<?> model) {
        // ⓿ §883：把第二层（帽子/夹克/左右袖/左右裤）显式设为可见 + 不跳过绘制
        //    实测：她皮肤里帽子层有 178 个不透明像素（确实画了帽子），但游戏里看不到
        //    ⇒ 只能是这些「外层部件」被置成了不可见（原版有若干状态会藏帽子）
        //    ⇒ 这里每个外层部件都显式设一遍，幂等、无害。
        for (net.minecraft.client.model.geom.ModelPart part : new net.minecraft.client.model.geom.ModelPart[]{
                model.hat, model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants}) {
            part.visible = true;
            part.skipDraw = false;
        }

        // ① §884 头身比：照「玩偶」把**头放大**（娃娃感的关键 ✓）
        //    数值集中在这里，想调只管改这几个 ✓
        final float HEAD_SCALE = 1.40F;   // 头放大倍数（1.0 = 原版比例）
        final float LIMB_SCALE = 0.92F;   // 四肢略收细 ⇒ 显得头更大 ✓
        model.head.xScale = HEAD_SCALE;
        model.head.yScale = HEAD_SCALE;
        model.head.zScale = HEAD_SCALE;
        for (net.minecraft.client.model.geom.ModelPart limb : new net.minecraft.client.model.geom.ModelPart[]{
                model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            limb.xScale = LIMB_SCALE;
            limb.yScale = LIMB_SCALE;
            limb.zScale = LIMB_SCALE;
        }

        // ② 双腿前伸（坐姿 ✓）
        model.rightLeg.xRot = -1.5F;
        model.leftLeg.xRot = -1.5F;
        model.rightLeg.yRot = 0.06F;
        model.leftLeg.yRot = -0.06F;
        // ③ ⚠ 腿部**外层**（裤子）是**独立部件**，不跟着腿转 ✗ ⇒ 必须手动同步 ✓（§882 用户实测 ✓）
        syncLeg(model.rightLeg, model.rightPants);
        syncLeg(model.leftLeg, model.leftPants);
        // ④ 手自然垂在前侧 ✓（袖子若是独立部件也一并同步 ✓）
        model.rightArm.xRot = 0.18F;
        model.leftArm.xRot = 0.18F;
        model.rightArm.zRot = 0.08F;
        model.leftArm.zRot = -0.08F;
        syncArm(model.rightArm, model.rightSleeve);
        syncArm(model.leftArm, model.leftSleeve);
        // ⑤ 头略微抬起（看着你 ✓）；帽子是头的子部件 ⇒ 自动跟随 ✓
        model.head.xRot = -0.12F;
    }

    /**
     * <b>方块里的玩偶</b>：把玩偶摆到方块上 —— "摆在哪"＝ 0.5 缩放 ＋ 实体模型那套 y 翻转 ＋
     * {@code translate(0,-1.501,0)}（让它"坐"在方块上 ✓）＋ 1px 下沉 ✓；姿势交给 {@link #poseDoll} ✓。
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

        poseDoll(model);
        tnl$logOnce(model);
        model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    /** §885 诊断：只打一次，把"第二层部件"的真实状态说出来（可见性/是否跳过绘制/子部件数） */
    private static boolean tnl$logged = false;

    private static void tnl$logOnce(PlayerModel<?> model) {
        if (tnl$logged) return;
        tnl$logged = true;
        try {
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("TinkersNewlife/FumoMo");
            log.info("[fufu] 模型诊断：PlayerModel 类={} young={}（true ⇒ 走幼年体分支 ✓ §902）"
                            + " hat: visible={} skipDraw={} | jacket visible={} "
                            + "| 左右袖 visible={}/{} | 左右裤 visible={}/{}",
                    model.getClass().getName(), model.young,
                    model.hat.visible, model.hat.skipDraw, model.jacket.visible,
                    model.leftSleeve.visible, model.rightSleeve.visible,
                    model.leftPants.visible, model.rightPants.visible);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/FumoMo")
                    .warn("[fufu] 模型诊断失败：{}", t.toString());
        }
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
