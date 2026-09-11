package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 飞剑「影视级流光拖尾」：动态条带（Ribbon）+ 自写流光着色器，<b>不依赖任何第三方模组</b>。
 *
 * <h3>原理</h3>
 * <ol>
 *   <li><b>记录轨迹点</b>：每渲染帧取飞剑的部分插值位置（{@code getPosition(partialTick)}）压入环形队列；
 *       三重裁剪：最小步长（慢速不抖动）、最大点数、最大总长度、点最大存活 tick（悬停时拖尾自动收掉）。</li>
 *   <li><b>条带网格</b>：历史点向两侧扩展成顶点对，TRIANGLE_STRIP 提交；侧向量取
 *       {@code 方向 × (相机 − 点)}，即<b>面向相机的条带</b>——任何视角都有厚度，不会侧视消失。</li>
 *   <li><b>流光着色器</b>：U 沿拖尾 0→1，片元用多层正弦 {@code sin(u·f − t·s)} 叠加出流动光带
 *       （见 {@code flying_sword_trail.fsh}），时间由每帧写入的 {@code TrailTime} uniform 驱动。</li>
 * </ol>
 *
 * <h3>为什么画在实体渲染器里（而不是 RenderLevelStageEvent）</h3>
 * 实体渲染时 poseStack 的坐标契约是确定的：调度器已经 {@code translate(x,y,z)}（相机相对+插值位置），
 * 因此「世界坐标 − 实体插值位置 = 本地坐标」这一套与剑身模型本身完全一致，不会有
 * RenderLevelStageEvent 那种「事件里的 poseStack 是否已含相机平移」的歧义。条带与剑身同批次提交，
 * 由原版在实体渲染结束时统一 endBatch。
 *
 * <h3>安全性</h3>
 * 全程 try/catch；着色器或渲染类型任何一步失败都只记一条 warn 并整段跳过（保留原版粒子），
 * 绝不影响正常游戏；资源重载时重建着色器。开关：配置 {@code flying_sword/enable_trail}。
 */
public final class FlyingSwordTrailRenderer {

    private static final ResourceLocation TRAIL_TEXTURE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/entity/flying_sword_trail.png");

    /** 单把飞剑最多保留的历史点数 */
    private static final int MAX_POINTS = 64;
    /** 相邻点最小间距（格） */
    private static final double MIN_STEP = 0.05;
    /** 拖尾最大总长度（格） */
    private static final double MAX_LENGTH = 30.0;
    /** 轨迹点最大存活 tick：悬停时拖尾会自动收掉，不会僵在半空 */
    private static final int MAX_POINT_AGE = 18;
    /** 飞剑消失多久后清掉它的拖尾数据（tick） */
    private static final int DROP_AFTER = 60;
    /** 管状拖尾的半径（格）——圆截面，任何角度看都是立体的（不再是扁平条带） */
    private static final double TUBE_RADIUS = 0.15;
    /** 圆截面分段数（越大越圆；8 段已足够，且顶点数可控） */
    private static final int TUBE_SIDES = 8;

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();

    private static ShaderInstance trailShader;
    private static boolean shaderFailed = false;
    private static RenderType trailRenderType;
    private static boolean loggedOnce = false;

    private FlyingSwordTrailRenderer() {}

    // ============================================================
    //  着色器 / 渲染类型
    // ============================================================

    private static ShaderInstance shader() {
        if (shaderFailed) return null;
        if (trailShader == null) {
            Minecraft mc = Minecraft.getInstance();
            ResourceManager rm = mc.getResourceManager();
            if (rm == null) return null;
            try {
                trailShader = new ShaderInstance(rm,
                        TinkersNewlife.MOD_ID + ":flying_sword_trail",
                        DefaultVertexFormat.POSITION_COLOR_TEX);
            } catch (Throwable t) {
                shaderFailed = true;
                TinkersNewlife.LOGGER.warn("[飞剑] 流光着色器加载失败，拖尾已跳过（仅保留粒子）: {}", t.toString());
            }
        }
        return trailShader;
    }

