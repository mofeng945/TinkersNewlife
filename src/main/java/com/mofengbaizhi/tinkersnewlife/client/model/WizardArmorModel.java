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
     * 整体压低（像素，Y 向下 ⇒ 正数=更贴近头）。用户要求"帽子故意压低一点"，
     * 想再低/再高只改这一个数即可（1.0 = 一个像素）。
     */
    private static final float HAT_SINK = 3.0F;

    // 法帽：按"材料槽"分三组（对应 Blockbench 里的 plating / maille / lance 三个组）
    private final ModelPart[] hatPlating;  // 槽0 镶板
    private final ModelPart[] hatMaille;   // 槽1 锁链基底
    private final ModelPart[] hatLace;     // 槽2 法袍系带

    // 其余三件（沿用先前几何）
    private final ModelPart robe;
    private final ModelPart sleeveRight;
    private final ModelPart sleeveLeft;
    private final ModelPart legWrapRight;
    private final ModelPart legWrapLeft;
    private final ModelPart bootCuffRight;
    private final ModelPart bootCuffLeft;

    private ItemStack currentStack = ItemStack.EMPTY;
    private EquipmentSlot currentSlot = EquipmentSlot.HEAD;

    public WizardArmorModel(ModelPart root) {
        super(root);
        ModelPart head = root.getChild("head");
        this.hatPlating = new ModelPart[]{
                head.getChild("hat_tip_ornament"), head.getChild("hat_crown"), head.getChild("hat_tower"),
                head.getChild("hat_tower_body"), head.getChild("hat_tip") };
        this.hatMaille = new ModelPart[]{
                head.getChild("hat_brim"), head.getChild("hat_veil") };
        this.hatLace = new ModelPart[]{
                head.getChild("hat_band_plate"), head.getChild("hat_buckle"), head.getChild("hat_band_front"),
                head.getChild("hat_band_back"), head.getChild("hat_band_left"), head.getChild("hat_band_right") };
        this.robe = root.getChild("body").getChild("robe");
        this.sleeveRight = root.getChild("right_arm").getChild("sleeve_right");
        this.sleeveLeft = root.getChild("left_arm").getChild("sleeve_left");
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

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");

        // 法帽：用户原型（2026-09-18 第二次导出，13 个立方体）坐标原样，UV 重新分配到 128x128
        // —— Blockbench 组 plating ⇒ 槽0 镶板
        addBox(head, "hat_tip_ornament", 7F, 22.13281F, 13.72266F, 9F, 24.13281F, 15.72266F, 24, 100);
        addBox(head, "hat_crown", 1.5F, 11.59375F, 1.5F, 14.5F, 15.12891F, 14.5F, 0, 20);
        addBox(head, "hat_tower", 3F, 14.48828F, 3F, 13F, 18.26953F, 13F, 0, 38);
        addBox(head, "hat_tower_body", 5.49609F, 18.01172F, 6.41016F, 10.50391F, 22.64844F, 11.95703F, 0, 53);
        addBox(head, "hat_tip", 6.64258F, 20.62109F, 10.83984F, 9.35742F, 23.83984F, 14.31641F, 0, 65);
        // —— Blockbench 组 maille ⇒ 槽1 锁链基底
        addBox(head, "hat_brim", -4.5F, 11.28685F, -4.5F, 20.5F, 11.61888F, 20.5F, 0, 0);
        addBox(head, "hat_veil", 3F, 5.44922F, 14.64844F, 13F, 11.44922F, 14.74844F, 0, 100);
        // —— Blockbench 组 lance（系带）⇒ 槽2 法袍系带
        addBox(head, "hat_band_plate", 6.5F, 12.26953F, 1.33984F, 9.5F, 14.26953F, 1.53984F, 0, 73);
        addBox(head, "hat_buckle", 7F, 12.625F, 1.15625F, 9F, 13.90625F, 1.45625F, 8, 73);
        addBox(head, "hat_band_front", 1.37891F, 12.625F, 1.39063F, 14.57891F, 14.125F, 1.59063F, 0, 77);
        addBox(head, "hat_band_back", 1.37891F, 12.63672F, 14.42969F, 14.57891F, 14.13672F, 14.62969F, 0, 80);
        addBox(head, "hat_band_left", 1.39453F, 12.58984F, 1.4F, 1.59453F, 14.08984F, 14.6F, 0, 83);
        addBox(head, "hat_band_right", 14.40547F, 12.64453F, 1.4F, 14.60547F, 14.14453F, 14.6F, 0, 87);

        // 法袍（下摆 + 宽袖）
        root.getChild("body").addOrReplaceChild("robe",
                CubeListBuilder.create().texOffs(96, 0)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 10.0F, 0.0F));
        root.getChild("right_arm").addOrReplaceChild("sleeve_right",
                CubeListBuilder.create().texOffs(96, 17)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.0F, 4.0F, 0.0F));
        root.getChild("left_arm").addOrReplaceChild("sleeve_left",
                CubeListBuilder.create().texOffs(112, 17)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.0F, 4.0F, 0.0F));

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
                draw(poseStack, buffer, packedLight, packedOverlay, 2, this.body, robe);
                draw(poseStack, buffer, packedLight, packedOverlay, 1, this.rightArm, sleeveRight);
                draw(poseStack, buffer, packedLight, packedOverlay, 1, this.leftArm, sleeveLeft);
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
                group(poseStack, buffers, light, overlay, 2, prefix, legsLayer, this.body, robe);
                group(poseStack, buffers, light, overlay, 1, prefix, legsLayer, this.rightArm, sleeveRight);
                group(poseStack, buffers, light, overlay, 1, prefix, legsLayer, this.leftArm, sleeveLeft);
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
