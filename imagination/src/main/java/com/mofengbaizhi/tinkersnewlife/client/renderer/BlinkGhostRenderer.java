package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/**
 * TNL1123 draws the ghosts that ThrustGhostCache collects.
 *
 * The cache half existed but nothing ever called alive(), so the blade dash afterimages never showed.
 * This renders the plain player model at each recorded point, fading out with its progress, which is
 * exactly what the user asked for: the whole line between the blink start and the arrival point.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlinkGhostRenderer {

    private static final ResourceLocation TEX =
            new ResourceLocation("tinkersnewlife", "textures/entity/ktulu_momo.png");
    private static HumanoidModel<?> model;

    private BlinkGhostRenderer() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        var ghosts = ThrustGhostCache.alive();
        if (ghosts.isEmpty()) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (model == null) {
            model = new HumanoidModel(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER));
        }
        var camera = event.getCamera();
        var pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        int now = mc.player == null ? -1 : mc.player.tickCount;
        for (ThrustGhostCache.Ghost g : ghosts) {
            float fade = 1.0F - g.progress(now);
            if (fade <= 0.05F) {
                continue;
            }
            pose.pushPose();
            pose.translate(g.x - camera.getPosition().x, g.y - camera.getPosition().y,
                    g.z - camera.getPosition().z);
            pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-g.yaw)));
            pose.scale(-1.0F, -1.0F, 1.0F);
            var vc = buffer.getBuffer(RenderType.entityTranslucent(TEX));
            model.renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY,
                    0.10F, 0.11F, 0.14F, 0.55F * fade);   // dark translucent ghost
            pose.popPose();
        }
        buffer.endBatch();
    }
}
