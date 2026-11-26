package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Client-only world geometry for the Star Dream channel, lens and laser. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StarDreamRenderer {
    private static final float CHANNEL_SECONDS = 10.0F;
    private static final float LASER_SECONDS = 5.0F;

    private StarDreamRenderer() {}

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        var buffer = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        boolean drew = false;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof KtuluMomo momo) || !momo.tnl$isStarDreamActive()) continue;
            if (momo.tnl$spell() != 7 || !momo.isAlive()) continue;
            float age = 300 - momo.tnl$castTicks() + event.getPartialTick();
            Vec3 center = momo.tnl$starDreamCenter();
            pose.pushPose();
            try {
                pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
                Matrix4f matrix = pose.last().pose();
                if (age < 200) {
                    float p = Mth.clamp(age / 200, 0, 1);
                    drawMagicCircle(matrix, buffer, momo.tickCount);
                    buffer.endBatch(RenderType.debugQuads());
                    pose.mulPose(event.getCamera().rotation());
                    StarDreamLens.draw(pose.last().pose(), 5 * smooth(p));
                    pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
                    drawBlackHole(pose.last().pose(), buffer, p, momo.tickCount);
                } else {
                    drawLaser(matrix, buffer, Mth.clamp((age - 200) / 100, 0, 1), momo.tickCount);
                }
            } finally {
                pose.popPose();
            }
            drew = true;
        }
        if (drew) buffer.endBatch(RenderType.debugQuads());
    }

    private static void drawBlackHole(Matrix4f matrix, MultiBufferSource.BufferSource buffer,
                                      float progress, int tick) {
        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
        float radius = 5.0F * smooth(progress);
        float lens = 1.0F - 0.5F * progress;
        for (int i = 0; i < 3; i++) {
            float ringRadius = radius * (0.94F + i * 0.1F);
            float wobble = 0.035F * Mth.sin(tick * 0.14F + i * 2.2F);
            circle(vc, matrix, ringRadius, 0.08F, 0.015F + wobble, 0.02F + wobble,
                    0.10F, 0.78F * lens, tick * (i % 2 == 0 ? 0.8F : -0.55F) + i * 31.0F, i == 1);
        }
        // Black disk plus an asymmetric, bright accretion rim suggests a lens rather than a flat ring.
        disc(vc, matrix, Math.max(0.02F, radius * 0.79F), 0.008F, 0.005F, 0.02F, 1.0F);
        for (int i = 0; i < 64; i++) {
            float a = (float) (Math.PI * 2.0 * i / 64.0) + tick * 0.025F;
            float r1 = radius * (0.84F + 0.025F * Mth.sin(a * 3.0F + tick * 0.05F));
            float r2 = radius * (0.94F + 0.05F * Mth.sin(a * 2.0F - tick * 0.07F));
            quad(vc, matrix, polar(r1, a, -0.025F), polar(r2, a + 0.035F, -0.008F),
                    polar(r2, a + 0.035F, 0.008F), polar(r1, a, 0.025F),
                    0.9F, 0.74F, 1.0F, 0.5F * lens);
        }
    }

    private static void drawMagicCircle(Matrix4f matrix, MultiBufferSource.BufferSource buffer, int tick) {
        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
        float y = 6.5F;
        float spin = tick * 0.8F;
        circleAtY(vc, matrix, 3.4F, y, 0.035F, 1.0F, 0.72F, 0.16F, 0.95F, spin * 0.4F, 96);
        circleAtY(vc, matrix, 2.7F, y + 0.025F, 0.026F, 1.0F, 0.86F, 0.35F, 0.9F, -spin * 0.55F, 72);
        circleAtY(vc, matrix, 1.75F, y + 0.04F, 0.022F, 1.0F, 0.93F, 0.55F, 0.85F, spin * 0.8F, 60);
        star(vc, matrix, 1.55F, y + 0.06F, 0.045F, spin * 1.4F);
        for (int i = 0; i < 6; i++) {
            float angle = (float) Math.toRadians(spin * 1.4F + i * 60.0F);
            float x = Mth.cos(angle) * 1.2F;
            float z = Mth.sin(angle) * 1.2F;
            circleAt(vc, matrix, x, y + 0.07F, z, 0.11F, 0.035F, 1.0F, 0.95F, 0.68F, 0.9F, tick * 2.0F + i * 60, 12);
        }
        for (int i = 0; i < 6; i++) {
            float angle = (float) Math.toRadians(spin * 0.45F + i * 60.0F);
            line(vc, matrix, new float[]{0, y + 0.08F, 0},
                    new float[]{Mth.cos(angle) * 1.75F, y + 0.08F, Mth.sin(angle) * 1.75F},
                    0.018F, 1.0F, 0.75F, 0.24F, 0.65F);
        }
    }

    private static void drawLaser(Matrix4f matrix, MultiBufferSource.BufferSource buffer, float progress, int tick) {
        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
        progress = Mth.clamp((progress - 0.04F) / 0.96F, 0.0F, 1.0F);
        float fade = progress < 0.08F ? progress / 0.08F
                : progress > 0.82F ? (1.0F - progress) / 0.18F : 1.0F;
        float radius = 5.0F * (progress < 0.82F
                ? smooth(Mth.clamp(progress / 0.16F, 0.0F, 1.0F))
                : smooth(Mth.clamp((1.0F - progress) / 0.18F, 0.0F, 1.0F)));
        float beamRadius = radius * (0.12F + 0.88F * fade);
        float innerRadius = beamRadius * 0.22F;
        float top = 48.0F;
        float bottom = -24.0F;
        int segments = 64;
        float angle0 = tick * 0.025F;
        for (int i = 0; i < segments; i++) {
            float a0 = angle0 + (float) (Math.PI * 2.0 * i / segments);
            float a1 = angle0 + (float) (Math.PI * 2.0 * (i + 1) / segments);
            float x0 = Mth.cos(a0), z0 = Mth.sin(a0);
            float x1 = Mth.cos(a1), z1 = Mth.sin(a1);
            float edgeAlpha = 0.16F + 0.18F * (0.5F + 0.5F * Mth.sin(i * 1.7F + tick * 0.08F));
            quad(vc, matrix, new float[]{x0 * beamRadius, top, z0 * beamRadius},
                    new float[]{x1 * beamRadius, top, z1 * beamRadius},
                    new float[]{x1 * beamRadius, bottom, z1 * beamRadius},
                    new float[]{x0 * beamRadius, bottom, z0 * beamRadius},
                    1.0F, 0.72F, 0.16F, edgeAlpha * fade);
            quad(vc, matrix, new float[]{x0 * innerRadius, top, z0 * innerRadius},
                    new float[]{x1 * innerRadius, top, z1 * innerRadius},
                    new float[]{x1 * innerRadius, bottom, z1 * innerRadius},
                    new float[]{x0 * innerRadius, bottom, z0 * innerRadius},
                    1.0F, 0.98F, 0.78F, 0.12F * fade);
        }
        circleAtY(vc, matrix, radius, -0.02F, 0.06F, 1.0F, 0.75F, 0.22F, fade * 0.8F, tick, 64);
    }

    private static void disc(VertexConsumer vc, Matrix4f m, float radius,
                             float red, float green, float blue, float alpha) {
        float[] center = new float[]{0.0F, -0.04F, 0.0F};
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / segments);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / segments);
            quad(vc, m, center, new float[]{Mth.cos(a0) * radius, -0.04F, Mth.sin(a0) * radius},
                    new float[]{Mth.cos(a1) * radius, -0.04F, Mth.sin(a1) * radius}, center,
                    red, green, blue, alpha);
        }
    }

    private static void star(VertexConsumer vc, Matrix4f m, float r, float y, float width, float spin) {
        float[] p = new float[6];
        for (int i = 0; i < 6; i++) {
            float a = (float) Math.toRadians(spin + i * 60.0F);
            p[i] = a;
        }
        for (int i = 0; i < 6; i++) {
            int j = (i + 2) % 6;
            line(vc, m, polar3(r, p[i], y), polar3(r, p[j], y), width,
                    1.0F, 0.88F, 0.4F, 0.95F);
        }
    }

    private static void circle(VertexConsumer vc, Matrix4f m, float radius, float width,
                               float red, float green, float blue, float alpha, float spin, boolean warped) {
        int segments = 96;
        float prev = spin;
        for (int i = 1; i <= segments; i++) {
            float angle = spin + (float) (Math.PI * 2.0 * i / segments);
            float r1 = warped ? radius * (1.0F + 0.035F * Mth.sin(prev * 3.0F)) : radius;
            float r2 = warped ? radius * (1.0F + 0.035F * Mth.sin(angle * 3.0F)) : radius;
            line(vc, m, polar3(r1, prev, -0.018F), polar3(r2, angle, -0.018F), width, red, green, blue, alpha);
            prev = angle;
        }
    }

    private static void circleAtY(VertexConsumer vc, Matrix4f m, float radius, float y, float width,
                                  float red, float green, float blue, float alpha, float spin, int segments) {
        float previous = spin;
        for (int i = 1; i <= segments; i++) {
            float next = spin + (float) (Math.PI * 2.0 * i / segments);
            line(vc, m, polar3(radius, previous, y), polar3(radius, next, y), width, red, green, blue, alpha);
            previous = next;
        }
    }

    private static void circleAt(VertexConsumer vc, Matrix4f m, float x, float y, float z, float radius,
                                 float width, float red, float green, float blue, float alpha,
                                 float spin, int segments) {
        float previous = spin;
        for (int i = 1; i <= segments; i++) {
            float next = spin + (float) (Math.PI * 2.0 * i / segments);
            line(vc, m, new float[]{x + Mth.cos(previous) * radius, y, z + Mth.sin(previous) * radius},
                    new float[]{x + Mth.cos(next) * radius, y, z + Mth.sin(next) * radius},
                    width, red, green, blue, alpha);
            previous = next;
        }
    }

    private static float[] polar(float r, float a, float y) {
        return new float[]{Mth.cos(a) * r, y, Mth.sin(a) * r};
    }

    private static float[] polar3(float r, float a, float y) {
        return new float[]{Mth.cos(a) * r, y, Mth.sin(a) * r};
    }

    private static void line(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float width,
                             float red, float green, float blue, float alpha) {
        float dx = b[0] - a[0];
        float dz = b[2] - a[2];
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4F) return;
        float nx = -dz / len * width;
        float nz = dx / len * width;
        quad(vc, m, new float[]{a[0] + nx, a[1], a[2] + nz},
                new float[]{b[0] + nx, b[1], b[2] + nz},
                new float[]{b[0] - nx, b[1], b[2] - nz},
                new float[]{a[0] - nx, a[1], a[2] - nz}, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d,
                             float red, float green, float blue, float alpha) {
        vertex(vc, m, a, red, green, blue, alpha);
        vertex(vc, m, b, red, green, blue, alpha);
        vertex(vc, m, c, red, green, blue, alpha);
        vertex(vc, m, d, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float[] p,
                               float red, float green, float blue, float alpha) {
        vc.vertex(m, p[0], p[1], p[2]).color(red, green, blue, alpha).endVertex();
    }

    private static float smooth(float t) { return t * t * (3.0F - 2.0F * t); }
}
