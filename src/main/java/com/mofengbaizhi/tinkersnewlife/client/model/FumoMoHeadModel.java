package com.mofengbaizhi.tinkersnewlife.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * <b>戴在头上的 fufu</b>（§891）：一个"只有 fufu"的人形骨架 ——
 * 人形的 head/body/arms/legs 都建成**空节点**（没有任何方块 ⇒ 什么也不画 ✓），
 * 再把整只 fufu（头壳 + 帽子壳 + 身体 + 两条前伸的腿）挂到 {@code head} 节点下 ✓
 * ⇒ 它**跟着玩家的头一起转** ✓，而且不会像普通护甲那样把玩家整个复制一遍 ✗。
 * <p>UV 全部取自**标准玩家皮肤布局**（头部 8,8 起 / 帽子层 32,0 起 / 躯干 20,20 起 ✓）⇒ 不会错位 ✓。
 */
public class FumoMoHeadModel extends HumanoidModel<LivingEntity> {

    /** 戴在头上的整体缩放（1.0 = 与玩家头同尺度的一半左右 ✓ 看着像摆在头顶的玩偶 ✓） */
    public static final float SCALE = 0.55F;

    public FumoMoHeadModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // ① 人形骨架全部建成空节点（不画任何东西 ✓ 只是为了让 HumanoidModel 构造能取到这些名字 ✓）
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);

        // ② 把 fufu 挂到 head 下（y 负方向 = 往上 ✓ 让它"坐"在头顶）
        PartDefinition head = root.getChild("head");
        // 头壳（标准头部 8x8x8 ⇒ UV 8,8 起）
        head.addOrReplaceChild("fufu_head",
                CubeListBuilder.create().texOffs(8, 8).addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8),
                PartPose.offset(0.0F, -30.0F, 0.0F));
        // 帽子壳（标准帽子层 32,0 起 ✓ 外扩 0.6px ⇒ 看得见 ✓）
        head.addOrReplaceChild("fufu_hat",
                CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, new CubeDeformation(0.6F)),
                PartPose.offset(0.0F, -30.0F, 0.0F));
        // 身体（标准躯干 20,20 起）
        head.addOrReplaceChild("fufu_body",
                CubeListBuilder.create().texOffs(20, 20).addBox(-4.0F, -12.0F, -2.0F, 8, 6, 4),
                PartPose.offset(0.0F, -25.5F, 0.0F));
        // 两条前伸的腿（标准右腿 4,20 起）
        head.addOrReplaceChild("fufu_leg_r",
                CubeListBuilder.create().texOffs(4, 20).addBox(-2.0F, -1.5F, -8.0F, 3, 3, 8),
                PartPose.offset(-1.5F, -24.5F, 1.0F));
        head.addOrReplaceChild("fufu_leg_l",
                CubeListBuilder.create().texOffs(4, 20).addBox(-1.0F, -1.5F, -8.0F, 3, 3, 8),
                PartPose.offset(1.5F, -24.5F, 1.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
