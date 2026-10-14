package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.StarfallHitCache;
import com.mofengbaizhi.tinkersnewlife.client.StarfallRenderTypes;
import com.mofengbaizhi.tinkersnewlife.content.entity.StarfallArrow;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** TNL880 \u661f\u9668\u5c3e\u8ff9\uff08\u2605\u7167\u642c\u53c2\u8003 ClientRenderHandler \u2713\uff09 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarfallRenderHandler {

    private static final int RING = 8;
    private static final int FADE_TIMEOUT_TICKS = 40;
    private static final int MAX_RADIUS = 64;
    private static final int MAX_TRAIL_POINTS = 8;
    private static final float ARROW_HALF_LEN = 0.50F;
    private static final float TIP_EXTEND = 0.10F;
    private static final float FEATHER_HALF = 0.10F;
    private static final float[] COL_HEAD = {1.00F, 1.00F, 1.00F};   // TNL884 \u2605\u767d \u2713
    private static final float[] COL_MID = {0.62F, 0.62F, 0.62F};    // TNL884 \u2605\u4e2d\u7070 \u2713
    private static final float[] COL_TAIL = {0.22F, 0.22F, 0.22F};  // TNL884 \u2605\u8fd1\u9ed1 \u2713
    // TNL1019 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u5f3a\u5316\u7bad\u77e2\u7684\u5c3e\u75d5\u989c\u8272\u6362\u6210\u7ea2\u8272" \u2713
    private static final float[] COL_MID_RED = {0.95F, 0.18F, 0.16F};
    private static final float[] COL_TAIL_RED = {0.45F, 0.04F, 0.04F};
    /** \u2605\u672c\u6b21\u6e32\u67d3\u7684\u7bad\u662f\u4e0d\u662f"\u5f3a\u5316\u7bad"\uff08\u2605\u7528\u7ea2\u8272\u5c3e\u75d5 \u2713\uff09 */
    private static boolean ENHANCED_ARROW;
    private static final Map<UUID, FadeData> FADING = new HashMap<>();

    private static class FadeData {
        final LinkedList<Vec3> positions;
        long lastUpdateTick;

        FadeData(LinkedList<Vec3> src, long tick) {
            this.positions = new LinkedList<>(src);
            this.lastUpdateTick = tick;
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        long gameTime = mc.level.getGameTime();
        Matrix4f matrix = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        FADING.entrySet().removeIf(e -> (gameTime - e.getValue().lastUpdateTick) > FADE_TIMEOUT_TICKS);
        Set<UUID> active = new HashSet<>();
        for (StarfallArrow arrow : mc.level.getEntitiesOfClass(StarfallArrow.class,
                mc.player.getBoundingBox().inflate(MAX_RADIUS))) {
            if (!arrow.shouldRenderTail()) {
                continue;
            }
            UUID id = arrow.getUUID();
            active.add(id);
            LinkedList<Vec3> hist = arrow.getTrailHistory();
            if (hist.size() >= 2) {
                FADING.put(id, new FadeData(hist, gameTime));
            }
            renderArrow(arrow, matrix, camPos, partialTick, buffer);
        }
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, FadeData> e : FADING.entrySet()) {
            if (active.contains(e.getKey())) {
                continue;
            }
            LinkedList<Vec3> pts = e.getValue().positions;
            if (!pts.isEmpty()) {
                pts.removeFirst();
            }
            if (!pts.isEmpty()) {
                pts.removeFirst();
            }
            if (pts.isEmpty()) {
                toRemove.add(e.getKey());
                continue;
            }
            renderFade(buffer, matrix, camPos, pts);
        }
        for (UUID id : toRemove) {
            FADING.remove(id);
        }
        StarfallHitCache.prune(gameTime);
        for (StarfallHitCache.Hit hit : StarfallHitCache.get()) {
            renderHitRing(buffer, matrix, camPos, hit, gameTime);
            renderSixStar(buffer, matrix, camPos, hit, gameTime);   // TNL885 \u2605\u516d\u8292\u661f \u2713
        }
    }

    private static void renderArrow(StarfallArrow arrow, Matrix4f matrix, Vec3 camPos,
                                    float partialTick, MultiBufferSource.BufferSource buffer) {
        LinkedList<Vec3> hist = arrow.getTrailHistory();
        if (hist.size() < 2) {
            return;
        }
        // TNL1019 \u2605\u672c\u6b21\u662f\u4e0d\u662f\u5f3a\u5316\u7bad\uff08\u2605\u51b3\u5b9a\u5c3e\u75d5\u989c\u8272 \u2713\uff09
        ENHANCED_ARROW = arrow.tnl$isEnhanced();
        float sc = arrow.getArrowScale();
        float halfLen = ARROW_HALF_LEN * sc;
        float tipExtend = TIP_EXTEND * sc;
        float featherHalf = FEATHER_HALF * sc;
        Vec3 arrowPos = arrow.getPosition(partialTick);
        Vec3 prevPos = hist.get(Math.max(0, hist.size() - 2));
        Vec3 localDir = arrowPos.subtract(prevPos);
        if (localDir.lengthSqr() < 1e-6) {
            localDir = arrow.getDeltaMovement();
        }
        if (localDir.lengthSqr() < 1e-8) {
            return;
        }
        localDir = localDir.normalize();
        Vec3 tipPos = arrowPos.add(localDir.scale(halfLen + tipExtend));
        Vec3 featherPos = arrowPos.subtract(localDir.scale(halfLen));
        List<Vec3> path = new ArrayList<>();
        path.add(tipPos);
        path.add(featherPos);
        Vec3 prev = featherPos;
        int taken = 0;
        for (int i = hist.size() - 2; i >= 0 && taken < MAX_TRAIL_POINTS; i--) {
            Vec3 p = hist.get(i);
            if (p.subtract(featherPos).dot(localDir) > -0.05D) {
                continue;
            }
            if (prev.distanceToSqr(p) < 0.01D) {
                continue;
            }
            path.add(p);
            prev = p;
            taken++;
        }
        if (path.size() < 3) {
            return;
        }
        int n = path.size();
        float[] radius = new float[n];
        radius[0] = 0.0F;
        radius[1] = featherHalf;
        int tailSegs = n - 2;
        for (int i = 2; i < n; i++) {
            float t = (float) (i - 1) / tailSegs;
            radius[i] = featherHalf * (float) Math.pow(1.0F - t, 1.2D);
        }
        renderTube(buffer, matrix, camPos, path, radius, n);
    }

    private static void renderFade(MultiBufferSource.BufferSource buffer, Matrix4f matrix, Vec3 camPos,
                                   LinkedList<Vec3> historyOldToNew) {
        int hn = historyOldToNew.size();
        if (hn < 2) {
            return;
        }
        List<Vec3> path = new ArrayList<>(hn);
        for (int i = hn - 1; i >= 0; i--) {
            path.add(historyOldToNew.get(i));
        }
        int n = path.size();
        float[] radius = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / (n - 1);
            radius[i] = FEATHER_HALF * (float) Math.pow(1.0F - t, 1.2D);
        }
        renderTube(buffer, matrix, camPos, path, radius, n);
    }

    private static void renderTube(MultiBufferSource.BufferSource buffer, Matrix4f matrix, Vec3 camPos,
                                   List<Vec3> path, float[] radius, int n) {
        Vec3[] tangent = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 t;
            if (i == 0) {
                t = path.get(0).subtract(path.get(1));
            } else if (i == n - 1) {
                t = path.get(n - 2).subtract(path.get(n - 1));
            } else {
                t = path.get(i - 1).subtract(path.get(i + 1));
            }
            if (t.lengthSqr() < 1e-8) {
                t = new Vec3(0, 0, 1);
            }
            tangent[i] = t.normalize();
        }
        Vec3[] u = new Vec3[n];
        Vec3[] v = new Vec3[n];
        Vec3 t0 = tangent[0];
        Vec3 ref0 = Math.abs(t0.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        u[0] = t0.cross(ref0);
        if (u[0].lengthSqr() < 1e-8) {
            u[0] = t0.cross(new Vec3(1, 0, 0));
        }
        u[0] = u[0].normalize();
        v[0] = t0.cross(u[0]).normalize();
        for (int i = 1; i < n; i++) {
            Vec3 ti = tangent[i];
            Vec3 proj = u[i - 1].subtract(ti.scale(u[i - 1].dot(ti)));
            if (proj.lengthSqr() < 1e-8) {
                Vec3 ref = Math.abs(ti.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                proj = ti.cross(ref);
            }
            u[i] = proj.normalize();
            v[i] = ti.cross(u[i]).normalize();
        }
        Vec3[][] ring = new Vec3[n][RING];
        Vec3[] ringNormal = new Vec3[n * RING];
        Vec3[] toCam = new Vec3[n];
        for (int i = 0; i < n; i++) {
            float r = radius[i];
            Vec3 center = path.get(i);
            Vec3 tc = camPos.subtract(center);
            toCam[i] = tc.lengthSqr() > 1e-8 ? tc.normalize() : new Vec3(0, 1, 0);
            for (int j = 0; j < RING; j++) {
                double a = j * 2 * Math.PI / RING;
                ringNormal[i * RING + j] = u[i].scale(Math.cos(a)).add(v[i].scale(Math.sin(a))).normalize();
                ring[i][j] = center.add(u[i].scale(Math.cos(a) * r).add(v[i].scale(Math.sin(a) * r)));
            }
        }
        VertexConsumer b = buffer.getBuffer(StarfallRenderTypes.ARROW_TRAIL);
        for (int i = 0; i < n - 1; i++) {
            float p1 = (float) i / (n - 1);
            float p2 = (float) (i + 1) / (n - 1);
            float[] c1 = colorAt(p1);
            float[] c2 = colorAt(p2);
            float fade1 = axisFade(p1);
            float fade2 = axisFade(p2);
            for (int j = 0; j < RING; j++) {
                int j2 = (j + 1) % RING;
                emit(b, matrix, ring[i][j], camPos, c1, fade1 * ringFade(ringNormal[i * RING + j], toCam[i]));
                emit(b, matrix, ring[i][j2], camPos, c1, fade1 * ringFade(ringNormal[i * RING + j2], toCam[i]));
                emit(b, matrix, ring[i + 1][j2], camPos, c2, fade2 * ringFade(ringNormal[(i + 1) * RING + j2], toCam[i + 1]));
                emit(b, matrix, ring[i + 1][j], camPos, c2, fade2 * ringFade(ringNormal[(i + 1) * RING + j], toCam[i + 1]));
            }
        }
        buffer.endBatch(StarfallRenderTypes.ARROW_TRAIL);
    }

    private static void emit(VertexConsumer b, Matrix4f m, Vec3 p, Vec3 cam, float[] c, float a) {
        b.vertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .color(c[0], c[1], c[2], a)
                .endVertex();
    }

    private static float[] colorAt(float p) {
        float[] out = new float[3];
        if (ENHANCED_ARROW) {
            // \u2605\u5f3a\u5316\u7bad\uff1a\u2605\u7ea2\u8272\u5c3e\u75d5 \u2713
            if (p < 0.35F) {
                lerp3(COL_HEAD, COL_TAIL_RED, p / 0.35F, out);
            } else if (p < 0.60F) {
                lerp3(COL_TAIL_RED, COL_MID_RED, (p - 0.35F) / 0.25F, out);
            } else {
                out[0] = COL_MID_RED[0];
                out[1] = COL_MID_RED[1];
                out[2] = COL_MID_RED[2];
            }
            return out;
        }
        if (p < 0.35F) {
            lerp3(COL_HEAD, COL_TAIL, p / 0.35F, out);
        } else if (p < 0.60F) {
            lerp3(COL_TAIL, COL_MID, (p - 0.35F) / 0.25F, out);
        } else {
            out[0] = COL_MID[0];
            out[1] = COL_MID[1];
            out[2] = COL_MID[2];
        }
        return out;
    }

    private static void lerp3(float[] a, float[] b, float t, float[] out) {
        out[0] = a[0] + (b[0] - a[0]) * t;
        out[1] = a[1] + (b[1] - a[1]) * t;
        out[2] = a[2] + (b[2] - a[2]) * t;
    }

    private static float axisFade(float p) {
        return 0.55F - p * 0.05F;
    }

    private static float ringFade(Vec3 normal, Vec3 toCam) {
        double d = normal.dot(toCam);
        return 0.25F + 0.75F * (float) Math.max(0.0D, d);
    }

    private static void renderHitRing(MultiBufferSource.BufferSource buffer, Matrix4f matrix, Vec3 camPos,
                                      StarfallHitCache.Hit hit, long gameTime) {
        float t = hit.progress(gameTime);
        float R = 0.32F * (float) Math.pow(t, 0.55D);
        float halfW = 0.018F * (1.0F - t * 0.4F);
        float halfH = 0.008F;
        float a = t < 0.2F ? t / 0.2F : 1.0F - (t - 0.2F) / 0.8F;
        if (a <= 0.0F) {
            return;
        }
        float r = 1.00F * a;   // TNL884 \u2605\u767d\u7070\u661f\u73af \u2713
        float g = 1.00F * a;
        float b = 1.00F * a;
        Vec3 normal = hit.dir.lengthSqr() > 1e-6 ? hit.dir.normalize() : new Vec3(0, 1, 0);
        Vec3 ref = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = normal.cross(ref).normalize();
        Vec3 v = normal.cross(u).normalize();
        final int SEG = 32;
        Vec3[] inner = new Vec3[SEG];
        Vec3[] outer = new Vec3[SEG];
        for (int i = 0; i < SEG; i++) {
            double ang = i * 2 * Math.PI / SEG;
            Vec3 radial = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
            inner[i] = hit.pos.add(radial.scale(R - halfW)).add(normal.scale(halfH));
            outer[i] = hit.pos.add(radial.scale(R + halfW)).add(normal.scale(halfH));
        }
        VertexConsumer vc = buffer.getBuffer(StarfallRenderTypes.ARROW_TRAIL);
        float[] col = new float[]{r, g, b};
        for (int i = 0; i < SEG; i++) {
            int j = (i + 1) % SEG;
            emit(vc, matrix, inner[i], camPos, col, a);
            emit(vc, matrix, outer[i], camPos, col, a);
            emit(vc, matrix, outer[j], camPos, col, a);
            emit(vc, matrix, inner[j], camPos, col, a);
        }
        float R2 = R * 0.72F;
        float w2 = halfW * 0.35F;
        float[] col2 = new float[]{Math.min(1.0F, r * 1.4F), Math.min(1.0F, g * 1.2F), Math.min(1.0F, b * 1.05F)};
        for (int i = 0; i < SEG; i++) {
            double ang1 = i * 2 * Math.PI / SEG;
            double ang2 = (i + 1) * 2 * Math.PI / SEG;
            Vec3 rad1 = u.scale(Math.cos(ang1)).add(v.scale(Math.sin(ang1)));
            Vec3 rad2 = u.scale(Math.cos(ang2)).add(v.scale(Math.sin(ang2)));
            Vec3 i1 = hit.pos.add(rad1.scale(R2 - w2)).add(normal.scale(halfH));
            Vec3 o1 = hit.pos.add(rad1.scale(R2 + w2)).add(normal.scale(halfH));
            Vec3 i2 = hit.pos.add(rad2.scale(R2 - w2)).add(normal.scale(halfH));
            Vec3 o2 = hit.pos.add(rad2.scale(R2 + w2)).add(normal.scale(halfH));
            emit(vc, matrix, i1, camPos, col2, a);
            emit(vc, matrix, o1, camPos, col2, a);
            emit(vc, matrix, o2, camPos, col2, a);
            emit(vc, matrix, i2, camPos, col2, a);
        }
        buffer.endBatch(StarfallRenderTypes.ARROW_TRAIL);
    }

    /**
     * TNL885 \u2605\u2605 \u547d\u4e2d\u7279\u6548\uff1a**\u516d\u8292\u661f** \u2713\uff08\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff09
     *
     * <p>\u2605\u65e7\u5199\u6cd5\u662f\u4e94\u8292\u661f\uff08\u2605\u5355\u7b14\u753b\u7684\u4e94\u89d2\u661f \u2717\uff09\uff1b
     * \u2605\u516d\u8292\u661f = **\u4e24\u4e2a\u4ea4\u53e0\u7684\u7b49\u8fb9\u4e09\u89d2\u5f62** \u2713\uff1a
     * \u2605\u4e09\u89d2\u5f62 A = \u9876\u70b9 0/2/4 \u2713 \u2605\u4e09\u89d2\u5f62 B = \u9876\u70b9 1/3/5 \u2713
     * \uff08\u2605\u5171 6 \u4e2a\u9876\u70b9 \u00d7 60\u00b0 \u2713\uff09\u3002
     */
    private static void renderSixStar(MultiBufferSource.BufferSource buffer, Matrix4f matrix, Vec3 camPos,
                                      StarfallHitCache.Hit hit, long gameTime) {
        long age = gameTime - hit.startTick;
        final int LIFE = 8;          // \u26050.4 \u79d2 \u2713
        if (age > LIFE) {
            return;
        }
        float t = age / (float) LIFE;
        float alpha = 1.0F - t;
        if (alpha <= 0.0F) {
            return;
        }
        float R = 0.25F + t * 0.30F;
        float phase = (float) (Math.PI / 2) - t * (float) Math.PI * 0.5F;
        float colorT = (float) ((Math.sin(t * Math.PI * 2) + 1) * 0.5);
        Vec3 toCam = camPos.subtract(hit.pos);
        Vec3 normal = toCam.lengthSqr() > 1e-8 ? toCam.normalize() : new Vec3(0, 1, 0);
        Vec3 ref = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = normal.cross(ref).normalize();
        Vec3 v = normal.cross(u).normalize();
        Vec3[] verts = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double ang = phase + i * 2 * Math.PI / 6;      // \u2605\u6bcf 60\u00b0 \u4e00\u4e2a\u9876\u70b9 \u2713
            verts[i] = hit.pos.add(u.scale(Math.cos(ang) * R).add(v.scale(Math.sin(ang) * R)));
        }
        final int SUB = 16;
        for (int tri = 0; tri < 2; tri++) {                // \u2605\u4e24\u4e2a\u4e09\u89d2\u5f62 \u2713
            List<Vec3> path = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                Vec3 a = verts[(tri + k * 2) % 6];
                Vec3 b = verts[(tri + (k + 1) * 2) % 6];
                for (int j = 0; j < SUB; j++) {
                    path.add(a.lerp(b, (float) j / SUB));
                }
            }
            path.add(verts[tri]);                          // \u2605\u95ed\u5408 \u2713
            renderStarTube(buffer, matrix, camPos, path, 0.020F, alpha, colorT);
        }
    }


    private static void renderStarTube(MultiBufferSource.BufferSource buffer, Matrix4f matrix, Vec3 camPos,
                                       List<Vec3> path, float radius, float alpha, float colorT) {
        int n = path.size();
        if (n < 2) {
            return;
        }
        Vec3[] tangent = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 t;
            if (i == 0) {
                t = path.get(1).subtract(path.get(0));
            } else if (i == n - 1) {
                t = path.get(n - 1).subtract(path.get(n - 2));
            } else {
                t = path.get(i + 1).subtract(path.get(i - 1));
            }
            if (t.lengthSqr() < 1e-8) {
                t = new Vec3(0, 0, 1);
            }
            tangent[i] = t.normalize();
        }
        Vec3[] u = new Vec3[n];
        Vec3[] v = new Vec3[n];
        Vec3 t0 = tangent[0];
        Vec3 ref0 = Math.abs(t0.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        u[0] = t0.cross(ref0);
        if (u[0].lengthSqr() < 1e-8) {
            u[0] = t0.cross(new Vec3(1, 0, 0));
        }
        u[0] = u[0].normalize();
        v[0] = t0.cross(u[0]).normalize();
        for (int i = 1; i < n; i++) {
            Vec3 ti = tangent[i];
            Vec3 proj = u[i - 1].subtract(ti.scale(u[i - 1].dot(ti)));
            if (proj.lengthSqr() < 1e-8) {
                Vec3 ref = Math.abs(ti.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                proj = ti.cross(ref);
            }
            u[i] = proj.normalize();
            v[i] = ti.cross(u[i]).normalize();
        }
        Vec3[][] ring = new Vec3[n][RING];
        Vec3[] ringNormal = new Vec3[n * RING];
        Vec3[] toCam = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 center = path.get(i);
            Vec3 tc = camPos.subtract(center);
            toCam[i] = tc.lengthSqr() > 1e-8 ? tc.normalize() : new Vec3(0, 1, 0);
            for (int j = 0; j < RING; j++) {
                double a = j * 2 * Math.PI / RING;
                ringNormal[i * RING + j] = u[i].scale(Math.cos(a)).add(v[i].scale(Math.sin(a))).normalize();
                ring[i][j] = center.add(u[i].scale(Math.cos(a) * radius).add(v[i].scale(Math.sin(a) * radius)));
            }
        }
        VertexConsumer vc = buffer.getBuffer(StarfallRenderTypes.ARROW_TRAIL);
        float[] colBlue = {1.00F, 1.00F, 1.00F};      // TNL884 \u2605\u767d \u2713
        float[] colPink = {0.30F, 0.30F, 0.30F};      // TNL884 \u2605\u6df1\u7070 \u2713
        float[] col = {
                colBlue[0] + (colPink[0] - colBlue[0]) * colorT,
                colBlue[1] + (colPink[1] - colBlue[1]) * colorT,
                colBlue[2] + (colPink[2] - colBlue[2]) * colorT
        };
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < RING; j++) {
                int j2 = (j + 1) % RING;
                emit(vc, matrix, ring[i][j], camPos, col, alpha * ringFade(ringNormal[i * RING + j], toCam[i]));
                emit(vc, matrix, ring[i][j2], camPos, col, alpha * ringFade(ringNormal[i * RING + j2], toCam[i]));
                emit(vc, matrix, ring[i + 1][j2], camPos, col, alpha * ringFade(ringNormal[(i + 1) * RING + j2], toCam[i + 1]));
                emit(vc, matrix, ring[i + 1][j], camPos, col, alpha * ringFade(ringNormal[(i + 1) * RING + j], toCam[i + 1]));
            }
        }
        buffer.endBatch(StarfallRenderTypes.ARROW_TRAIL);
    }
}
