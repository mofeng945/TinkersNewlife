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
 * 巫师套装的<b>自绘模型</b>（路线 B，见 {@code docs/新盔甲开发步骤.md} §6.5）。
 *
 * <h2>造型（用户要求：头部只做法帽，不做兜帽）</h2>
 * <ul>
 *   <li><b>尖顶法帽</b>：帽檐（14×1×14）+ 帽筒（9×5×9）+ 锥顶（5×4×5）三层箱体 ✓ ——
 *       全部挂在 {@code head} 之下 ⇒ 转头/潜行等姿态**自动跟随** ✓（不用自己同步 ✓）；</li>
 *   <li><b>法袍</b>：躯干下方接一圈下摆（10×14×6，挂在 {@code body} 下 ⇒ 不随腿分开 ✓ 像长袍 ✓）；
 *       两条手臂各加一段宽袖（5×8×5 ✓）。</li>
 * </ul>
 *
 * <h2>为什么所有箱体都 texOffs(0,0)</h2>
 * 贴图是**脚本按材料色生成的纯色图**（见 {@code tools/gen-wizard-armor-textures.ps1} ✓），
 * 整张图基本同色 + 下摆处一条深色镶边 ⇒ UV 取在哪一块**看起来都一样** ✓
 * ⇒ 不必为每个箱体算 UV，省掉最容易出错的一环 ✓（用户要的是"像法袍"的轮廓，不是细节纹样 ✓）。
 *
 * <h2>姿态拷贝</h2>
 * 本模型用 {@link HumanoidModel#createMesh} 的原版骨架（head/hat/body/arms/legs ✓ 同名部件 ✓），
 * 所以 Forge 的 {@code copyModelProperties} 能把玩家姿态整套拷过来 ✓；
 * 自加的 {@code hat_*}/{@code robe}/{@code sleeve_*} 都是这些部件的**子部件** ⇒ 一起跟着动 ✓。
 */
public class WizardArmorModel extends HumanoidModel<LivingEntity> {

    /** 模型层 id（客户端注册用） */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(TinkersNewlife.MOD_ID, "wizard_armor"), "main");

    public WizardArmorModel(ModelPart root) {
        super(root);
    }

    /** 生成模型层定义（在 {@code EntityRenderersEvent.RegisterLayerDefinitions} 里注册 ✓） */
    public static LayerDefinition createBodyLayer() {
        // 以原版盔甲骨架为基底（外扩 0.5 = 盔甲层厚度 ✓）
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
        PartDefinition root = mesh.getRoot();

        // ---------- 尖顶法帽（挂 head 下 ⇒ 跟随头部 ✓） ----------
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("hat_brim",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-7.0F, -1.0F, -7.0F, 14.0F, 1.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -7.0F, 0.0F));
        head.addOrReplaceChild("hat_crown",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.5F, -5.0F, -4.5F, 9.0F, 5.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -7.0F, 0.0F));
        head.addOrReplaceChild("hat_tip",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.5F, -4.0F, -2.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -12.0F, 0.0F));

        // ---------- 法袍：躯干下摆 + 宽袖 ----------
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("robe",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.0F, 0.0F, -3.0F, 10.0F, 14.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 10.0F, 0.0F));

        root.getChild("right_arm").addOrReplaceChild("sleeve_right",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.5F, -2.0F, -2.5F, 5.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.0F, 4.0F, 0.0F));
        root.getChild("left_arm").addOrReplaceChild("sleeve_left",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.5F, -2.0F, -2.5F, 5.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.0F, 4.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
