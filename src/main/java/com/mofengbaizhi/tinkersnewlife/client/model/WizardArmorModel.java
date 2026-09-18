package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 巫师套装的<b>自绘模型</b>。
 *
 * <h2>法帽：用户用 Blockbench 建的原型（11 个立方体，旋转全 0，组名 head，画布 64x64）</h2>
 * 换算约定（可追溯、可一键微调）：
 * x/z = 模型坐标 - 8（与原版头部同原点，头箱体 -4..4）；
 * y ：用户以"帽檐下沿 = 11.04688、向上为负"表达（即他们的 Y 向上），
 *     本模组头部原点在脖子、头箱体 -8..0（Y 向下）=&gt;
 *     y0 = -8 - (to.y - HAT_BASE_Y)，h = to.y - from.y；
 * 全部旋转为 0 所以不需要 rotation。整体高低不对只改 HAT_BASE_Y 一个常量即可。
 *
 * <h2>颜色（顶点着色，逐组按材料槽取色）</h2>
 * 套装是 **3 槽**：槽0 镶板 / 槽1 锁链基底 / 槽2 法袍系带 ✓。
 * 帽子按用户在 Blockbench 里分的**组名**归槽（2026-09-18 第二次导出 ✓）：
 * <pre>
 *   plating → 槽0 镶板      ：尖顶小球、帽身、塔身上段+下段、锥顶
 *   maille  → 槽1 锁链基底  ：帽檐、背后垂布
 *   lance   → 槽2 法袍系带  ：系带扣座、扣、前后左右四条饰带（组名是 lace 的笔误，按系带处理 ✓）
 * </pre>
 * 想换分组只改 {@link #createBodyLayer()} 里 `addBox` 出来的组 + 下面两个 render 里画到第几槽 ✓。
 */
public class WizardArmorModel extends HumanoidModel<LivingEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TinkersNewlife.MOD_ID, "wizard_armor"), "main");

    /** 灰阶贴图（颜色靠顶点着色；画布 128x128） */
    public static final String TEX_GREY = "textures/armor/wizard/grey.png";
    public static final int TEX_W = 128;
    public static final int TEX_H = 128;

    /** 用户模型里"帽檐下沿"的基准 y，对应本模型头部箱体顶面 y = -8 */
    private static final float HAT_BASE_Y = 11.04688F;
    /** 用户模型里 head 组的原点（x/z 归零用） */
    private static final float HAT_ORIGIN_XZ = 8.0F;
    /**
     * 整体缩放：用户模型是按"更大的头"做的（帽檐 18 宽 / 帽筒 13 宽，原版头只有 8 宽），
     * 直接搬过来又大又高；收到 0.7 后帽筒约 9.1、帽檐约 12.6，正好罩住原版头。
     */
    private static final float HAT_SCALE = 0.8F;
    /**
     * 整体压低/抬高（像素，Y 向下 ⇒ 数越大越贴近头、数越小越往上）。
     * 用户要求"帽子故意压低一点"，第一次定 3.0；后来要求"整体上移 0.5" ⇒ 2.5 ✓。
     * 想再低/再高只改这一个数即可（1.0 = 一个像素）。
     */
    private static final float HAT_SINK = 2.5F;

    // 法帽：按"材料槽"分三组（对应 Blockbench 里的 plating / maille / lance 三个组）
    private final ModelPart[] hatPlating;  // 槽0 镶板
    private final ModelPart[] hatMaille;   // 槽1 锁链基底
    private final ModelPart[] hatLace;     // 槽2 法袍系带

    // 法袍（用户 2026-09-18 第三份模型 ✓）：主体挂 body、袖子挂 left_arm / right_arm ✓
    // 槽映射同帽子：plating → 槽0、maille → 槽1、lace → 槽2 ✓（换算见 tools\convert-robe-model.ps1 ✓）
    private final ModelPart[] robePlating;   // 槽0 镶板（袍身 + 前後裙摆）
    private final ModelPart[] robeMaille;    // 槽1 锁链基底（内衬）
    private final ModelPart[] robeLace;      // 槽2 法袍系带（腰带）
    private final ModelPart[] sleeveLPlating, sleeveLMaille, sleeveLLace;   // 左袖（MC 的 left_arm ✓）
    private final ModelPart[] sleeveRPlating, sleeveRMaille, sleeveRLace;   // 右袖（MC 的 right_arm ✓）

    // 其余两件（护腿 / 靴子，还是占位几何 ✓）
    private final ModelPart legWrapRight;
    private final ModelPart legWrapLeft;
    private final ModelPart bootCuffRight;
    private final ModelPart bootCuffLeft;

    private ItemStack currentStack = ItemStack.EMPTY;
    private EquipmentSlot currentSlot = EquipmentSlot.HEAD;

    public WizardArmorModel(ModelPart root) {
        super(root);
        ModelPart head = root.getChild("head");
        // ⚠ 下面三行由 tools\import-hat-blockbench.ps1 生成/替换（标记之间勿手改 ✓）
        // <<< HAT_PARTS (generated) >>>
        this.hatPlating = new ModelPart[]{ head.getChild("plating_2"), head.getChild("plating_3"), head.getChild("plating_4"), head.getChild("plating_5"), head.getChild("plating_6") };
        this.hatMaille = new ModelPart[]{ head.getChild("maille_0"), head.getChild("maille_1") };
        this.hatLace = new ModelPart[]{ head.getChild("lace_7"), head.getChild("lace_8"), head.getChild("lace_9"), head.getChild("lace_10"), head.getChild("lace_11"), head.getChild("lace_12") };
        // <<< /HAT_PARTS >>>
        ModelPart body = root.getChild("body");
        ModelPart armL = root.getChild("left_arm");
        ModelPart armR = root.getChild("right_arm");
        this.robePlating = new ModelPart[]{
                body.getChild("body_plating_0"), body.getChild("body_plating_1"), body.getChild("body_plating_2") };
        this.robeMaille = new ModelPart[]{ body.getChild("body_maille_4") };
        this.robeLace = new ModelPart[]{ body.getChild("body_lace_3") };
        this.sleeveLPlating = new ModelPart[]{ armL.getChild("left_arm_plating_5") };
        this.sleeveLMaille = new ModelPart[]{ armL.getChild("left_arm_maille_7") };
        this.sleeveLLace = new ModelPart[]{ armL.getChild("left_arm_lace_6") };
        this.sleeveRPlating = new ModelPart[]{ armR.getChild("right_arm_plating_8") };
        this.sleeveRMaille = new ModelPart[]{ armR.getChild("right_arm_maille_10") };
        this.sleeveRLace = new ModelPart[]{ armR.getChild("right_arm_lace_9") };
        this.legWrapRight = root.getChild("right_leg").getChild("leg_wrap_right_leg");
        this.legWrapLeft = root.getChild("left_leg").getChild("leg_wrap_left_leg");
        this.bootCuffRight = root.getChild("right_leg").getChild("boot_cuff_right_leg");
        this.bootCuffLeft = root.getChild("left_leg").getChild("boot_cuff_left_leg");
    }

    public void setCurrent(ItemStack stack, EquipmentSlot slot) {
        this.currentStack = stack == null ? ItemStack.EMPTY : stack;
        this.currentSlot = slot == null ? EquipmentSlot.HEAD : slot;
    }

    /** Blockbench 立方体 -> addBox（换算见类注释） */
    private static void addBox(PartDefinition parent, String name,
                               float fromX, float fromY, float fromZ,
                               float toX, float toY, float toZ, int u, int v) {
        float x = (fromX - HAT_ORIGIN_XZ) * HAT_SCALE;
        float z = (fromZ - HAT_ORIGIN_XZ) * HAT_SCALE;
        float w = (toX - fromX) * HAT_SCALE;
        float h = (toY - fromY) * HAT_SCALE;
        float d = (toZ - fromZ) * HAT_SCALE;
        float y = -8.0F - (toY - HAT_BASE_Y) * HAT_SCALE + HAT_SINK;
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v)
                        .addBox(x, y, z, w, h, d, new CubeDeformation(0.0F)),
                PartPose.ZERO);
    }

    /**
     * 法袍专用：**直接把"骨骼局部坐标"加进模型** ✓
     * （换算、缩放、居中都在 {@code tools\convert-robe-model.ps1} 里算好了 ✓：
     * body 缩放 1.0 / 袖子 1.12 并按手臂居中 ✓；局部空间 y 向下，body 局部 0 = 颈肩 ✓）
     */
    private static void addLocalBox(PartDefinition parent, String name,
                                    float x, float y, float z, float w, float h, float d, int u, int v) {
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v)
                        .addBox(x, y, z, w, h, d, new CubeDeformation(0.0F)),
                PartPose.ZERO);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");

        // 法帽：用户原型（2026-09-18 第二次导出，13 个立方体）坐标原样，UV 重新分配到 128x128
        // ⚠ 下面这段由 tools\import-hat-blockbench.ps1 生成/替换（标记之间勿手改 ✓）
        // <<< HAT_ADD_BOX (generated) >>>
        addBox(head, "maille_0", -4.5F, 11.28685F, -4.5F, 20.5F, 11.61888F, 20.5F, 0, 0);
        addBox(head, "maille_1", 3F, 5.44922F, 14.64844F, 13F, 11.44922F, 14.74844F, 62, 37);
        addBox(head, "plating_2", 7F, 22.13281F, 13.72266F, 9F, 24.13281F, 15.72266F, 80, 37);
        addBox(head, "plating_3", 1.5F, 11.59375F, 1.5F, 14.5F, 15.12891F, 14.5F, 0, 22);
        addBox(head, "plating_4", 3F, 14.48828F, 3F, 13F, 18.26953F, 13F, 0, 37);
        addBox(head, "plating_5", 5.49609F, 18.01172F, 6.41016F, 10.50391F, 22.64844F, 11.95703F, 33, 37);
        addBox(head, "plating_6", 6.64258F, 20.62109F, 10.83984F, 9.35742F, 23.83984F, 14.31641F, 51, 37);
        addBox(head, "lace_7", 6.5F, 12.26953F, 1.33984F, 9.5F, 14.26953F, 1.53984F, 23, 50);
        addBox(head, "lace_8", 7F, 12.625F, 1.15625F, 9F, 13.90625F, 1.45625F, 88, 37);
        addBox(head, "lace_9", 1.37891F, 12.625F, 1.39063F, 14.57891F, 14.125F, 1.59063F, 30, 50);
        addBox(head, "lace_10", 1.37891F, 12.63672F, 14.42969F, 14.57891F, 14.13672F, 14.62969F, 0, 50);
        addBox(head, "lace_11", 1.39453F, 12.58984F, 1.4F, 1.59453F, 14.08984F, 14.6F, 43, 22);
        addBox(head, "lace_12", 14.40547F, 12.64453F, 1.4F, 14.60547F, 14.14453F, 14.6F, 66, 22);
        // <<< /HAT_ADD_BOX >>>

        // 法袍：用户第三份模型（11 个方块）—— 由 tools\convert-robe-model.ps1 换算好的**局部坐标** ✓
        PartDefinition bodyPart = root.getChild("body");
        PartDefinition armLPart = root.getChild("left_arm");
        PartDefinition armRPart = root.getChild("right_arm");
        // —— 袍身（槽0 镶板）：主身 + 前后裙摆
        addLocalBox(bodyPart, "body_plating_0", -4.65F, -0.49531F, -2.35F, 9.3F, 9F, 4.7F, 49, 61);
        addLocalBox(bodyPart, "body_plating_1", -4.53516F, 8.19609F, -2.28906F, 4.2F, 9F, 4.7F, 97, 63);
        addLocalBox(bodyPart, "body_plating_2", 0.33516F, 8.19609F, -2.28906F, 4.2F, 9F, 4.7F, 78, 63);
        // —— 内衬（槽1 锁链基底）
        addLocalBox(bodyPart, "body_maille_4", -4.5F, -0.34688F, -2.25F, 9F, 10F, 4.5F, 21, 53);
        // —— 腰带（槽2 法袍系带）
        addLocalBox(bodyPart, "body_lace_3", -4.75F, 6.93437F, -2.5F, 9.5F, 2F, 5F, 83, 78);
        // —— 左袖（MC 的 left_arm ✓）
        addLocalBox(armLPart, "left_arm_plating_5", -1.40713F, 1.66433F, -2.408F, 4.816F, 11.2F, 4.816F, 0, 53);
        addLocalBox(armLPart, "left_arm_maille_7", -1.34149F, -1.99755F, -2.31875F, 4.704F, 3.92F, 4.704F, 0, 71);
        addLocalBox(armLPart, "left_arm_lace_6", -1.429F, 12.38745F, -2.464F, 4.928F, 2.24F, 4.928F, 62, 78);
        // —— 右袖（MC 的 right_arm ✓）
        addLocalBox(armRPart, "right_arm_plating_8", -3.37527F, 1.66433F, -2.408F, 4.816F, 11.2F, 4.816F, 62, 43);
        addLocalBox(armRPart, "right_arm_maille_10", -3.32889F, -1.99755F, -2.31875F, 4.704F, 3.92F, 4.704F, 21, 69);
        addLocalBox(armRPart, "right_arm_lace_9", -3.4654F, 12.38745F, -2.464F, 4.928F, 2.24F, 4.928F, 41, 76);

        // 法师护腿
        for (String leg : new String[]{"right_leg", "left_leg"}) {
            root.getChild(leg).addOrReplaceChild("leg_wrap_" + leg,
                    CubeListBuilder.create().texOffs(96, 36)
                            .addBox(-2.5F, 0.0F, -2.5F, 5.0F, 10.0F, 5.0F, new CubeDeformation(0.0F)),
                    PartPose.offset(0.5F, 1.0F, 0.0F));
        }

        // 法师靴子
        for (String leg : new String[]{"right_leg", "left_leg"}) {
            root.getChild(leg).addOrReplaceChild("boot_cuff_" + leg,
                    CubeListBuilder.create().texOffs(96, 52)
                            .addBox(-3.0F, 0.0F, -3.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)),
                    PartPose.offset(0.5F, 10.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    /** 只画"当前这件"的部件组，并逐组用该组材料槽的颜色 */
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        switch (currentSlot) {
            case HEAD -> {
                // 槽0 镶板：尖顶小球 / 帽身 / 塔身 / 锥顶
                draw(poseStack, buffer, packedLight, packedOverlay, 0, this.head, hatPlating);
                // 槽1 锁链基底：帽檐 / 背后垂布
                draw(poseStack, buffer, packedLight, packedOverlay, 1, this.head, hatMaille);
                // 槽2 法袍系带：扣座 + 扣 + 四向饰带
                draw(poseStack, buffer, packedLight, packedOverlay, 2, this.head, hatLace);
            }
            case CHEST -> {
                // 槽0 镶板：袍身 + 前后裙摆
                draw(poseStack, buffer, packedLight, packedOverlay, 0, this.body, robePlating);
                // 槽1 锁链基底：内衬（+ 两袖肩片）
                draw(poseStack, buffer, packedLight, packedOverlay, 1, this.body, robeMaille);
                draw(poseStack, buffer, packedLight, packedOverlay, 1, this.leftArm, sleeveLMaille);
                draw(poseStack, buffer, packedLight, packedOverlay, 1, this.rightArm, sleeveRMaille);
                // 槽2 法袍系带：腰带 + 两袖袖口
                draw(poseStack, buffer, packedLight, packedOverlay, 2, this.body, robeLace);
                draw(poseStack, buffer, packedLight, packedOverlay, 2, this.leftArm, sleeveLLace);
                draw(poseStack, buffer, packedLight, packedOverlay, 2, this.rightArm, sleeveRLace);
                // 袖子主体（槽0 镶板）
                draw(poseStack, buffer, packedLight, packedOverlay, 0, this.leftArm, sleeveLPlating);
                draw(poseStack, buffer, packedLight, packedOverlay, 0, this.rightArm, sleeveRPlating);
            }
            case LEGS -> {
                draw(poseStack, buffer, packedLight, packedOverlay, 0, this.rightLeg, legWrapRight);
                draw(poseStack, buffer, packedLight, packedOverlay, 0, this.leftLeg, legWrapLeft);
                draw(poseStack, buffer, packedLight, packedOverlay, 3, this.rightLeg, bootCuffRight);
                draw(poseStack, buffer, packedLight, packedOverlay, 3, this.leftLeg, bootCuffLeft);
            }
            default -> {
                draw(poseStack, buffer, packedLight, packedOverlay, 0, this.rightLeg, bootCuffRight);
                draw(poseStack, buffer, packedLight, packedOverlay, 0, this.leftLeg, bootCuffLeft);
                draw(poseStack, buffer, packedLight, packedOverlay, 4, this.rightLeg, legWrapRight);
                draw(poseStack, buffer, packedLight, packedOverlay, 4, this.leftLeg, legWrapLeft);
            }
        }
    }

    /** 用"第 index 个材料槽"的颜色绘制这些部件，并先套上父部件变换 */
    private void draw(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int index,
                      ModelPart parent, ModelPart... parts) {
        float[] c = WizardArmorColors.of(materialPath(index));
        for (ModelPart part : parts) {
            if (part == null) continue;
            poseStack.pushPose();
            if (parent != null) parent.translateAndRotate(poseStack);
            part.render(poseStack, buffer, light, overlay, c[0], c[1], c[2], 1.0F);
            poseStack.popPose();
        }
    }

    /** 反射读匠魂材料 id（读不到就退回兜底色） */
    /**
     * 渲染层用：只画"这一件"的部件组，**按组取匠魂生成器产出的按材料贴图** ✓；
     * 某组没有对应贴图时退回 {@code all_grey.png} + 顶点着色 ✓（外观与 renderToBuffer 完全一致 ✓）。
     */
    public void renderMulti(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers,
                            int light, int overlay, ItemStack stack, EquipmentSlot slot) {
        setCurrent(stack, slot);
        String prefix = com.mofengbaizhi.tinkersnewlife.client.renderer.WizardArmorTextures.prefixFor(slot);
        boolean legsLayer = com.mofengbaizhi.tinkersnewlife.client.renderer.WizardArmorTextures.usesLeggingsLayer(slot);
        switch (slot) {
            case HEAD -> {
                // 槽0 镶板：尖顶小球 / 帽身 / 塔身 / 锥顶
                group(poseStack, buffers, light, overlay, 0, prefix, legsLayer, this.head, hatPlating);
                // 槽1 锁链基底：帽檐 / 背后垂布
                group(poseStack, buffers, light, overlay, 1, prefix, legsLayer, this.head, hatMaille);
                // 槽2 法袍系带：扣座 + 扣 + 四向饰带
                group(poseStack, buffers, light, overlay, 2, prefix, legsLayer, this.head, hatLace);
            }
            case CHEST -> {
                // 槽0 镶板：袍身 + 裙摆 + 两袖主体
                group(poseStack, buffers, light, overlay, 0, prefix, legsLayer, this.body, robePlating);
                group(poseStack, buffers, light, overlay, 0, prefix, legsLayer, this.leftArm, sleeveLPlating);
                group(poseStack, buffers, light, overlay, 0, prefix, legsLayer, this.rightArm, sleeveRPlating);
                // 槽1 锁链基底：内衬 + 两袖肩片
                group(poseStack, buffers, light, overlay, 1, prefix, legsLayer, this.body, robeMaille);
                group(poseStack, buffers, light, overlay, 1, prefix, legsLayer, this.leftArm, sleeveLMaille);
                group(poseStack, buffers, light, overlay, 1, prefix, legsLayer, this.rightArm, sleeveRMaille);
                // 槽2 法袍系带：腰带 + 两袖袖口
                group(poseStack, buffers, light, overlay, 2, prefix, legsLayer, this.body, robeLace);
                group(poseStack, buffers, light, overlay, 2, prefix, legsLayer, this.leftArm, sleeveLLace);
                group(poseStack, buffers, light, overlay, 2, prefix, legsLayer, this.rightArm, sleeveRLace);
            }
            case LEGS -> {
                group(poseStack, buffers, light, overlay, 0, prefix, legsLayer, this.rightLeg, legWrapRight);
                group(poseStack, buffers, light, overlay, 0, prefix, legsLayer, this.leftLeg, legWrapLeft);
                group(poseStack, buffers, light, overlay, 3, prefix, legsLayer, this.rightLeg, bootCuffRight);
                group(poseStack, buffers, light, overlay, 3, prefix, legsLayer, this.leftLeg, bootCuffLeft);
            }
            default -> {
                group(poseStack, buffers, light, overlay, 0, prefix, legsLayer, this.rightLeg, bootCuffRight);
                group(poseStack, buffers, light, overlay, 0, prefix, legsLayer, this.leftLeg, bootCuffLeft);
                group(poseStack, buffers, light, overlay, 4, prefix, legsLayer, this.rightLeg, legWrapRight);
                group(poseStack, buffers, light, overlay, 4, prefix, legsLayer, this.leftLeg, legWrapLeft);
            }
        }
    }

    /** 单组：有生成贴图 ⇒ 用图且不再染色；没有 ⇒ 灰图 + 材料色顶点着色 ✓ */
    private void group(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers,
                       int light, int overlay, int index, String prefix, boolean legsLayer,
                       ModelPart parent, ModelPart... parts) {
        String mat = materialPath(index);
        net.minecraft.resources.ResourceLocation tex =
                com.mofengbaizhi.tinkersnewlife.client.renderer.WizardArmorTextures
                        .materialArmorTexture(prefix, mat, legsLayer);
        float[] c = tex != null ? new float[]{ 1.0F, 1.0F, 1.0F }
                                : WizardArmorColors.of(mat);
        net.minecraft.resources.ResourceLocation use =
                tex != null ? tex : com.mofengbaizhi.tinkersnewlife.client.renderer.WizardArmorTextures.GREY;
        VertexConsumer buffer = buffers.getBuffer(
                net.minecraft.client.renderer.RenderType.armorCutoutNoCull(use));
        for (ModelPart part : parts) {
            if (part == null) continue;
            poseStack.pushPose();
            if (parent != null) parent.translateAndRotate(poseStack);
            part.render(poseStack, buffer, light, overlay, c[0], c[1], c[2], 1.0F);
            poseStack.popPose();
        }
    }
    /**
     * 读第 index 个材料槽的材料 id（路径部分）。
     *
     * <p>⭐ <b>照抄匠魂源码</b>（{@code MaterialArmorTextureSupplier.Material#getMaterial} ✓）：
     * 匠魂**不**通过 {@code ToolStack} 对象取材料，而是直接读物品 NBT 里的材料字符串表
     * —— {@code ToolStack.TAG_MATERIALS}（{@code tic_materials}）是一个字符串列表，按 index 取即可 ✓。
     *
     * <p>我此前用反射猜方法名 ✗ ⇒ 取不到就所有槽退回同一材料 ⇒ "不同槽用了不同材料，甲上却只有单色" ✗
     * （用户实测 ✓）。这段改成与匠魂一致的读法后退回兜底色只在真正没有材料时发生 ✓。
     */
    @javax.annotation.Nullable
    private String materialPath(int index) {
        try {
            if (currentStack == null || currentStack.isEmpty()) return null;
            net.minecraft.nbt.CompoundTag tag = currentStack.getTag();
            if (tag == null) return null;
            String key = slimeknights.tconstruct.library.tools.nbt.ToolStack.TAG_MATERIALS;
            if (!tag.contains(key, net.minecraft.nbt.Tag.TAG_LIST)) return null;
            net.minecraft.nbt.ListTag list = tag.getList(key, net.minecraft.nbt.Tag.TAG_STRING);
            if (index < 0 || index >= list.size()) return null;
            String s = list.getString(index);
            if (s == null || s.isEmpty()) return null;
            int colon = s.indexOf(':');
            return colon >= 0 ? s.substring(colon + 1) : s;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
