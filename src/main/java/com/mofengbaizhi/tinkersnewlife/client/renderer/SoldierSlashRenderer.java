package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.SoldierSlashEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 「兵士佩刀」的<b>刀光</b>渲染（§810／§811）—— 仿拔刀剑那种弧形斩击面片 ✓，<b>不是横扫粒子</b> ✓。
 *
 * <h2>画法</h2>
 * <ol>
 *   <li><b>弧带网格</b>：把一条圆弧按 {@link #SEGMENTS} 段切成四边形条带 ✓；弧带宽（厚度）沿弧长按
 *       {@code sin(πu)^0.55} 收尖 ⇒ 两端自然变成"刀锋尖" ✓ 就是拔刀剑那种月牙形 ✓；</li>
 *   <li><b>贴图</b>：{@code textures/entity/soldier_slash.png} ✓（64×64 弧光图 ✓ 中间厚、
 *       偏内一条锐利刃口线、u 两端收尖 ✓）—— 只提供"柔光 + 刃口"的明暗，形状仍由网格决定 ✓；</li>
 *   <li><b>顶点色</b>：{@link SoldierSlashEntity#getSlashTint()}（唐横刀 = 灰色 {@code 0xC9CFD9} ✓）
 *       × 随寿命淡出 ✓ ⇒ 灰色刀光 ✓。</li>
 * </ol>
 *
 * <h2>§811 ⭐ 朝向：<b>弧面永远正对镜头</b>（billboard）——修"刀光重叠"✗</h2>
 * 旧做法是服务端给每道刀光随机一个 {@code yaw} ✗ ⇒ 弧面**侧对镜头**时只剩一条细线 ✗，
 * 几道叠在一起就分不清谁是谁 ✗（用户反馈：「<b>附加刀光别给我重叠了，每一段角度应该不太一样</b>」✓）。
 * 现在改成：<b>渲染时按"实体 → 相机"方向算基向量</b> ✓ ⇒ 弧面必定正对镜头、每一道都完整可见 ✓；
 * 玩家看到的"这一刀的角度"就完全由 {@link SoldierSlashEntity#getSlashRoll()} 决定 ✓
 * （服务端给每一段安排互不相同的角度 ✓），再配合 {@code MIRROR} 左右镜像 ＋ 每段不同的大小
 * ＋ 每段不同的落点（服务端偏移 ✓）⇒ <b>一眼就是"连续几刀"而不是一团重叠</b> ✓。
 *
 * <h2>⚠ 渲染类型：必须只用原版 shader getter（FlyingSwordTrailRenderer 的教训 ✓）</h2>
 * 本仓（飞剑拖尾）踩过：自己 {@code new ShaderInstance} 的着色器<b>不在光影包（Oculus/Iris）的替换名单里</b> ✗
 * ⇒ 在装了光影包的整合包里<b>整片看不见</b> ✗。所以这里照抄飞剑那套 ✓：
 * 用<b>原版</b> {@code GameRenderer::getRendertypeEntityTranslucentEmissiveShader}（自发光程序 ✓）
 * ＋ <b>原版</b>顶点格式 {@code DefaultVertexFormat.NEW_ENTITY} ✓ ＋ 加色混合（SRC_ALPHA, ONE ✓ 更"发光" ✓）
 * ＋ 不写深度（刀光互相叠加不发黑 ✓）＋ 不剔除 ＋ LEQUAL 深度测试 ✓。
 * {@code RenderStateShard} 里那几个常量是 {@code protected} ⇒ 外部包拿不到 ✓ 只能自己建一份 ✓（同飞剑 ✓）。
 *
 * <p>全程 {@code try/catch} ✓：渲染任何一步出问题都只是这一道光不画 ✓ 绝不影响游戏 ✓。
 */
public class SoldierSlashRenderer extends EntityRenderer<SoldierSlashEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/entity/soldier_slash.png");

    /** 弧带分段数（越大越圆滑 ✓ 顶点数可控 ✓） */
    private static final int SEGMENTS = 18;
    /** 弧的张角（度）—— 太大像圆环 ✗ 太小像直线 ✗ 152° 左右最像斩击 ✓ */
    private static final float ARC_SPAN = 152.0F;
    /** 弧半径（格） */
    private static final float RADIUS = 1.25F;
    /** 弧最粗处的半宽（格） */
    private static final float MAX_HALF_WIDTH = 0.34F;
    /** 弧带整体下移（格）：让月牙大致居中在目标身体中心 ✓ */
    private static final float VERTICAL_CENTER = -0.42F;

    private static RenderType slashRenderType;

    public SoldierSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SoldierSlashEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(SoldierSlashEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        try {
            int life = Math.max(1, entity.getLifeTicks());
            float age = entity.tickCount + partialTick - entity.getStartDelay();
            if (age < 0.0F) return;                       // 还没轮到这一段 ✓
            float t = Mth.clamp(age / (float) life, 0.0F, 1.0F);

            float alpha = (float) Math.pow(1.0F - t, 0.7);              // 淡出 ✓
            float scale = entity.getSlashScale() * (0.80F + 0.36F * t); // 微微张开 ✓
            if (alpha <= 0.02F) return;

            RenderType type = renderType();
            if (type == null) return;

            int tint = entity.getSlashTint();
            int r = (tint >> 16) & 0xFF;
            int g = (tint >> 8) & 0xFF;
            int b = tint & 0xFF;
            int a = (int) (255.0F * alpha);
            float mirror = entity.isMirrored() ? -1.0F : 1.0F;   // 奇数段左右镜像 ⇒ 与偶数段不同 ✓

            poseStack.pushPose();
            applyBillboard(poseStack, entity, partialTick, entity.getSlashRoll());
            Matrix4f matrix = poseStack.last().pose();

            VertexConsumer consumer = buffer.getBuffer(type);
            float halfSpan = ARC_SPAN * 0.5F;

            for (int i = 0; i < SEGMENTS; i++) {
                float u0 = (float) i / SEGMENTS;
                float u1 = (float) (i + 1) / SEGMENTS;

                float ang0 = (float) Math.toRadians(-halfSpan + ARC_SPAN * u0);
                float ang1 = (float) Math.toRadians(-halfSpan + ARC_SPAN * u1);

                float sin0 = Mth.sin(ang0), cos0 = Mth.cos(ang0);
                float sin1 = Mth.sin(ang1), cos1 = Mth.cos(ang1);

                float rad = RADIUS * scale;
                float w0 = MAX_HALF_WIDTH * taper(u0) * scale;
                float w1 = MAX_HALF_WIDTH * taper(u1) * scale;

                // 弧心点（本地 XY 平面，法线朝 +Z ⇒ 正对镜头 ✓）
                float cx0 = sin0 * rad, cy0 = cos0 * rad + VERTICAL_CENTER * scale;
                float cx1 = sin1 * rad, cy1 = cos1 * rad + VERTICAL_CENTER * scale;

                // 沿"半径方向"加/减厚度 ⇒ 内侧点与外侧点
                float ix0 = cx0 - sin0 * w0, iy0 = cy0 - cos0 * w0;
                float ox0 = cx0 + sin0 * w0, oy0 = cy0 + cos0 * w0;
                float ix1 = cx1 - sin1 * w1, iy1 = cy1 - cos1 * w1;
                float ox1 = cx1 + sin1 * w1, oy1 = cy1 + cos1 * w1;

                // 四边形（不剔除 ⇒ 绕序无所谓 ✓）：内0 → 外0 → 外1 → 内1
                emit(consumer, matrix, ix0 * mirror, iy0, r, g, b, a, u0, 0.0F);
                emit(consumer, matrix, ox0 * mirror, oy0, r, g, b, a, u0, 1.0F);
                emit(consumer, matrix, ox1 * mirror, oy1, r, g, b, a, u1, 1.0F);
                emit(consumer, matrix, ix1 * mirror, iy1, r, g, b, a, u1, 0.0F);
            }

            poseStack.popPose();
        } catch (Throwable t) {
            // 刀光画不出来只是少个特效 ✓ 绝不影响游戏 ✓
        }
    }

    /**
     * 把弧面转到<b>正对相机</b>（billboard），并在平面内自转 {@code rollDeg} ✓。
     *
     * <p>基向量直接按世界坐标算 ✓ 不碰 Minecraft 的 yaw/pitch 约定 ⇒ 不会出现"符号搞反了变成背对镜头"✗：
     * <ul>
     *   <li>{@code n} = 「实体 → 相机」单位向量 ⇒ 弧面法线 ✓（永远正对镜头 ✓）；</li>
     *   <li>{@code r} = 世界上方向 × n ⇒ 平面内的"左右" ✓；</li>
     *   <li>{@code u} = n × r ⇒ 平面内的"上下" ✓；</li>
     *   <li>再按 {@code rollDeg} 在 (r, u) 平面内旋转 ⇒ 玩家看到的就是"这一刀的角度" ✓。</li>
     * </ul>
     */
    private static void applyBillboard(PoseStack poseStack, SoldierSlashEntity entity, float partialTick,
                                       float rollDeg) {
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = entity.getPosition(partialTick);

        Vec3 n = eye.subtract(center);
        if (n.lengthSqr() < 1.0E-6D) n = new Vec3(0.0D, 0.0D, 1.0D);
        n = n.normalize();

        Vec3 r = new Vec3(0.0D, 1.0D, 0.0D).cross(n);
        if (r.lengthSqr() < 1.0E-6D) r = new Vec3(1.0D, 0.0D, 0.0D);
        r = r.normalize();

        Vec3 up = n.cross(r).normalize();

        double rad = Math.toRadians(rollDeg);
        double cs = Math.cos(rad);
        double sn = Math.sin(rad);
        Vec3 r2 = r.scale(cs).add(up.scale(sn));
        Vec3 up2 = up.scale(cs).subtract(r.scale(sn));

        // 列主序：第 0 列 = 本地 X 轴、第 1 列 = 本地 Y 轴、第 2 列 = 本地 Z 轴（法线 ✓）
        Matrix4f m = new Matrix4f(
                (float) r2.x, (float) r2.y, (float) r2.z, 0.0F,
                (float) up2.x, (float) up2.y, (float) up2.z, 0.0F,
                (float) n.x, (float) n.y, (float) n.z, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
        // 1.20.1 的 PoseStack#mulPose 只收四元数 ✗ ⇒ 这里直接把基向量矩阵乘进 pose 矩阵 ✓
        // （自发光程序不用法线 ✓ 所以不更新 normal 矩阵也没影响 ✓）
        poseStack.last().pose().mul(m);
    }

    /** 弧带半宽沿弧长的收尖曲线：两端 0 ⇒ 刀锋尖 ✓ 中间最粗 ✓ */
    private static float taper(float u) {
        float s = Mth.sin((float) Math.PI * Mth.clamp(u, 0.0F, 1.0F));
        return (float) Math.pow(Math.max(0.0F, s), 0.55);
    }

    /**
     * 顶点必须按 {@link DefaultVertexFormat#NEW_ENTITY} 的元素顺序写 ✓：
     * Position → Color → UV0 → UV1(overlay) → UV2(lightmap) → Normal ✓
     * （UV1 写 {@code NO_OVERLAY} ⇒ 原版 emissive 着色器不会拿 overlay 图给刀光染色 ✓；
     *   UV2 写全亮 ⇒ 刀光自发光、不受方块光照影响 ✓）。
     */
    private static void emit(VertexConsumer consumer, Matrix4f matrix, float x, float y,
                             int r, int g, int b, int a, float u, float v) {
        consumer.vertex(matrix, x, y, 0.0F)
                .color(r, g, b, Math.max(0, Math.min(255, a)))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    // ============================================================
    //  渲染类型（★ 只用原版着色器 + 原版顶点格式，光影包才认 ✓ 同 FlyingSwordTrailRenderer）
    // ============================================================

    /** 加色混合：比原版半透明更"发光" ✓ 与飞剑拖尾同一套 ✓ */
    private static final RenderStateShard.TransparencyStateShard SLASH_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard("tinkersnewlife_slash_transparency", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            }, () -> {
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
            });
    private static final RenderStateShard.CullStateShard SLASH_NO_CULL =
            new RenderStateShard.CullStateShard(false);
    private static final RenderStateShard.WriteMaskStateShard SLASH_COLOR_WRITE =
            new RenderStateShard.WriteMaskStateShard(true, false);
    /** GL_LEQUAL = 515 */
    private static final RenderStateShard.DepthTestStateShard SLASH_DEPTH_TEST =
            new RenderStateShard.DepthTestStateShard("lequal", 515);

    private static RenderType renderType() {
        if (slashRenderType == null) {
            try {
                slashRenderType = RenderType.create(
                        "tinkersnewlife_soldier_slash",
                        DefaultVertexFormat.NEW_ENTITY,
                        VertexFormat.Mode.QUADS,
                        2048, false, true,
                        RenderType.CompositeState.builder()
                                // ⭐ 关键：着色器必须是「原版 shader getter」提供的（光影包只替换这些 ✓）
                                .setShaderState(new RenderStateShard.ShaderStateShard(
                                        GameRenderer::getRendertypeEntityTranslucentEmissiveShader))
                                .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, false, false))
                                .setTransparencyState(SLASH_TRANSPARENCY)
                                .setCullState(SLASH_NO_CULL)
                                .setWriteMaskState(SLASH_COLOR_WRITE)
                                .setDepthTestState(SLASH_DEPTH_TEST)
                                .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                                .createCompositeState(false));
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[唐横刀] 刀光渲染类型创建失败，刀光已跳过: {}", t.toString());
            }
        }
        return slashRenderType;
    }
}
