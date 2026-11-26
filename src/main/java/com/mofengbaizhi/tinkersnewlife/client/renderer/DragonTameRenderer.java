package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.DragonTameVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Render-only scene state; no changes to the dragon, staff or taming state machine. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class DragonTameRenderer {
    private static final int FADE_TICKS = 24;
    private static final int ACTIVE_TIMEOUT = 20 * 120;
    private static final Map<UUID, Scene> SCENES = new HashMap<>();
    private static ClientLevel world;

    private record Scene(DragonTameVisual visual, long began) {}

    private DragonTameRenderer() {}

    public static void receive(DragonTameVisual visual) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(visual.dimension())) return;
        if (world != level) { SCENES.clear(); world = level; }
        SCENES.put(visual.dragonUuid(), new Scene(visual, level.getGameTime()));
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == world) { world = null; SCENES.clear(); }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SCENES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (world == null || world != mc.level) { world = null; SCENES.clear(); return; }
        long tick = world.getGameTime();
        float partial = event.getPartialTick();
        SCENES.values().removeIf(s -> tick - s.began >= (s.visual.phase() == DragonTameVisual.ACTIVE ? ACTIVE_TIMEOUT : FADE_TICKS));
        if (SCENES.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        for (Scene scene : SCENES.values()) {
            var visual = scene.visual;
            var dragon = resolve(visual.dragonId(), visual.dragonUuid());
            boolean active = visual.phase() == DragonTameVisual.ACTIVE;
            if (active && (dragon == null || !dragon.isAlive())) continue;
            Vec3 feet = visual.feet();
            if (feet.distanceToSqr(camera) > 112 * 112) continue;
            if (active) {
                Entity player = resolve(visual.playerId(), visual.playerUuid());
                if (player != null) {
                    Vec3 crystal = player.getEyePosition(partial).add(player.getViewVector(partial).scale(.35));
                    // Vanilla offsets the entire beam two blocks above the supplied pose origin.
                    Vec3 beamBase = crystal.add(0, -2, 0);
                    Vec3 delta = dragon.getPosition(partial).add(0, dragon.getBbHeight() * .55, 0).subtract(crystal);
                    pose.pushPose();
                    try {
                        pose.translate(beamBase.x - camera.x, beamBase.y - camera.y, beamBase.z - camera.z);
                        EnderDragonRenderer.renderCrystalBeams((float)delta.x, (float)delta.y, (float)delta.z,
                                partial, (int)tick, pose, buffers, LightTexture.FULL_BRIGHT);
                    } finally { pose.popPose(); }
                }
            }
            float fade = active ? .9f : Math.max(0, 1f - (tick - scene.began + partial) / FADE_TICKS);
            pose.pushPose();
            try {
                pose.translate(feet.x - camera.x, feet.y - camera.y + .08, feet.z - camera.z);
                float r = visual.phase() == DragonTameVisual.FAILURE ? 1f : .36f;
                float g = visual.phase() == DragonTameVisual.SUCCESS ? 1f : .41f;
                float b = active ? 1f : .32f;
                var lines = buffers.getBuffer(RenderType.lines());
                double radius = visual.radius() * (active ? 1 : 1 + .28 * (1 - fade));
                ring(lines, pose.last(), radius, r, g, b, fade);
                if (active) {
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3, c = a + 2 * Math.PI / 3;
                        line(lines, pose.last(), radius * .88 * Math.cos(a), radius * .88 * Math.sin(a),
                                radius * .88 * Math.cos(c), radius * .88 * Math.sin(c), r, g, b, fade);
                    }
                }
            } finally { pose.popPose(); }
        }
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(RenderType.entitySmoothCutout(new net.minecraft.resources.ResourceLocation("textures/entity/end_crystal/end_crystal_beam.png")));
    }

    private static Entity resolve(int id, UUID uuid) {
        Entity entity = world.getEntity(id);
        return entity != null && uuid.equals(entity.getUUID()) ? entity : null;
    }

    private static void ring(VertexConsumer lines, PoseStack.Pose pose, double radius, float r, float g, float b, float alpha) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24, c = (i + 1) * Math.PI / 24;
            line(lines, pose, radius * Math.cos(a), radius * Math.sin(a), radius * Math.cos(c), radius * Math.sin(c), r, g, b, alpha);
        }
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, double x1, double z1, double x2, double z2,
                             float r, float g, float b, float alpha) {
        float dx = (float)(x2 - x1), dz = (float)(z2 - z1);
        float length = (float)Math.hypot(dx, dz);
        if (length < .0001f) return;
        lines.vertex(pose.pose(), (float)x1, 0, (float)z1).color(r, g, b, alpha)
                .normal(pose.normal(), dx / length, 0, dz / length).endVertex();
        lines.vertex(pose.pose(), (float)x2, 0, (float)z2).color(r, g, b, alpha)
                .normal(pose.normal(), dx / length, 0, dz / length).endVertex();
    }
}
