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
 * 帽檐 ← 槽1 锁链基底 / 帽筒 ← 槽4 魔术布料 / 塔身 ← 槽0 头盔镶板 /
 * 锥顶 ← 槽2（第二块锁链基底）/ 饰带 ← 槽3 法袍系带（用户指定）。
 */
public class WizardArmorModel extends HumanoidModel<LivingEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TinkersNewlife.MOD_ID, "wizard_armor"), "main");

    /** 灰阶贴图（颜色靠顶点着色；画布 128x128） */
    public static final String TEX_GREY = "textures/tinker_armor/wizard_armor/all_grey.png";
    public static final int TEX_W = 128;
    public static final int TEX_H = 128;

    /** 用户模型里"帽檐下沿"的基准 y，对应本模型头部箱体顶面 y = -8 */
    private static final float HAT_BASE_Y = 11.04688F;
    /** 用户模型里 head 组的原点（x/z 归零用） */
    private static final float HAT_ORIGIN_XZ = 8.0F;

    // 法帽五组
    private final ModelPart hatBrim;
    private final ModelPart hatCrown;
    private final ModelPart[] hatTower;
    private final ModelPart hatTip;
    private final ModelPart[] hatBand;

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
        this.hatBrim = head.getChild("hat_brim");
        this.hatCrown = head.getChild("hat_crown");
        this.hatTower = new ModelPart[]{ head.getChild("hat_tower_a"), head.getChild("hat_tower_b") };
        this.hatTip = head.getChild("hat_tip");
        this.hatBand = new ModelPart[]{
                head.getChild("hat_band_a"), head.getChild("hat_band_b"), head.getChild("hat_band_c"),
                head.getChild("hat_band_d"), head.getChild("hat_band_e"), head.getChild("hat_band_f") };
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
        float x = fromX - HAT_ORIGIN_XZ;
        float z = fromZ - HAT_ORIGIN_XZ;
        float w = toX - fromX;
        float h = toY - fromY;
        float d = toZ - fromZ;
        float y = -8.0F - (toY - HAT_BASE_Y);
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v)
                        .addBox(x, y, z, w, h, d, new CubeDeformation(0.0F)),
                PartPose.ZERO);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");

        // 法帽：用户原型的 11 个立方体，坐标原样，UV 重新分配到 128x128
        addBox(head, "hat_brim", -1F, 11.04688F, -1F, 17F, 11.61888F, 17F, 0, 0);
        addBox(head, "hat_crown", 1.5F, 11.59375F, 1.5F, 14.5F, 15.34766F, 14.5F, 0, 20);
        addBox(head, "hat_tower_a", 3F, 14.48828F, 3F, 13F, 18.48828F, 13F, 0, 38);
        addBox(head, "hat_tower_b", 5.49609F, 18.01172F, 6.41016F, 10.50391F, 22.86719F, 11.95703F, 0, 53);
        addBox(head, "hat_tip", 6.64258F, 20.62109F, 10.83984F, 9.35742F, 24.05859F, 14.31641F, 0, 65);
        addBox(head, "hat_band_a", 7F, 12.26953F, 1.33984F, 9F, 14.26953F, 1.53984F, 0, 73);
        addBox(head, "hat_band_b", 7.25F, 12.48047F, 1.15625F, 8.75F, 13.98047F, 1.45625F, 8, 73);
        addBox(head, "hat_band_c", 1.37891F, 12.625F, 1.39063F, 14.57891F, 13.67578F, 1.59063F, 0, 77);
        addBox(head, "hat_band_d", 1.37891F, 12.63672F, 14.42969F, 14.57891F, 13.67969F, 14.62969F, 0, 80);
        addBox(head, "hat_band_e", 1.39453F, 12.58984F, 1.4F, 1.59453F, 13.68359F, 14.6F, 0, 83);
        addBox(head, "hat_band_f", 14.40547F, 12.64453F, 1.4F, 14.60547F, 13.62891F, 14.6F, 0, 87);

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
                draw(poseStack, buffer, packedLight, packedOverlay, 1, this.head, hatBrim);
                draw(poseStack, buffer, packedLight, packedOverlay, 4, this.head, hatCrown);
                draw(poseStack, buffer, packedLight, packedOverlay, 0, this.head, hatTower);
                draw(poseStack, buffer, packedLight, packedOverlay, 2, this.head, hatTip);
                draw(poseStack, buffer, packedLight, packedOverlay, 3, this.head, hatBand);
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
    private String materialPath(int index) {
        try {
            Object tool = slimeknights.tconstruct.library.tools.nbt.ToolStack.from(currentStack);
            if (tool == null) return null;
            Object materials = tool.getClass().getMethod("getMaterials").invoke(tool);
            if (materials == null) return null;
            Object material = null;
            for (String m : new String[]{"getMaterial", "get"}) {
                try {
                    material = materials.getClass().getMethod(m, int.class).invoke(materials, index);
                    break;
                } catch (Throwable ignored) {
                    // 试下一个名字
                }
            }
            if (material == null) return null;
            for (String m : new String[]{"getLocation", "getId", "getIdentifier"}) {
                try {
                    Object id = material.getClass().getMethod(m).invoke(material);
                    if (id != null) {
                        String s = id.toString();
                        int slash = s.indexOf(':');
                        return slash >= 0 ? s.substring(slash + 1) : s;
                    }
                } catch (Throwable ignored) {
                    // 试下一个名字
                }
            }
        } catch (Throwable ignored) {
            // 兜底色
        }
        return null;
    }
}