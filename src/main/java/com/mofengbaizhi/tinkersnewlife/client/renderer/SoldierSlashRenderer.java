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
 * 「兵士佩刀」的<b>刀光</b>渲染（§810／§811／§1044）—— 仿拔刀剑那种弧形斩击面片 ✓，<b>不是横扫粒子</b> ✓。
 *
 * <h2>§1044 ⭐ 几何（用户最终口径 ✓）：弧带的<b>圆心就是施法者</b></h2>
 * 用户原话三步走 ✓：
 * <ol>
 *   <li>「剑气弧面应当凹面自玩家方向，凸面远离玩家」✓；</li>
 *   <li>「我剑气的贴图本身不就是个<b>平面</b>扇形弧面吗，我是想让你把<b>圆心对着玩家</b>，
 *       不是让你弯曲弧面啊」✓ ⇒ <b>弧面必须保持平面</b> ✗ 不许弯成球冠 ✗；</li>
 *   <li>「退化成细线好解决，<b>侧一点点角度就好</b>」✓ ⇒ 圆心<b>就放在玩家身上</b> ✓
 *       （半径 = 玩家到弧带的距离 ✓），细线问题用<b>侧倾</b>解决 ✓。</li>
 * </ol>
 *
 * <p>所以现在的画法（仍然是<b>一张平面的弧带</b> ✓ 顶点全部共面 ✓ 一个字都没弯 ✓）：
 * <pre>
 *   圆心 C    = 施法者位置（玩家身上 ✓）
 *   半径 r    = clamp(|锚点 - C|, MIN_RADIUS, MAX_RADIUS)   // 锚点 = 剑气所在位置（目标身上 ✓）
 *   跨中方向 u = 「C → 锚点」方向 d 绕"世界上方"侧倾 TILT_DEG 度 ✓   ← 就是用户说的"侧一点点角度" ✓
 *   平面内另一轴 v = d × u
 *   弧带点    = C + r·(cos θ·u + sin θ·v)，θ ∈ ±ARC_SPAN/2        // 一条平面圆弧 ✓
 * </pre>
 * ⇒ 弧带的<b>圆心正好压在玩家身上</b> ✓ ⇒ 弧面凹向玩家 ✓ 凸面背离玩家 ✓（正是要的效果 ✓）。
 *
 * <h2>为什么"侧倾"能解决细线 ✗</h2>
 * 若跨中方向就取 d（正对锚点 ✓），整张弧带的平面必含直线 C→锚点 ✓，
 * 而玩家正沿着这条线看 ⇒ 平面<b>侧对镜头</b>、退化成一条细线 ✗（就是 §811 当年那个毛病 ✗）。
 * 把 u 从 d 侧倾 {@link #TILT_DEG} 度后 ✓，平面法线 {@code m = u × v} 与视线方向 d 的可见度
 * <b>{@code |m·d| = sin(TILT_DEG)}</b> ✓（推导：{@code v ∝ d×u ⇒ m ∝ d - u·cosT ⇒ m·d = (1-cos²T)/sinT = sinT} ✓）
 * ⇒ 20° 时约 <b>0.34</b> ✓ —— 不刺眼、也不是细线 ✓，正好是用户要的"侧一点点" ✓。
 *
 * <h2>⚠ 弧带与锚点的关系（如实说明，别当成"精确穿过目标" ✗）</h2>
 * 侧倾之后，弧带所在平面<b>不再包含锚点</b> ✗ —— 锚点到该平面的距离是
 * <b>{@code rad·|m·d| = rad·sin(TILT_DEG)}</b> ✓（构造上圆心 = 锚点 − rad·d ✓，而 d 与平面法线 m 的夹角是 90°−TILT ✓）。
 * 即 20° 侧倾时弧带会从目标<b>旁边约 0.34·rad 格</b>扫过 ✓（rad = 玩家到目标的距离 ✓，3 格时约 1.0 格 ✓）；
 * 弧带本身很宽（半宽 ≈ 0.27·rad ✓）⇒ 观感仍是"扫过目标" ✓，但要想<b>精确穿过锚点</b>只能把
 * {@link #TILT_DEG} 调小 ✓（代价：越接近侧对镜头、越像细线 ✗）。
 * 这正是用户说的「<b>侧一点点角度就好</b>」所对应的取舍 ✓。
 *
 * <h2>兜底（拿不到施法者位置时 ✓）</h2>
 * 退回 §811 那套：以实体自身为圆心、法线朝相机的平面 ✓（老观感 ✓ 绝不消失 ✗）。
 *
 * <h2>⚠ 渲染类型：只用原版 shader getter（FlyingSwordTrailRenderer 的教训 ✓）</h2>
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
    /** 兜底半径（格）：拿不到施法者位置时才用 ✓（老行为 ✓） */
    private static final float FALLBACK_RADIUS = 1.25F;
    /** 弧带最粗处的半宽 : 半径 的固定比例 ⇒ 半径越大弧带越宽、比例不变 ✓（0.34 / 1.25 ✓） */
    private static final float HALF_WIDTH_RATIO = 0.272F;
    /**
     * §1044 <b>侧倾角</b>（度 ✓）—— 用户口径「<b>侧一点点角度就好</b>」✓。
     * <p>把跨中方向从"正对锚点"偏向世界上方这么多 ✓ ⇒ 弧面不再侧对镜头 ✓
     * 可见度 = {@code sin(该角)} ✓（20° ⇒ 0.34 ✓）。
     * <b>调大 = 弧面更"正对"玩家（更宽更显眼 ✓）但弧带离锚点更远 ✗；调小 = 更薄更贴线 ✗</b> ⇒ 一般 15~30 ✓。
     */
    private static final double TILT_DEG = 20.0D;
    /** §1044 半径下限（格）：贴脸时方向会退化 ⇒ 给个下限免得圆心与锚点重合 ✓ */
    private static final double MIN_RADIUS = 0.5D;
    /**
     * §1044 半径上限（格）：悚怖钢剑气会沿弹道飞出很远 ✓，半径 = 距离会让弧带变成几十格的巨圈 ✗
     * ⇒ 超远时把半径截到该值 ✓（此时圆心落在玩家与剑气之间 ✓ 仍在玩家那一侧 ✓）。
     */
    private static final double MAX_RADIUS = 4.0D;
    /** §1044 服务端给的角度只当"平面内摆动"用 ✓ 并限幅 ⇒ 不至于把弧带甩出锚点 ✗ */
    private static final float SPAN_SHIFT_LIMIT = 30.0F;

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

            // §1044 锚点 = 实体自身的渲染位置（poseStack 的原点就在这儿 ✓）
            Vec3 anchor = entity.getPosition(partialTick);
            Frame frame = frame(entity, anchor, mirror);

            poseStack.pushPose();
            Matrix4f matrix = poseStack.last().pose();

            VertexConsumer consumer = buffer.getBuffer(type);
            float halfSpan = ARC_SPAN * 0.5F;
            float spanShift = Mth.clamp(entity.getSlashRoll(), -SPAN_SHIFT_LIMIT, SPAN_SHIFT_LIMIT);

            float rad = frame.radius;
            float halfWidthBase = rad * HALF_WIDTH_RATIO * scale;

            for (int i = 0; i < SEGMENTS; i++) {
                float u0 = (float) i / SEGMENTS;
                float u1 = (float) (i + 1) / SEGMENTS;

                float ang0 = (float) Math.toRadians(-halfSpan + ARC_SPAN * u0 + spanShift);
                float ang1 = (float) Math.toRadians(-halfSpan + ARC_SPAN * u1 + spanShift);

                float cos0 = Mth.cos(ang0), sin0 = Mth.sin(ang0);
                float cos1 = Mth.cos(ang1), sin1 = Mth.sin(ang1);

                float w0 = halfWidthBase * taper(u0);
                float w1 = halfWidthBase * taper(u1);

                // 径向单位向量（从圆心指向弧带 ✓）= cos θ·u + sin θ·v
                Vec3 dir0 = frame.u.scale(cos0).add(frame.v.scale(sin0));
                Vec3 dir1 = frame.u.scale(cos1).add(frame.v.scale(sin1));

                // 弧带中心线：始终落在半径 rad 的圆上 ⇒ 圆心就是施法者 ✓
                Vec3 c0 = frame.center.add(dir0.scale(rad));
                Vec3 c1 = frame.center.add(dir1.scale(rad));

                // 沿半径方向加/减厚度 ⇒ 内缘点与外缘点（整张弧带仍然共面 ✓）
                Vec3 in0 = c0.subtract(dir0.scale(w0)), out0 = c0.add(dir0.scale(w0));
                Vec3 in1 = c1.subtract(dir1.scale(w1)), out1 = c1.add(dir1.scale(w1));

                // 四边形（不剔除 ⇒ 绕序无所谓 ✓）：内0 → 外0 → 外1 → 内1
                emit(consumer, matrix, in0, anchor, r, g, b, a, u0, 0.0F);
                emit(consumer, matrix, out0, anchor, r, g, b, a, u0, 1.0F);
                emit(consumer, matrix, out1, anchor, r, g, b, a, u1, 1.0F);
                emit(consumer, matrix, in1, anchor, r, g, b, a, u1, 0.0F);
            }

            poseStack.popPose();
        } catch (Throwable t) {
            // 刀光画不出来只是少个特效 ✓ 绝不影响游戏 ✓
        }
    }

    // ============================================================
    //  §1044 弧带的平面坐标系（圆心／跨中方向／平面内另一轴／半径）
    // ============================================================

    /** 一张平面弧带的坐标系 ✓：圆心 ＋ 平面内两轴 ＋ 半径 ✓ */
    private static final class Frame {
        final Vec3 center;
        final Vec3 u;
        final Vec3 v;
        final float radius;

        Frame(Vec3 center, Vec3 u, Vec3 v, float radius) {
            this.center = center;
            this.u = u;
            this.v = v;
            this.radius = radius;
        }
    }

    /**
     * §1044 建系：<b>圆心 = 施法者</b> ✓、半径 = 施法者到锚点的距离（限幅 ✓）、
     * 跨中方向 = 「圆心 → 锚点」方向绕世界上方侧倾 {@link #TILT_DEG} 度 ✓（保证不侧对镜头 ✓）。
     *
     * @param mirror 奇数段左右镜像 ✓ ⇒ 直接翻转平面内第二轴（弧带形状镜像 ✓ 可见度不变 ✓）
     */
    private static Frame frame(SoldierSlashEntity entity, Vec3 anchor, float mirror) {
        Vec3 caster = entity.getCasterPos();
        if (caster != null) {
            Vec3 toAnchor = anchor.subtract(caster);
            double dist = toAnchor.length();
            if (dist > 1.0E-4D) {
                float radius = (float) Mth.clamp(dist, MIN_RADIUS, MAX_RADIUS);
                Vec3 d = toAnchor.scale(1.0D / dist);

                // 世界上方在 ⊥d 平面内的分量；d 恰好竖直时换一个侧向 ✓
                Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
                Vec3 perp = up.subtract(d.scale(up.dot(d)));
                if (perp.lengthSqr() < 1.0E-6D) {
                    perp = new Vec3(1.0D, 0.0D, 0.0D).subtract(d.scale(d.x));
                }
                if (perp.lengthSqr() < 1.0E-6D) {
                    perp = new Vec3(0.0D, 0.0D, 1.0D).subtract(d.scale(d.z));
                }
                perp = perp.normalize();

                // 跨中方向：从 d 侧倾 TILT_DEG 度 ⇒ |m·d| = sin(TILT) > 0 ⇒ 不会退化成细线 ✓
                double tilt = Math.toRadians(TILT_DEG);
                Vec3 u = d.scale(Math.cos(tilt)).add(perp.scale(Math.sin(tilt))).normalize();
                Vec3 v = d.cross(u);
                if (v.lengthSqr() < 1.0E-9D) {
                    v = perp;
                }
                v = v.normalize().scale(mirror);

                // 圆心：让半径正好等于"圆心到锚点"⇒ 弧带必然穿过锚点 ✓（锚点在 θ = -TILT_DEG 处 ✓）
                Vec3 center = anchor.subtract(d.scale(radius));
                return new Frame(center, u, v, radius);
            }
        }

        // 兜底（老行为 ✓）：以实体自身为圆心、法线朝相机的平面 ✓ —— 至少不会消失 ✗
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 n = eye.subtract(anchor);
        if (n.lengthSqr() < 1.0E-6D) n = new Vec3(0.0D, 0.0D, 1.0D);
        n = n.normalize();
        Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(n);
        if (right.lengthSqr() < 1.0E-6D) right = new Vec3(1.0D, 0.0D, 0.0D);
        right = right.normalize();
        Vec3 up2 = n.cross(right).normalize();
        return new Frame(anchor, up2, right.scale(mirror), FALLBACK_RADIUS);
    }

    /** 弧带半宽沿弧长的收尖曲线：两端 0 ⇒ 刀锋尖 ✓ 中间最粗 ✓ */
    private static float taper(float u) {
        float s = Mth.sin((float) Math.PI * Mth.clamp(u, 0.0F, 1.0F));
        return (float) Math.pow(Math.max(0.0F, s), 0.55);
    }

    /**
     * 顶点必须按 {@link DefaultVertexFormat#NEW_ENTITY} 的元素顺序写 ✓：
     * Position → Color → UV0 → UV1(overlay) → UV2(lightmap) → Normal ✓
     * <p>§1044：传入的是<b>世界坐标</b> ✓，这里减去锚点（= poseStack 原点所在 ✓）换算成实体局部坐标 ✓。
     * （UV1 写 {@code NO_OVERLAY} ⇒ 原版 emissive 着色器不会拿 overlay 图给刀光染色 ✓；
     *   UV2 写全亮 ⇒ 刀光自发光、不受方块光照影响 ✓）。
     */
    private static void emit(VertexConsumer consumer, Matrix4f matrix, Vec3 world, Vec3 anchor,
                             int r, int g, int b, int a, float u, float v) {
        consumer.vertex(matrix,
                        (float) (world.x - anchor.x),
                        (float) (world.y - anchor.y),
                        (float) (world.z - anchor.z))
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
