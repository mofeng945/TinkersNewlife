package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.PixieTreasureModifier.Glow;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/** Per-viewer outlines; never modify entity flags, potion state, chunk data, or block entities. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class PixieTreasureOutline {
    private static ClientLevel world;
    private static final Map<BlockPos, Long> blocks = new HashMap<>();
    private static final Map<Integer, Long> entities = new HashMap<>();

    public static void receive(Glow pulse) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(pulse.dimension())) return;
        if (world != level) { clear(); world = level; }
        long until = level.getGameTime() + 60;
        for (long pos : pulse.positions()) blocks.put(BlockPos.of(pos), until);
        for (int id : pulse.entities()) entities.put(id, until);
    }

    private static void clear() { blocks.clear(); entities.clear(); world = null; }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level && level == world) clear();
    }

    private static final class Lines extends RenderStateShard {
        private Lines() { super("pixie_lines", () -> {}, () -> {}); }
        static final RenderType TYPE = RenderType.create("tnl_pixie_treasure_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 256, false, false, RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.of(2)))
                        .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || world == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { clear(); return; }
        long now = world.getGameTime();
        blocks.values().removeIf(until -> until <= now);
        entities.values().removeIf(until -> until <= now);
        if (blocks.isEmpty() && entities.isEmpty()) return;
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            var lines = buffers.getBuffer(Lines.TYPE);
            for (BlockPos pos : blocks.keySet()) {
                if (world.hasChunkAt(pos) && world.getBlockState(pos).hasBlockEntity()) {
                    LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(.003), 1f, .75f, .92f, 1f);
                }
            }
            for (int id : entities.keySet()) {
                Entity entity = world.getEntity(id);
                if (entity == null || !entity.isAlive()) continue;
                AABB box = entity.getBoundingBox().move(entity.getPosition(event.getPartialTick()).subtract(entity.position()));
                LevelRenderer.renderLineBox(pose, lines, box.inflate(.025), 1f, .75f, .92f, 1f);
            }
            // Vanilla's NO_DEPTH_TEST has an empty setup; the world pass may still have depth enabled.
            boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            try { buffers.endBatch(Lines.TYPE); }
            finally { if (depth) com.mojang.blaze3d.systems.RenderSystem.enableDepthTest(); }
        } finally { pose.popPose(); }
    }
}
