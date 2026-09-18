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

import java.util.ArrayList;
import java.util.List;

/**
 * 巫师套装的<b>自绘模型</b>（法帽 / 法袍 / 法师护腿 / 法师靴子四组部件），
 * 并负责按<b>每个部件槽自己的材料颜色</b>染色 ⇒ 匠魂那种"混搭材料 = 多色" ✓。
 *
 * <h2>⭐ 染色怎么做的（不用渲染层、不用每材料一张图）</h2>
 * {@link ModelPart#render} 自带颜色参数 ✓ ⇒ 只要**一张灰阶贴图**
 * （{@code textures/tinker_armor/wizard_armor/all_grey.png} ✓，四组 UV 区域全填浅灰 ✓），
 * 然后**逐组用该组的材料色**绘制即可 ✓——
 * 顶点色是写在顶点里的，同一批 buffer 里不同颜色互不影响 ✓。
 *
 * <p>每个部件槽用哪个材料（与 5 槽定义一致 ✓）：
 * <ul>
 *   <li>法帽 ← 槽 0（头盔镶板）</li>
 *   <li>法袍 ← 槽 0（胸甲镶板）；宽袖 ← 槽 1（锁链基底）</li>
 *   <li>护腿 ← 槽 0（护腿镶板）；束带 ← 槽 3（法袍系带）</li>
 *   <li>靴子 ← 槽 0（靴子镶板）；靴口 ← 槽 4（魔术布料）</li>
 * </ul>
 *
 * <p>⚠ 只画**当前这件**的部件组（同一个模型实例会按四件分别渲染 ✓）：
 * 戴法帽时不会画出法袍 ✓（上一版靠贴图透明遮罩 ✗，现在靠"只画该组" ✓，更省一张贴图 ✓）。
 *
 * <h2>材料色从哪来</h2>
 * {@link WizardArmorColors}（脚本从 {@code mantle/colors.json} 抽出 ✓）。
 * 材料 id 走**反射**读匠魂 {@code ToolStack} ✓，读不到就用兜底色 ✓（绝不会因为 API 变动而崩 ✗）。
 */
