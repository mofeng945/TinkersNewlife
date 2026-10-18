package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.client.model.MomoGeoModel;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TNL1293/TNL1297/TNL1300 克图露·墨默渲染器 —— GeckoLib ＋ **残影**
 *
 * <p>模型/动画全部读文件（`geo/ktulu_momo.geo.json`、`animations/ktulu_momo.animation.json`）。
 *
 * <h3>残影两种来源</h3>
 * <ul>
 *   <li>**冲刺**（{@code MomoPose.DASH}）：每 2 tick 采一个位置样本，最多 6 个、每个活 8 tick；</li>
 *   <li>**瞬身**（{@code MomoPose.BLINK}）：位移一帧跳 > 2 格 ⇒ 在"旧位置 → 新位置"之间
 *       补 6 个残影（越靠旧位置越淡），做出"拉出一条影子"的效果。</li>
 * </ul>
 *
 * ⚠ 关键：必须先让 {@link MomoGeoModel#GHOST_PASS} 为 true ⇒ 渲染类型换成
 * `entityTranslucent` ✗ 否则 `entityCutoutNoCull` **不做混合** ✓ `setShaderColor` 的 alpha 无效 ✓
 */
public class KtuluMomoRenderer extends GeoEntityRenderer<KtuluMomo> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/ktulu_momo.png");

    private static final float MODEL_SCALE = 0.85F;

    /** 残影：最长活 8 tick、最多 8 个、单帧最大不透明度 */
    private static final int GHOST_LIFE = 8;
    private static final int GHOST_MAX = 8;
    private static final float GHOST_ALPHA = 0.38F;
    /** 瞬身残影数量 */
    private static final int BLINK_GHOSTS = 6;

    private static final class Ghost {
        final double x, y, z;
        final float yaw;
        final int born;
        final float strength;

        Ghost(double x, double y, double z, float yaw, int born, float strength) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.born = born;
            this.strength = strength;
        }
    }

    /** 按实体 id 保存残影样本（客户端渲染线程独占） */
    private static final Map<Integer, List<Ghost>> GHOSTS = new HashMap<>();
    /** 上一帧的位置（用来发现瞬身那一帧的跳跃） */
    private static final Map<Integer, Vec3> LAST_POS = new HashMap<>();

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
        // ① 本体
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);

        int id = entity.getId();
        int now = entity.tickCount;
        int pose = entity.tnl$animPose();
        boolean dashing = pose == MomoPose.DASH.ordinal();
        boolean blinking = pose == MomoPose.BLINK.ordinal();

        Vec3 cur = new Vec3(entity.getX(), entity.getY(), entity.getZ());
        Vec3 last = LAST_POS.put(id, cur);
        List<Ghost> ghosts = GHOSTS.computeIfAbsent(id, k -> new ArrayList<>());
        ghosts.removeIf(g -> now - g.born > GHOST_LIFE);

        // ② 瞬身：一帧位移很大 ⇒ 沿路径补影子
        if (blinking && last != null && cur.distanceToSqr(last) > 4.0D) {
            for (int i = 1; i <= BLINK_GHOSTS; i++) {
                double f = i / (double) (BLINK_GHOSTS + 1);
                ghosts.add(new Ghost(
                        last.x + (cur.x - last.x) * f,
                        last.y + (cur.y - last.y) * f,
                        last.z + (cur.z - last.z) * f,
                        entity.getYRot(), now - i, 1.0F - 0.10F * i));
            }
            while (ghosts.size() > GHOST_MAX) {
                ghosts.remove(0);
            }
        }

        // ③ 冲刺：每 2 tick 采一个样本
        if (dashing && entity.getDeltaMovement().horizontalDistanceSqr() > 0.02D && now % 2 == 0) {
            ghosts.add(new Ghost(entity.getX(), entity.getY(), entity.getZ(),
                    entity.getYRot(), now, 1.0F));
            while (ghosts.size() > GHOST_MAX) {
                ghosts.remove(0);
            }
        }

        if (ghosts.isEmpty()) {
            GHOSTS.remove(id);
            LAST_POS.remove(id);
            return;
        }

        // ④ 半透明重画同一套模型
        MomoGeoModel.GHOST_PASS = true;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            for (Ghost g : ghosts) {
                float t = (now - g.born) / (float) GHOST_LIFE;
                float alpha = GHOST_ALPHA * g.strength
                        * (1.0F - Math.max(0.0F, Math.min(1.0F, t)));
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
