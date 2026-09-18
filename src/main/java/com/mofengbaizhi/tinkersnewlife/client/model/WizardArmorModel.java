package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
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
import net.minecraft.world.entity.LivingEntity;

/**
 * 巫师套装的<b>自绘模型</b>（路线 B）：法帽 / 法袍 / 法师护腿 / 法师靴子四组部件。
 *
 * <h2>⭐ 四组部件各有独立的 UV 区域（关键）</h2>
 * 盔甲层是"**同一套模型逐件渲染**"的 ✓ —— 每件显示哪些部件，靠**这件自己的贴图**决定
 * （原版就是这样：胸甲贴图里腿部区域是透明的 ✗）。
 * 所以本模型把四组部件的 UV 分别放在 64×64 的四块互不重叠的区域里 ✓：
 *
 * <pre>
 *   法帽   cols 0..32  rows 0..30   （帽檐 8×1×8 / 帽筒 8×4×8 / 锥顶 4×3×4）
 *   法袍   cols 32..64 rows 0..29   （下摆 8×12×4 / 宽袖 4×8×4 ×2）
 *   护腿   cols 0..20  rows 33..48  （腿部束带 5×10×5 ×2）
 *   靴子   cols 22..46 rows 33..43  （靴筒 6×4×6 ×2）
 * </pre>
 *
 * 于是四张贴图各只画自己那一块、其余**全透明** ⇒ 只穿靴子就只显示靴子 ✓
 * （上一版是"一张实色贴图 + 所有箱体都 texOffs(0,0)"✗ ⇒ 只穿一件也会把帽袍一起画出来 ✗，
 *  用户指出后返工 ✓）。
 *
 * <h2>姿态</h2>
 * 骨架用原版 {@link HumanoidModel#createMesh}（同名部件 head/hat/body/arm/leg ✓），
 * Forge 的姿态拷贝照常生效 ✓；自加部件都是它们的**子部件** ⇒ 一起跟着动 ✓。
 */
public class WizardArmorModel extends HumanoidModel<LivingEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TinkersNewlife.MOD_ID, "wizard_armor"), "main");

    /** 四张贴图（按部位遮罩；由 tools/gen-wizard-armor-art.ps1 生成 ✓） */
    public static final String TEX_HAT = "textures/armor/wizard_armor/hat.png";
    public static final String TEX_ROBE = "textures/armor/wizard_armor/robe.png";
    public static final String TEX_LEGGINGS = "textures/armor/wizard_armor/mage_leggings.png";
    public static final String TEX_BOOTS = "textures/armor/wizard_armor/mage_boots.png";

    public WizardArmorModel(ModelPart root) {
        super(root);
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
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("robe",
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
}