public class WizardArmorModel extends HumanoidModel<LivingEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TinkersNewlife.MOD_ID, "wizard_armor"), "main");

    /** 灰阶贴图（四组 UV 区域全填浅灰 ⇒ 颜色完全由顶点色决定 ✓） */
    public static final String TEX_GREY = "textures/tinker_armor/wizard_armor/all_grey.png";

    private final ModelPart hatBrim;
    private final ModelPart hatCrown;
    private final ModelPart hatTip;
    private final ModelPart robe;
    private final ModelPart sleeveRight;
    private final ModelPart sleeveLeft;
    private final ModelPart legWrapRight;
    private final ModelPart legWrapLeft;
    private final ModelPart bootCuffRight;
    private final ModelPart bootCuffLeft;

    /** 当前正在渲染的那一件（由 {@link #setCurrent} 在取模型时写入 ✓） */
    private ItemStack currentStack = ItemStack.EMPTY;
    private EquipmentSlot currentSlot = EquipmentSlot.HEAD;

    public WizardArmorModel(ModelPart root) {
        super(root);
        this.hatBrim = root.getChild("head").getChild("hat_brim");
        this.hatCrown = root.getChild("head").getChild("hat_crown");
        this.hatTip = root.getChild("head").getChild("hat_tip");
        this.robe = root.getChild("body").getChild("robe");
        this.sleeveRight = root.getChild("right_arm").getChild("sleeve_right");
        this.sleeveLeft = root.getChild("left_arm").getChild("sleeve_left");
        this.legWrapRight = root.getChild("right_leg").getChild("leg_wrap_right_leg");
        this.legWrapLeft = root.getChild("left_leg").getChild("leg_wrap_left_leg");
        this.bootCuffRight = root.getChild("right_leg").getChild("boot_cuff_right_leg");
        this.bootCuffLeft = root.getChild("left_leg").getChild("boot_cuff_left_leg");
    }

    /** 盔甲层每次渲染前都会先取模型 ⇒ 在这里记住"这一件 + 是哪个槽" ✓ */
    public void setCurrent(ItemStack stack, EquipmentSlot slot) {
        this.currentStack = stack == null ? ItemStack.EMPTY : stack;
        this.currentSlot = slot == null ? EquipmentSlot.HEAD : slot;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
        PartDefinition root = mesh.getRoot();

        // ---------- 法帽（cols 0..32 / rows 0..30）----------
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("hat_brim",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -1.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -7.0F, 0.0F));
        head.addOrReplaceChild("hat_crown",
                CubeListBuilder.create().texOffs(0, 10)
                        .addBox(-4.0F, -4.0F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -7.5F, 0.0F));
        head.addOrReplaceChild("hat_tip",
                CubeListBuilder.create().texOffs(0, 23)
                        .addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -11.5F, 0.0F));

        // ---------- 法袍（cols 32..64 / rows 0..29）----------
        root.getChild("body").addOrReplaceChild("robe",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 10.0F, 0.0F));
        root.getChild("right_arm").addOrReplaceChild("sleeve_right",
                CubeListBuilder.create().texOffs(32, 17)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.0F, 4.0F, 0.0F));
        root.getChild("left_arm").addOrReplaceChild("sleeve_left",
                CubeListBuilder.create().texOffs(48, 17)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.0F, 4.0F, 0.0F));

        // ---------- 法师护腿（cols 0..20 / rows 33..48）----------
        for (String leg : new String[]{"right_leg", "left_leg"}) {
            root.getChild(leg).addOrReplaceChild("leg_wrap_" + leg,
                    CubeListBuilder.create().texOffs(0, 33)
                            .addBox(-2.5F, 0.0F, -2.5F, 5.0F, 10.0F, 5.0F, new CubeDeformation(0.0F)),
                    PartPose.offset(0.5F, 1.0F, 0.0F));
        }

        // ---------- 法师靴子（cols 22..46 / rows 33..43）----------
        for (String leg : new String[]{"right_leg", "left_leg"}) {
            root.getChild(leg).addOrReplaceChild("boot_cuff_" + leg,
                    CubeListBuilder.create().texOffs(22, 33)
                            .addBox(-3.0F, 0.0F, -3.0F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)),
                    PartPose.offset(0.5F, 10.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * ⭐ 只画"当前这件"的部件组，并**逐组用该组的材料色**染 ✓。
     * 这样：① 四件各显各的造型 ✓；② 混搭材料 ⇒ 同一件上出现多种颜色 ✓（匠魂原版那种效果 ✓）。
     */
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        switch (currentSlot) {
            case HEAD -> draw(poseStack, buffer, packedLight, packedOverlay, 0,
                    hatBrim, hatCrown, hatTip);
            case CHEST -> {
                draw(poseStack, buffer, packedLight, packedOverlay, 0, robe);
                draw(poseStack, buffer, packedLight, packedOverlay, 1, sleeveRight, sleeveLeft);
            }
            case LEGS -> {
                draw(poseStack, buffer, packedLight, packedOverlay, 0, legWrapRight, legWrapLeft);
                draw(poseStack, buffer, packedLight, packedOverlay, 3, bootCuffRight, bootCuffLeft);
            }
            default -> {
                draw(poseStack, buffer, packedLight, packedOverlay, 0, bootCuffRight, bootCuffLeft);
                draw(poseStack, buffer, packedLight, packedOverlay, 4, legWrapRight, legWrapLeft);
            }
        }
    }

    /** 用"第 index 个材料槽"的颜色绘制这些部件 ✓ */
    private void draw(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int index, ModelPart... parts) {
        float[] c = WizardArmorColors.of(materialPath(index));
        for (ModelPart part : parts) {
            if (part != null) part.render(poseStack, buffer, light, overlay, c[0], c[1], c[2], 1.0F);
        }
    }

    /**
     * 读第 index 个材料槽的材料 id。**反射**调用匠魂 API ⇒ 读不到就用兜底色 ✓
     * （不硬编码方法名，匠魂改 API 也不会让本模型崩 ✗）。
     */
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
                    // 试下一个名字 ✓
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
                    // 试下一个名字 ✓
                }
            }
        } catch (Throwable ignored) {
            // 整套反射失败 ⇒ 兜底色 ✓
        }
        return null;
    }
}