    /** 自建渲染状态：原版 RenderStateShard 里那几个常量是 protected，外部包拿不到 */
    private static final RenderStateShard.TransparencyStateShard TRAIL_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard("tinkersnewlife_trail_transparency", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            }, () -> {
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
            });
    private static final RenderStateShard.CullStateShard TRAIL_NO_CULL =
            new RenderStateShard.CullStateShard(false);
    private static final RenderStateShard.WriteMaskStateShard TRAIL_COLOR_WRITE =
            new RenderStateShard.WriteMaskStateShard(true, false);
    /** GL_LEQUAL = 515 */
    private static final RenderStateShard.DepthTestStateShard TRAIL_DEPTH_TEST =
            new RenderStateShard.DepthTestStateShard("lequal", 515);

    private static RenderType renderType() {
        if (trailRenderType == null) {
            try {
                trailRenderType = RenderType.create(
                        "tinkersnewlife_flying_sword_trail",
                        DefaultVertexFormat.POSITION_COLOR_TEX,
                        VertexFormat.Mode.QUADS,          // 管状网格：每个圆截面分段一个四边形
                        8192, false, true,
                        RenderType.CompositeState.builder()
                                .setShaderState(new RenderStateShard.ShaderStateShard(FlyingSwordTrailRenderer::shader))
                                .setTextureState(new RenderStateShard.TextureStateShard(TRAIL_TEXTURE, false, false))
                                .setTransparencyState(TRAIL_TRANSPARENCY)
                                .setCullState(TRAIL_NO_CULL)
                                .setWriteMaskState(TRAIL_COLOR_WRITE)
                                .setDepthTestState(TRAIL_DEPTH_TEST)
                                .createCompositeState(false));
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[飞剑] 拖尾渲染类型创建失败，拖尾已跳过: {}", t.toString());
            }
        }
        return trailRenderType;
    }

    /** 资源重载后重建着色器 */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ReloadHandler {
        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager rm, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void unused, ResourceManager rm, ProfilerFiller profiler) {
                    trailShader = null;
                    shaderFailed = false;
                }
            });
        }
    }

    // ============================================================
    //  由 FlyingSwordRenderer 每帧调用
    // ============================================================

    /**
     * 记录轨迹点并绘制条带。必须在实体渲染器<b>最开始</b>调用（poseStack 还没被剑身模型变换污染）。
     */
    public static void renderTrail(FlyingSwordEntity sword, PoseStack poseStack, MultiBufferSource buffer,
                                   float partialTick) {
        if (!ModConfig.FLYING_SWORD_TRAIL.get()) return;
        try {
            long tick = sword.level().getGameTime();
            Vec3 origin = sword.getPosition(partialTick);     // 本帧实体插值位置 = 本地坐标原点

            // ---- 1) 记录轨迹点 ----
            Trail trail = TRAILS.computeIfAbsent(sword.getId(), id -> new Trail());
            trail.lastSeenTick = tick;

            Vector3f c = sword.getTrailColor();
            if (c != null) {
                // 与旧粒子完全一致的配色逻辑：
                //   尾部 = 旧"拖尾尘埃"色，头部 = 旧"每 2 tick 那颗亮尘"色，沿拖尾插值
                if (sword.isChaseMode()) {
                    trail.baseR = Math.min(1.0f, c.x() + 0.5f);
                    trail.baseG = c.y() * 0.4f;
                    trail.baseB = c.z() * 0.3f;
                    trail.headR = 1.0f;
                    trail.headG = 0.3f;
                    trail.headB = 0.1f;
                } else {
                    trail.baseR = Math.min(1.0f, c.x() * 1.3f);
                    trail.baseG = Math.min(1.0f, c.y() * 1.3f);
                    trail.baseB = Math.min(1.0f, c.z() * 1.3f);
                    trail.headR = Math.min(1.0f, c.x() + 0.5f);
                    trail.headG = Math.min(1.0f, c.y() + 0.5f);
                    trail.headB = Math.min(1.0f, c.z() + 0.5f);
                }
            }

            Vec3 newest = trail.points.peekFirst() == null ? null : trail.points.peekFirst().pos;
            if (newest == null || newest.distanceTo(origin) >= MIN_STEP) {
                trail.points.addFirst(new Point(origin, tick));
            }
            prune(trail, tick);
            dropStale(tick);

            if (trail.points.size() < 2) return;

            // ---- 2) 绘制条带 ----
            ShaderInstance shader = shader();
            if (shader == null) return;
            RenderType type = renderType();
            if (type == null) return;

            try {
                var uniform = shader.getUniform("TrailTime");
                if (uniform != null) uniform.set((float) (Util.getMillis() / 1000.0));
            } catch (Throwable ignored) { }

            Matrix4f matrix = new Matrix4f(poseStack.last().pose());   // 复制：后面剑身模型会继续改这个矩阵
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

            VertexConsumer consumer = buffer.getBuffer(type);
            int vertices = buildTube(consumer, matrix, trail, cam, origin);

            if (!loggedOnce && vertices > 0) {
                loggedOnce = true;
                TinkersNewlife.LOGGER.info("[飞剑] 流光拖尾已生效（圆截面光管 + 自定义流光着色器），本帧 {} 顶点", vertices);
            }
        } catch (Throwable t) {
            TRAILS.clear();
            TinkersNewlife.LOGGER.warn("[飞剑] 拖尾渲染异常，已跳过: {}", t.toString());
        }
    }

    /** 三重裁剪：点数、总长度、点存活时间 */
    private static void prune(Trail trail, long tick) {
        while (trail.points.size() > MAX_POINTS) {
            trail.points.removeLast();
        }
        List<Point> snapshot = new ArrayList<>(trail.points);
        double total = 0;
        Point prev = null;
        for (int i = 0; i < snapshot.size(); i++) {
            Point p = snapshot.get(i);
            if (prev != null) total += prev.pos.distanceTo(p.pos);
            if (total > MAX_LENGTH || tick - p.tick > MAX_POINT_AGE) {
                while (trail.points.size() > i) trail.points.removeLast();
                return;
            }
            prev = p;
        }
    }

    /** 清掉早已消失的飞剑的拖尾数据 */
    private static void dropStale(long tick) {
        if (TRAILS.size() <= 1) return;
        Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            if (tick - it.next().getValue().lastSeenTick > DROP_AFTER) it.remove();
        }
    }

    /**
     * 把历史点扩展成<b>圆截面光管</b>（每点一个圆环，相邻圆环之间用四边形连接），
     * 顶点透明度按「圆环法线 · 指向相机」做圆柱侧影明暗，因此从任何角度看都是立体的光柱，
     * 不再是会随视角变薄变没的扁平条带。
     *
     * @param origin 实体本帧插值位置（世界坐标），用于把世界坐标换算成实体本地坐标
     * @return 提交的顶点数
     */
    private static int buildTube(VertexConsumer consumer, Matrix4f matrix, Trail trail, Vec3 cam, Vec3 origin) {
        List<Point> pts = new ArrayList<>(trail.points);   // 0 = 头（剑位置）→ 末尾 = 尾
        int n = pts.size();
        if (n < 2) return 0;

        // ---- 1) 每个点的切向 + 平行传输的正交基（避免圆环扭转、拧麻花）----
        Vec3[] center = new Vec3[n];
        Vec3[] axis1 = new Vec3[n];
        Vec3[] axis2 = new Vec3[n];
        Vec3 ref = null;
        for (int i = 0; i < n; i++) {
            Vec3 p = pts.get(i).pos;
            center[i] = p;

            Vec3 a = pts.get(Math.max(0, i - 1)).pos;
            Vec3 b = pts.get(Math.min(n - 1, i + 1)).pos;
            Vec3 dir = a.subtract(b);
            if (dir.lengthSqr() < 1.0E-8) {
                dir = (i == 0) ? pts.get(1).pos.subtract(p) : p.subtract(pts.get(i - 1).pos);
            }
            if (dir.lengthSqr() < 1.0E-8) dir = new Vec3(0, 1, 0);
            dir = dir.normalize();

            // 平行传输：把上一个圆环的参考向量投影到新切向的垂面
            if (ref == null) {
                ref = arbitraryPerpendicular(dir);
            } else {
                Vec3 proj = ref.subtract(dir.scale(ref.dot(dir)));
                ref = proj.lengthSqr() < 1.0E-8 ? arbitraryPerpendicular(dir) : proj.normalize();
            }
            axis1[i] = ref;
            axis2[i] = dir.cross(ref).normalize();
        }

        // ---- 2) 逐段提交四边形 ----
        int emitted = 0;
        for (int i = 0; i < n - 1; i++) {
            float t0 = (float) i / (n - 1);            // 0 头 → 1 尾
            float t1 = (float) (i + 1) / (n - 1);
            float r0 = (float) tubeRadius(t0);
            float r1 = (float) tubeRadius(t1);

            int alpha0 = (int) (255.0f * Math.pow(1.0 - t0, 0.85));
            int alpha1 = (int) (255.0f * Math.pow(1.0 - t1, 0.85));
            if (alpha0 <= 2 && alpha1 <= 2) continue;

            // 头部用旧"亮尘"色、尾部用旧"拖尾尘埃"色，中间插值（颜色逻辑保持不变）
            float k0 = 1.0f - t0, k1 = 1.0f - t1;
            int r0c = (int) (Math.min(1.0f, trail.baseR + (trail.headR - trail.baseR) * k0) * 255.0f);
            int g0c = (int) (Math.min(1.0f, trail.baseG + (trail.headG - trail.baseG) * k0) * 255.0f);
            int b0c = (int) (Math.min(1.0f, trail.baseB + (trail.headB - trail.baseB) * k0) * 255.0f);
            int r1c = (int) (Math.min(1.0f, trail.baseR + (trail.headR - trail.baseR) * k1) * 255.0f);
            int g1c = (int) (Math.min(1.0f, trail.baseG + (trail.headG - trail.baseG) * k1) * 255.0f);
            int b1c = (int) (Math.min(1.0f, trail.baseB + (trail.headB - trail.baseB) * k1) * 255.0f);

            Vec3 c0 = center[i].subtract(origin);
            Vec3 c1 = center[i + 1].subtract(origin);

            for (int s = 0; s < TUBE_SIDES; s++) {
                double ang0 = Math.PI * 2.0 * s / TUBE_SIDES;
                double ang1 = Math.PI * 2.0 * (s + 1) / TUBE_SIDES;

                Vec3 n00 = axis1[i].scale(Math.cos(ang0)).add(axis2[i].scale(Math.sin(ang0)));
                Vec3 n01 = axis1[i].scale(Math.cos(ang1)).add(axis2[i].scale(Math.sin(ang1)));
                Vec3 n10 = axis1[i + 1].scale(Math.cos(ang0)).add(axis2[i + 1].scale(Math.sin(ang0)));
                Vec3 n11 = axis1[i + 1].scale(Math.cos(ang1)).add(axis2[i + 1].scale(Math.sin(ang1)));

                // 圆柱侧影明暗：法线越正对相机越亮，侧影处渐隐 → 立体感
                float sh00 = shade(n00, center[i], cam);
                float sh01 = shade(n01, center[i], cam);
                float sh10 = shade(n10, center[i + 1], cam);
                float sh11 = shade(n11, center[i + 1], cam);

                emit(consumer, matrix, c0.add(n00.scale(r0)), r0c, g0c, b0c, (int) (alpha0 * sh00), t0);
                emit(consumer, matrix, c0.add(n01.scale(r0)), r0c, g0c, b0c, (int) (alpha0 * sh01), t0);
                emit(consumer, matrix, c1.add(n11.scale(r1)), r1c, g1c, b1c, (int) (alpha1 * sh11), t1);
                emit(consumer, matrix, c1.add(n10.scale(r1)), r1c, g1c, b1c, (int) (alpha1 * sh10), t1);
                emitted += 4;
            }
        }
        return emitted;
    }

    private static void emit(VertexConsumer consumer, Matrix4f matrix, Vec3 local,
                             int r, int g, int b, int a, float u) {
        // V 固定取纹理中线：柔和横向衰减交给「圆柱侧影明暗」处理，避免圆管一侧发黑
        consumer.vertex(matrix, (float) local.x, (float) local.y, (float) local.z)
                .color(r, g, b, Math.max(0, Math.min(255, a)))
                .uv(u, 0.5f)
                .endVertex();
    }

    /** 圆柱侧影明暗系数：法线与「指向相机」夹角越小越亮（保留 0.22 底光，避免后半圈全黑） */
    private static float shade(Vec3 normal, Vec3 point, Vec3 cam) {
        Vec3 toCam = cam.subtract(point);
        if (toCam.lengthSqr() < 1.0E-8) return 1.0f;
        return 0.22f + 0.78f * (float) Math.abs(normal.dot(toCam.normalize()));
    }

    /** 沿拖尾收窄的管半径：剑身处最粗，向尾部缓慢收细 */
    private static double tubeRadius(float t) {
        return TUBE_RADIUS * (1.0 - 0.55 * t);
    }

    /** 与切向垂直的任意单位向量（构建圆环基用） */
    private static Vec3 arbitraryPerpendicular(Vec3 dir) {
        Vec3 refUp = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 p = dir.cross(refUp);
        if (p.lengthSqr() < 1.0E-8) p = dir.cross(new Vec3(0, 0, 1));
        return p.normalize();
    }

    private record Point(Vec3 pos, long tick) {}

    private static final class Trail {
        final ArrayDeque<Point> points = new ArrayDeque<>();
        long lastSeenTick;
        // 配色（与旧粒子逻辑一致）：base = 拖尾尘埃色，head = 亮尘色
        float baseR = 1.0f, baseG = 1.0f, baseB = 1.0f;
        float headR = 1.0f, headG = 1.0f, headB = 1.0f;
    }
}
