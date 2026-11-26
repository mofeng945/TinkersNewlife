package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only directed beam segments. The server supplies one main segment and up to five branches. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class WitherGazeRenderer {
    private static final Map<Edge, Beam> BEAMS = new HashMap<>();
    private static final int TRACKING_GRACE = 3;
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

            return false;
        });
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || currentLevel == null || currentLevel != Minecraft.getInstance().level || BEAMS.isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        for (var entry : BEAMS.entrySet()) {
            Edge edge = entry.getKey();
            Beam beam = entry.getValue();
            Entity from = currentLevel.getEntity(edge.source());
            Entity to = currentLevel.getEntity(edge.target());
            if (from == null || to == null || !beam.matches(from, to)) continue;
            beam.sourceId = from.getUUID();
            beam.targetId = to.getUUID();
            Vec3 start = from.getPosition(partial);
            Vec3 end = to.getPosition(partial).add(0, to.getBbHeight() * 0.5, 0);
            if (!event.getFrustum().isVisible(new AABB(start.add(0, from.getEyeHeight(), 0), end).inflate(0.5))) continue;
            pose.pushPose();
            try {
                Vec3 offset = start.subtract(camera);
                pose.translate(offset.x, offset.y, offset.z);
                IceAndFireBeam.render(from, to, pose, buffers, partial);
            } finally {
                pose.popPose();
            }
        }
        IceAndFireBeam.flush(buffers);
    }

    // The optional mod owns both the geometry and texture. Resolve once without a hard dependency.
    private static final class IceAndFireBeam {
        private static java.lang.reflect.Method render;
        private static net.minecraft.client.renderer.RenderType layer;
        private static boolean resolved;

        private static void render(Entity from, Entity to, com.mojang.blaze3d.vertex.PoseStack pose,
                                   net.minecraft.client.renderer.MultiBufferSource buffers, float partial) {
            try {
                if (!resolved) {
                    resolved = true;
                    var type = Class.forName("com.github.alexthe666.iceandfire.client.particle.CockatriceBeamRender");
                    layer = (net.minecraft.client.renderer.RenderType) type.getField("TEXTURE_BEAM").get(null);
                    render = type.getMethod("render", Entity.class, Entity.class, com.mojang.blaze3d.vertex.PoseStack.class,
                            net.minecraft.client.renderer.MultiBufferSource.class, float.class);
                }
                if (render != null) render.invoke(null, from, to, pose, buffers, partial);
            } catch (ReflectiveOperationException | LinkageError exception) {
                render = null;
                TinkersNewlife.LOGGER.warn("Could not render the Ice and Fire cockatrice beam", exception);
            }
        }

        private static void flush(net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers) {
            if (layer != null) buffers.endBatch(layer);
        }
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

}
