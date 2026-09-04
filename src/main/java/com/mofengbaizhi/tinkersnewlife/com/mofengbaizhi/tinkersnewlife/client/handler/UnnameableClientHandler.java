package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public class UnnameableClientHandler {

    // 失明参数
    private static final float FOG_DISTANCE_FACTOR = 0.01f;   // 雾距离缩放（0.1 = 10% 原距离，视野极近）
    private static final float FOG_DARKNESS = 0.01f;         // 雾颜色暗度（0=纯黑，1=原色）

    // 反胃参数
    private static final float SWAY_AMOUNT = 10f;          // 晃动幅度

    /**
     * 模拟失明：缩小近/远平面距离
     */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        MobEffectInstance effect = player.getEffect(ModEffects.UNNAMEABLE.get());
        if (effect == null) return;

        // 直接设置近/远平面距离为当前距离乘以系数
        event.setNearPlaneDistance(event.getNearPlaneDistance() * FOG_DISTANCE_FACTOR);
        event.setFarPlaneDistance(event.getFarPlaneDistance() * FOG_DISTANCE_FACTOR);

        // 必须取消事件，否则修改无效
        event.setCanceled(true);
    }

    /**
     * 模拟失明：将雾颜色变为暗色（黑雾）
     */
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        MobEffectInstance effect = player.getEffect(ModEffects.UNNAMEABLE.get());
        if (effect == null) return;

        event.setRed(FOG_DARKNESS);
        event.setGreen(FOG_DARKNESS);
        event.setBlue(FOG_DARKNESS);
    }

    /**
     * 模拟反胃：视角晃动
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        MobEffectInstance effect = player.getEffect(ModEffects.UNNAMEABLE.get());
        if (effect == null) return;

        float ticks = player.tickCount + (float) event.getPartialTick();

        float swayYaw = (float) Math.sin(ticks * 0.08f) * SWAY_AMOUNT * 2;
        float swayPitch = (float) Math.cos(ticks * 0.07f) * SWAY_AMOUNT * 1.2f;
        float swayRoll = (float) Math.sin(ticks * 0.05f) * SWAY_AMOUNT * 0.8f;

        event.setYaw(event.getYaw() + swayYaw);
        event.setPitch(event.getPitch() + swayPitch);
        event.setRoll(event.getRoll() + swayRoll);
    }
}