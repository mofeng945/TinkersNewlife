package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.lang.reflect.Method;

/** Steer the existing same-level world pass; never teleport the player or render a second level. */
@Mod.EventBusSubscriber(modid = "tinkersnewlife", value = Dist.CLIENT)
public final class DragonWarCamera {
    static final double FOV = 60.0;

    private DragonWarCamera() {}

    private static class CameraAccess {
        // Camera#setPosition(double,double,double), protected in 1.20.1 Mojmap.
        private static final Method POSITION = ObfuscationReflectionHelper.findMethod(
                Camera.class, "m_90584_", double.class, double.class, double.class);
    }

    private static DragonWarScreen current() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.screen instanceof DragonWarScreen screen ? screen : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fieldOfView(ViewportEvent.ComputeFov event) {
        DragonWarScreen screen = current();
        if (screen != null) event.setFOV(screen.cameraFov());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        DragonWarScreen screen = current();
        if (screen == null) return;
        Vec3 eye = screen.cameraPosition();
        try {
            CameraAccess.POSITION.invoke(event.getCamera(), eye.x, eye.y, eye.z);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("Unable to position dragon war camera", failure);
            screen.onClose();
            return;
        }
        // Yaw 180 + pitch 90: north is screen up, east is screen right.
        event.setYaw(180);
        event.setPitch(90);
        event.setRoll(0);
    }

    @SubscribeEvent
    public static void capture(RenderLevelStageEvent event) {
        DragonWarScreen screen = current();
        if (screen == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Camera camera = event.getCamera();
        var window = Minecraft.getInstance().getWindow();
        Matrix4f worldToClip = new Matrix4f(event.getProjectionMatrix())
                .rotate(Axis.XP.rotationDegrees(camera.getXRot()))
                .rotate(Axis.YP.rotationDegrees(camera.getYRot() + 180));
        screen.setFrame(new Frame(camera.getPosition(), worldToClip, new Matrix4f(worldToClip).invert(),
                window.getWidth() / window.getGuiScale(), window.getHeight() / window.getGuiScale()));
    }

    @SubscribeEvent
    public static void hideHud(RenderGuiEvent.Pre event) {
        if (current() != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hideHand(RenderHandEvent event) {
        if (current() != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hideOverlay(RenderBlockScreenEffectEvent event) {
        if (current() != null) event.setCanceled(true);
    }

    record Frame(Vec3 eye, Matrix4f projection, Matrix4f inverse, double width, double height) {
        Vec3 project(Vec3 world) {
            Vec3 offset = world.subtract(eye);
            Vector4f clip = projection.transform(new Vector4f((float) offset.x, (float) offset.y, (float) offset.z, 1));
            if (clip.w <= 0 || clip.z < -clip.w || clip.z > clip.w) return null;
            return new Vec3((clip.x / clip.w + 1) * width / 2, (1 - clip.y / clip.w) * height / 2, clip.z / clip.w);
        }

        Vec3 unproject(double x, double y, float depth) {
            Vector4f point = inverse.transform(new Vector4f((float) (x * 2 / width - 1), (float) (1 - y * 2 / height), depth, 1));
            return eye.add(point.x / point.w, point.y / point.w, point.z / point.w);
        }
    }
}
