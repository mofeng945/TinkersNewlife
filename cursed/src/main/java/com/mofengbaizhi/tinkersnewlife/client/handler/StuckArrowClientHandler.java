package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.StuckArrowManager;
import com.mofengbaizhi.tinkersnewlife.network.stuck.PacketPullArrow;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TNL927 \u2605\u2605 \u9ed1\u7bad\u7684**\u5ba2\u6237\u7aef**\u90e8\u5206 \u2713
 *
 * <ul>
 *   <li>\u2605\u540c\u6b65\uff1a\u2605\u54ea\u4e9b\u751f\u7269\u8eab\u4e0a\u6709\u7bad \u002b \u2605\u5269\u4f59\u65f6\u95f4 \u2713\uff1b</li>
 *   <li>\u2605\u6e32\u67d3\uff1a\u2605\u5728\u751f\u7269\u8eab\u4e0a\u753b\u4e00\u652f**\u67d3\u9ed1\u7684\u7bad** \u2713\uff1b</li>
 *   <li>\u2605\u62d4\u9664\uff1a\u2605\u957f\u6309 **G \u6ee1 2 \u79d2\uff0840 tick\uff09** \u21d2 \u2605\u53d1\u5305 \u2713\uff1b\u2605\u677e\u624b\u91cd\u7f6e \u2713\uff1b</li>
 *   <li>\u2605\u62d4\u9664\u671f\u95f4\uff1a\u2605\u53d6\u6d88**\u653b\u51fb/\u4f7f\u7528\u7269\u54c1** \u2713\uff08\u2605\u79fb\u52a8\u4e0e\u8f6c\u89c6\u89d2\u4e0d\u53d7\u5f71\u54cd \u2713\uff09\u3002</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StuckArrowClientHandler {

    /** \u2605\u957f\u6309\u591a\u4e45\u7b97\u6210\u529f\uff08tick\uff09\u2713 */
    private static final int HOLD_TICKS = 40;

    private static final Map<UUID, Integer> REMAIN = new HashMap<>();
    private static KeyMapping pullKey;
    private static int hold = 0;

    private StuckArrowClientHandler() {
    }

    /** \u2605\u670d\u52a1\u7aef\u540c\u6b65\uff08\u2605\u6765\u81ea PacketStuckArrow \u2713\uff09 */
    public static void onSync(UUID id, boolean present) {
        if (present) {
            REMAIN.put(id, StuckArrowManager.FULL);
        } else {
            REMAIN.remove(id);
        }
    }

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            pullKey = new KeyMapping("key.tinkersnewlife.pull_arrow", GLFW.GLFW_KEY_G, "key.categories.tinkersnewlife");
            event.register(pullKey);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        REMAIN.replaceAll((k, v) -> v - 1);
        REMAIN.entrySet().removeIf(en -> en.getValue() <= 0);
        if (mc.player == null || pullKey == null) {
            hold = 0;
            return;
        }
        if (!REMAIN.containsKey(mc.player.getUUID())) {
            hold = 0;
            return;
        }
        if (pullKey.isDown()) {
            hold++;
            if (hold >= HOLD_TICKS) {
                hold = 0;
                TinkersNewlife.CHANNEL.sendToServer(new PacketPullArrow());   // \u2605\u62d4\u6389 \u2713
            }
        } else {
            hold = 0;                                                     // \u2605\u677e\u624b\u5931\u8d25 \u2713
        }
    }

    /** \u2605\u62d4\u9664\u671f\u95f4\u4e0d\u80fd\u653b\u51fb/\u7528\u7269\u54c1 \u2713 */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (hold > 0 && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /**
     * TNL929 \u2605\u2605 \u5728\u751f\u7269\u8eab\u4e0a\u753b\u4e00\u652f**\u9ed1\u7bad** \u2713
     *
     * <p>\u2605\u7528\u6237\u53cd\u9988\uff1a\u2605"\u51cf\u901f\u751f\u6548\u4e86\uff0c\u4f46\u662f\u6211\u60f3\u8981\u7684\u662f\u7bad\u77e2\u63d2\u5728\u751f\u7269\u8eab\u4e0a\u7684\u6548\u679c\uff0c
     * \u800c\u4e0d\u662f\u751f\u7269\u53d8\u9ed1" \u2713
     *
     * <p>\u2605\u771f\u56e0 \u2717\uff1a\u2605``RenderSystem.setShaderColor`` \u662f**\u5168\u5c40\u72b6\u6001** \u2717
     * \uff0c\u2605\u800c\u6211\u6ca1\u7528 ``try/finally`` \u4fdd\u62a4 \u2717 \u21d2
     * \u2605\u4e00\u65e6\u7bad\u7684\u6e32\u67d3\u4e2d\u9014\u629b\u5f02\u5e38\uff0c\u2605\u6062\u590d\u90a3\u884c\u5c31\u4e0d\u6267\u884c \u2717
     * \u21d2 \u2605\u540e\u9762**\u6240\u6709\u751f\u7269\u90fd\u88ab\u67d3\u9ed1** \u2713\u2713\uff08\u2605\u7528\u6237\u770b\u5230\u7684\u5c31\u662f\u8fd9\u4e2a \u2713\uff09\u3002
     *
     * <p>\u2605\u73b0\u5728 \u2713\uff1a\u2605``try { ... } finally { \u6062\u590d\u989c\u8272 \u002b popPose }`` \u2713
     * \uff0c\u2605\u5e76\u4e14\u628a\u7bad**\u653e\u5927 1.5 \u500d \u002b \u5f80\u8eab\u4f53\u4fa7\u540e\u504f** \u2713
     * \u21d2 \u2605\u770b\u8d77\u6765\u5c31\u662f\u2605\u4e00\u652f\u9ed1\u7bad\u624e\u5728\u8eab\u4e0a \u2713\u3002
     */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!REMAIN.containsKey(entity.getUUID())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        event.getPoseStack().pushPose();
        try {
            event.getPoseStack().translate(0.0D, entity.getBbHeight() * 0.62D, 0.0D);
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(38.0F));
            event.getPoseStack().mulPose(Axis.YP.rotationDegrees(20.0F));
            event.getPoseStack().scale(1.5F, 1.5F, 1.5F);
            RenderSystem.setShaderColor(0.10F, 0.10F, 0.10F, 1.0F);      // \u2605\u67d3\u9ed1 \u2713
            mc.getItemRenderer().renderStatic(new ItemStack(Items.ARROW), ItemDisplayContext.GROUND, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, event.getPoseStack(), event.getMultiBufferSource(),
                    entity.level(), entity.getId());
        } catch (Throwable ignored) {
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);         // \u2605\u2605 \u65e0\u8bba\u5982\u4f55\u90fd\u6062\u590d \u2713
            event.getPoseStack().popPose();
        }
    }
}
