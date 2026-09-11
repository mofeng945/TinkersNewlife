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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
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
 *   <li><b>记录轨迹点</b>：每渲染帧读取飞剑的部分插值位置（{@code getPosition(partialTick)}），
 *       压入该飞剑的环形队列；三重裁剪保证性能与观感：最小步长（慢速不抖动）、
 *       最大点数、最大总长度、最大存活 tick（悬停时拖尾会自动收掉）。</li>
 *   <li><b>生成条带网格</b>：把历史点向两侧扩展成有宽度的顶点对，按 TRIANGLE_STRIP 提交。
 *       侧向 = {@code 方向 × (相机 - 点)}，即「面向相机的条带」——任何角度看都有厚度，
 *       不会像固定上方向的条带那样侧视消失。宽度沿拖尾收窄、透明度头实尾虚。</li>
 *   <li><b>流光着色器</b>：顶点 U 坐标沿拖尾 0→1，片元用多层正弦
 *       {@code sin(u·f − t·s)} 叠加出流动光带（见 {@code flying_sword_trail.fsh}），
 *       时间由每帧写入的 {@code TrailTime} uniform 驱动。</li>
 *   <li><b>死亡淡出</b>：飞剑消失后拖尾不立刻消失，而是在 {@link #FADE_TICKS} tick 内淡出，
 *       避免"啪一下没了"。</li>
 * </ol>
 *
 * <h3>安全性</h3>
 * 全程 try/catch；自定义着色器加载失败时自动回退到原版 {@code position_color_tex} 着色器
 * （拖尾仍然可见，只是没有流光），再失败则整段跳过，绝不影响正常游戏。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlyingSwordTrailRenderer {

    private static final ResourceLocation TRAIL_TEXTURE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/entity/flying_sword_trail.png");

    /** 单把飞剑最多保留的历史点数 */
    private static final int MAX_POINTS = 40;
    /** 相邻点最小间距（格）：太小会抖动、太多顶点 */
    private static final double MIN_STEP = 0.07;
    /** 拖尾最大总长度（格） */
    private static final double MAX_LENGTH = 16.0;
    /** 轨迹点最大存活 tick（超时丢弃：悬停时拖尾自动收掉） */
    private static final int MAX_POINT_AGE = 12;
    /** 飞剑消失后拖尾淡出时长（tick） */
    private static final int FADE_TICKS = 8;
    /** 条带半宽（格） */
    private static final double HALF_WIDTH = 0.20;
    /** 超出该距离的飞剑不记录（格） */
    private static final double MAX_DIST_FROM_CAMERA = 128.0;

    /** 实体 id → 拖尾 */
    private static final Map<Integer, Trail> TRAILS = new HashMap<>();

    private static ShaderInstance trailShader;
    private static boolean shaderFailed = false;
    private static RenderType trailRenderType;

    private FlyingSwordTrailRenderer() {}

    // ============================================================
    //  着色器 / 渲染类型
    // ============================================================

    /** 惰性创建流光着色器；失败则置 shaderFailed 并回退原版着色器 */
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
                TinkersNewlife.LOGGER.warn("[飞剑] 流光着色器加载失败，回退原版拖尾着色（无流光）: {}", t.toString());
            }
        }
        return trailShader;
    }

    /** 自建状态：SRC_ALPHA / ONE 的加色辉光（原版 RenderStateShard 里那些常量是 protected，外部包拿不到） */
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
                        VertexFormat.Mode.TRIANGLE_STRIP,
                        1536, false, true,
                        RenderType.CompositeState.builder()
                                .setShaderState(new RenderStateShard.ShaderStateShard(FlyingSwordTrailRenderer::shader))
                                .setTextureState(new RenderStateShard.TextureStateShard(TRAIL_TEXTURE, false, false))
                                .setTransparencyState(TRAIL_TRANSPARENCY)
                                .setCullState(TRAIL_NO_CULL)
                                .setWriteMaskState(TRAIL_COLOR_WRITE)
                                .setDepthTestState(TRAIL_DEPTH_TEST)
                                .createCompositeState(false));
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[飞剑] 拖尾渲染类型创建失败，本次跳过: {}", t.toString());
            }
        }
        return trailRenderType;
    }

    /** 资源重载后重建着色器（着色器源码可能在资源包里被改） */
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
                    trailShader = null;        // 下一帧用新的资源管理器重建
                    shaderFailed = false;
                }
            });
        }
    }

    // ============================================================
    //  每帧：记录 + 绘制
    // ============================================================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            if (!ModConfig.FLYING_SWORD_TRAIL.get()) return;

            long tick = mc.level.getGameTime();
            float partial = event.getPartialTick();
            Vec3 cam = event.getCamera().getPosition();

            recordSwords(mc, tick, partial, cam);

            // 着色器没准备好就不画（避免 RenderSystem.setShader(null) 崩渲染线程）
            ShaderInstance shader = shader();
            if (shader == null) return;
            RenderType type = renderType();
            if (type == null) return;
            // 向 fsh 写入连续时间（uniform 值会随着色器 apply() 一起上传）
            try {
                var uniform = shader.getUniform("TrailTime");
                if (uniform != null) {
                    uniform.set((float) (Util.getMillis() / 1000.0));
                }
            } catch (Throwable ignored) { }

            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer consumer = buffers.getBuffer(type);
            Matrix4f matrix = event.getPoseStack().last().pose();

            Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
            while (it.hasNext()) {
                Trail trail = it.next().getValue();
                int age = (int) (tick - trail.lastSeenTick);
                if (age > FADE_TICKS || trail.points.size() < 2) {
                    if (age > FADE_TICKS) it.remove();
                    continue;
                }
                float fade = 1.0f - Math.max(0, age) / (float) FADE_TICKS;   // 消失后淡出
                buildRibbon(consumer, matrix, trail, cam, tick, fade);
            }
            buffers.endBatch(type);
        } catch (Throwable t) {
            // 渲染异常绝不影响游戏：清掉缓存，下一帧重来
            TRAILS.clear();
            TinkersNewlife.LOGGER.warn("[飞剑] 拖尾渲染异常，已跳过本帧: {}", t.toString());
        }
    }

    /** 记录本帧所有飞剑的位置 */
    private static void recordSwords(Minecraft mc, long tick, float partial, Vec3 cam) {
        double maxSqr = MAX_DIST_FROM_CAMERA * MAX_DIST_FROM_CAMERA;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof FlyingSwordEntity sword)) continue;
            if (sword.distanceToSqr(cam) > maxSqr) continue;

            Trail trail = TRAILS.computeIfAbsent(sword.getId(), id -> new Trail());
            trail.lastSeenTick = tick;

            // 拖尾配色：与粒子一致（追击模式偏炽红，普通模式偏亮）
            Vector3f c = sword.getTrailColor();
            if (c != null) {
                if (sword.isChaseMode()) {
                    trail.r = Math.min(1.0f, c.x() + 0.5f);
                    trail.g = c.y() * 0.4f;
                    trail.b = c.z() * 0.3f;
                } else {
                    trail.r = Math.min(1.0f, c.x() * 1.3f);
                    trail.g = Math.min(1.0f, c.y() * 1.3f);
                    trail.b = Math.min(1.0f, c.z() * 1.3f);
                }
            }

            Vec3 pos = sword.getPosition(partial);
            Point newest = trail.points.peekFirst();
            if (newest == null || newest.pos.distanceTo(pos) >= MIN_STEP) {
                trail.points.addFirst(new Point(pos, tick));
            }
            prune(trail, tick);
        }
    }

    /** 三重裁剪：点数、总长度、点存活时间 */
    private static void prune(Trail trail, long tick) {
        while (trail.points.size() > MAX_POINTS) {
            trail.points.removeLast();
        }
        // 总长度
        double total = 0;
        Point prev = null;
        Iterator<Point> it = trail.points.iterator();
        int index = 0;
        List<Point> snapshot = new ArrayList<>(trail.points);
        for (int i = 0; i < snapshot.size(); i++) {
            Point p = snapshot.get(i);
            if (prev != null) total += prev.pos.distanceTo(p.pos);
            if (total > MAX_LENGTH || tick - p.tick > MAX_POINT_AGE) {
                // 丢弃该点及之后（更旧）的所有点
                while (trail.points.size() > i) trail.points.removeLast();
                break;
            }
            prev = p;
            index++;
        }
    }

    /** 把历史点扩展成面向相机的三角形条带 */
    private static void buildRibbon(VertexConsumer consumer, Matrix4f matrix, Trail trail,
                                    Vec3 cam, long tick, float fade) {
        List<Point> pts = new ArrayList<>(trail.points);   // 0 = 头（剑位置），末尾 = 尾
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            Vec3 p = pts.get(i).pos;
            float t = (float) i / (n - 1);                 // 0 头 → 1 尾

            // 方向：中心差分更稳（首尾退化为单侧差分）
            Vec3 a = pts.get(Math.max(0, i - 1)).pos;
            Vec3 b = pts.get(Math.min(n - 1, i + 1)).pos;
            Vec3 dir = a.subtract(b);
            if (dir.lengthSqr() < 1.0E-8) {
                dir = (i == 0) ? pts.get(1).pos.subtract(p) : p.subtract(pts.get(i - 1).pos);
            }
            if (dir.lengthSqr() < 1.0E-8) continue;
            dir = dir.normalize();

            // 面向相机的侧向量：任何角度都有厚度
            Vec3 side = dir.cross(cam.subtract(p));
            if (side.lengthSqr() < 1.0E-8) side = dir.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1.0E-8) continue;
            side = side.normalize().scale(halfWidth(t));

            // 尾部渐细渐隐（片元里还会再乘一次 u 衰减）
            float pointFade = (float) Math.pow(1.0 - t, 1.5);
            int alpha = (int) (255.0f * fade * pointFade);
            if (alpha <= 2) continue;
            int r = (int) (trail.r * 255.0f);
            int g = (int) (trail.g * 255.0f);
            int bl = (int) (trail.b * 255.0f);

            consumer.vertex(matrix, (float) (p.x + side.x), (float) (p.y + side.y), (float) (p.z + side.z))
                    .color(r, g, bl, alpha).uv(t, 0.0f).endVertex();
            consumer.vertex(matrix, (float) (p.x - side.x), (float) (p.y - side.y), (float) (p.z - side.z))
                    .color(r, g, bl, alpha).uv(t, 1.0f).endVertex();
        }
    }

    /** 沿拖尾收窄的宽度：剑身处最宽，向尾部线性收细 */
    private static double halfWidth(float t) {
        return HALF_WIDTH * (1.0 - 0.72 * t);
    }

    /** 轨迹点：位置 + 记录时的游戏 tick（用于存活时间裁剪） */
    private record Point(Vec3 pos, long tick) {}

    /** 单把飞剑的拖尾状态（飞剑消失后仍保留，用于淡出） */
    private static final class Trail {
        final ArrayDeque<Point> points = new ArrayDeque<>();
        long lastSeenTick;
        float r = 1.0f, g = 1.0f, b = 1.0f;
    }
}
