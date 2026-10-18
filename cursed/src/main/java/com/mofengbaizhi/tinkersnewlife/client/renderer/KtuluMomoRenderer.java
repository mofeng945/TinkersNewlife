package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.client.model.MomoGeoModel;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * TNL1293/TNL1297 克图露·墨默渲染器 —— GeckoLib ＋ **冲刺残影**
 *
 * <p>TNL1293：模型/动画全部改读文件（`geo/ktulu_momo.geo.json`、
 * `animations/ktulu_momo.animation.json`），美术在 BB 里改完导出即可。
 *
 * <p>TNL1297 用户口径：「冲刺……身后渲染残影（模仿瞬身残影）」⇒
 * 冲刺段（{@code MomoPose.DASH}）每 2 tick 采一个位置样本，
 * 用**同一个 geo 模型**以半透明重画（最多 6 个、每个活 8 tick、越老越淡）。
 * ⚠ 关键点：必须先让 {@link MomoGeoModel#GHOST_PASS} 为 true ⇒
 * 渲染类型换成 `entityTranslucent` ✗ 否则 `entityCutoutNoCull` **不做混合** ✓
 * `setShaderColor` 的 alpha 根本没用 ✓
 */
public class KtuluMomoRenderer extends GeoEntityRenderer<KtuluMomo> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/ktulu_momo.png");

    /** 与旧版一致的模型缩放 / 影子大小 */
    private static final float MODEL_SCALE = 0.85F;

    /** 残影：最长活 8 tick、最多 6 个、单帧最大不透明度 */
    private static final int GHOST_LIFE = 8;
    private static final int GHOST_MAX = 6;
    private static final float GHOST_ALPHA = 0.38F;

    /** 一个采样点 */
    private static final class Ghost {
        final double x, y, z;
        final float yaw;
        final int born;

        Ghost(double x, double y, double z, float yaw, int born) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.born = born;
        }
    }

    /** 按实体 id 保存残影样本（客户端渲染线程独占，无需同步） */
    private static final java.util.Map<Integer, List<Ghost>> GHOSTS = new java.util.HashMap<>();

    public KtuluMomoRenderer(EntityRendererProvider.Context context) {
        super(context, new MomoGeoModel());
        this.shadowRadius = 0.43F;
        this.scaleWidth = MODEL_SCALE;
        this.scaleHeight = MODEL_SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(KtuluMomo entity) {
        return TEXTURE;
    }

    @Override
    public void render(KtuluMomo entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // ① 本体先正常画一遍
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);

        // ② 只有冲刺段才留残影（MomoPose.DASH）
        boolean dashing = entity.tnl$animPose()
                == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.DASH.ordinal();
        int id = entity.getId();
        List<Ghost> ghosts = GHOSTS.computeIfAbsent(id, k -> new ArrayList<>());
        int now = entity.tickCount;
        // 清掉过期的
        ghosts.removeIf(g -> now - g.born > GHOST_LIFE);
        if (!dashing) {
            if (ghosts.isEmpty()) {
                GHOSTS.remove(id);
            }
            return;
        }
        double speedSqr = entity.getDeltaMovement().horizontalDistanceSqr();
        if (speedSqr > 0.02D && now % 2 == 0) {
            ghosts.add(new Ghost(entity.getX(), entity.getY(), entity.getZ(),
                    entity.getYRot(), now));
            while (ghosts.size() > GHOST_MAX) {
                ghosts.remove(0);
            }
        }
        if (ghosts.isEmpty()) {
            return;
        }
        // ③ 半透明重画同一套模型
        MomoGeoModel.GHOST_PASS = true;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            for (Ghost g : ghosts) {
                float t = (now - g.born) / (float) GHOST_LIFE;      // 0 新 → 1 最老
                float alpha = GHOST_ALPHA * (1.0F - Math.max(0.0F, Math.min(1.0F, t)));
                if (alpha <= 0.02F) {
                    continue;
                }
                poseStack.pushPose();
                try {
                    poseStack.translate(g.x - entity.getX(), g.y - entity.getY(), g.z - entity.getZ());
                    RenderSystem.setShaderColor(0.55F, 0.55F, 0.70F, alpha);
                    super.render(entity, g.yaw, partialTick, poseStack, buffer, packedLight);
                } finally {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    poseStack.popPose();
                }
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            MomoGeoModel.GHOST_PASS = false;
        }
    }
}
