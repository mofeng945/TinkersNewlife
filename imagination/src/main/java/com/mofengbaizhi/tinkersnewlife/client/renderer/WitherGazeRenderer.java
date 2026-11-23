package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only directed beam segments. The server supplies one main segment and up to five branches. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class WitherGazeRenderer {
    private static final Map<Edge, Beam> BEAMS = new HashMap<>();
    private static final int TRACKING_GRACE = 3;
    private static final DustParticleOptions MOTE = new DustParticleOptions(new Vector3f(0.62F, 0.31F, 0.52F), 0.75F);
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(2048));
    private static ClientLevel currentLevel;
    private static long ticks;

    private WitherGazeRenderer() {}

    /** Add or refresh one directed segment for the given number of client ticks. Zero removes it. */
    public static void update(int source, int target, int duration) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> update(source, target, duration));
            return;
        }
        useLevel(mc.level);
        if (currentLevel == null || source == target) return;

        Edge edge = new Edge(source, target);
        if (duration <= 0) {
            BEAMS.remove(edge);
            return;
        }
        Entity from = currentLevel.getEntity(source);
        Entity to = currentLevel.getEntity(target);
        if ((from != null && !usable(from)) || (to != null && !usable(to))) {
            BEAMS.remove(edge);
            return;
        }
        Beam beam = BEAMS.get(edge);
        if (beam == null || !beam.matches(from, to)) {
            beam = new Beam(ticks, from, to);
            BEAMS.put(edge, beam);
        }
        beam.expires = ticks + duration;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        useLevel(mc.level);
        if (currentLevel == null || mc.isPaused()) return;
        ticks++;
        BEAMS.entrySet().removeIf(entry -> {
            Edge edge = entry.getKey();
            Beam beam = entry.getValue();
            if (ticks >= beam.expires) return true;
            Entity from = currentLevel.getEntity(edge.source());
            Entity to = currentLevel.getEntity(edge.target());
            if (!beam.matches(from, to)) return true;
            if (from != null) beam.sourceId = from.getUUID();
            if (to != null) beam.targetId = to.getUUID();
            if (from == null || to == null) return ticks - beam.started >= TRACKING_GRACE;

            if ((ticks + edge.source() + edge.target()) % 3 == 0) {
                double progress = (ticks % 24L) / 24.0D;
                Vec3 point = anchor(from, 1.0F).lerp(anchor(to, 1.0F), progress);
                currentLevel.addParticle(MOTE, point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || currentLevel == null || currentLevel != Minecraft.getInstance().level || BEAMS.isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        Matrix4f pose = event.getPoseStack().last().pose();
        VertexConsumer vertices = BUFFER.getBuffer(BeamLayer.TYPE);
        for (Map.Entry<Edge, Beam> entry : BEAMS.entrySet()) {
            Edge edge = entry.getKey();
            Beam beam = entry.getValue();
            Entity from = currentLevel.getEntity(edge.source());
            Entity to = currentLevel.getEntity(edge.target());
            if (from == null || to == null || !beam.matches(from, to)) continue;
            beam.sourceId = from.getUUID();
            beam.targetId = to.getUUID();
            Vec3 start = anchor(from, partial);
            Vec3 end = anchor(to, partial);
            if (!event.getFrustum().isVisible(new AABB(start, end).inflate(0.5D))) continue;

            boolean firstPerson = from == event.getCamera().getEntity()
                    && Minecraft.getInstance().options.getCameraType().isFirstPerson();
            if (firstPerson) {
                // Keep the near clip plane from cutting straight through the eye-level beam origin.
                start = camera.add(from.getViewVector(partial).scale(0.22D)).add(0.0D, -0.095D, 0.0D);
            }
            drawBeam(vertices, pose, start.subtract(camera), end.subtract(camera), from instanceof Player,
                    ticks + partial, 0.31D * edge.source() + 0.19D * edge.target());
        }
        BUFFER.endBatch(BeamLayer.TYPE);
    }

    private static Vec3 anchor(Entity entity, float partial) {
        return entity.getEyePosition(partial).add(0.0D, entity instanceof Player ? -0.06D : -0.12D, 0.0D);
    }

    private static void drawBeam(VertexConsumer out, Matrix4f pose, Vec3 start, Vec3 end,
                                 boolean fromPlayer, double time, double phase) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.025D) return;
        Vec3 along = delta.scale(1.0D / length);
        Vec3 side = along.cross(start.lerp(end, 0.5D)).normalize();
        if (side.lengthSqr() < 0.5D) {
            side = along.cross(Math.abs(along.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        }
        int segments = Math.min(48, Math.max(5, (int) Math.ceil(length * 3.0D)));
        double width = fromPlayer ? 0.125D : 0.087D;
        Vec3 previous = start;
        double previousPulse = 0.62D;
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            double envelope = Math.sin(t * Math.PI);
            double wave = phase + 9.0D * t - time * 0.28D;
            Vec3 next = start.lerp(end, t).add(side.scale(Math.sin(wave) * envelope * width * 0.48D));
            double pulse = 0.58D + 0.42D * Math.pow(0.5D + 0.5D * Math.sin(wave * 1.8D), 3.0D);
            Vec3 outer = side.scale(width);
            Vec3 core = side.scale(width * 0.32D);
            // Transparent edges frame a dark plum center with a pale pulse traveling toward the target.
            strip(out, pose, previous, next, outer, core, 0x452637, 0, 105, 0, 105);
            strip(out, pose, previous, next, core.scale(-1), outer.scale(-1), 0x452637, 105, 0, 105, 0);
            strip(out, pose, previous, next, core, Vec3.ZERO, 0xC787AE, 95, (int) (240 * previousPulse), 95, (int) (240 * pulse));
            strip(out, pose, previous, next, Vec3.ZERO, core.scale(-1), 0xC787AE, (int) (240 * previousPulse), 95, (int) (240 * pulse), 95);
            previous = next;
            previousPulse = pulse;
        }
    }

    private static void strip(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, Vec3 left, Vec3 right,
                              int color, int alphaALeft, int alphaARight, int alphaBLeft, int alphaBRight) {
        vertex(out, pose, a, left, color, alphaALeft);
        vertex(out, pose, a, right, color, alphaARight);
        vertex(out, pose, b, right, color, alphaBRight);
        vertex(out, pose, b, left, color, alphaBLeft);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 offset, int rgb, int alpha) {
        out.vertex(pose, (float) (center.x + offset.x), (float) (center.y + offset.y), (float) (center.z + offset.z))
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha).endVertex();
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() != currentLevel) return;
        int id = event.getEntity().getId();
        BEAMS.keySet().removeIf(edge -> edge.source() == id || edge.target() == id);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == currentLevel) useLevel(null);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        useLevel(null);
    }

    private static void useLevel(ClientLevel level) {
        if (currentLevel != level) {
            BEAMS.clear();
            currentLevel = level;
            ticks = 0;
        }
    }

    private static boolean usable(Entity entity) {
        return entity.isAlive() && !entity.isRemoved();
    }

    private record Edge(int source, int target) {}

    private static final class Beam {
        private final long started;
        private long expires;
        private UUID sourceId;
        private UUID targetId;

        private Beam(long started, Entity from, Entity to) {
            this.started = started;
            sourceId = from == null ? null : from.getUUID();
            targetId = to == null ? null : to.getUUID();
        }

        private boolean matches(Entity from, Entity to) {
            return matches(sourceId, from) && matches(targetId, to);
        }

        private static boolean matches(UUID id, Entity entity) {
            if (entity == null) return id == null;
            return usable(entity) && (id == null || id.equals(entity.getUUID()));
        }
    }

    private static final class BeamLayer extends RenderType {
        private static final RenderType TYPE = create("tinkersnewlife_wither_gaze", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 2048, false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private BeamLayer() {
            super("tinkersnewlife_wither_gaze", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 2048, false, true, () -> {}, () -> {});
        }
    }
}
