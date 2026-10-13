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
     * TNL931 \u2605\u2605 \u76f4\u63a5**\u590d\u7528\u5f13\u5c04\u51fa\u7684\u7bad\u7684\u5b9e\u4f53\u6a21\u578b\u002b\u8d34\u56fe** \u2713
     *
     * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u4f60\u76f4\u63a5\u590d\u7528\u5f13\u5c04\u51fa\u7684\u7bad\u7684\u5b9e\u4f53\u6a21\u578b\u4e0d\u5c31\u597d\u4e86" \u2713
     * \uff0b\u2605"\u6211\u8bf4\u6211\u753b\u7684\u90a3\u4e2a" \u2713
     *
     * <p>\u2605\u4e4b\u524d\u4e3a\u4ec0\u4e48\u4e0d\u5bf9 \u2717\uff1a\u2605\u6211\u81ea\u5df1\u753b\u9762\u7247 / \u2605\u6539
     * ``setShaderColor`` \u2717 \u2014\u2014 \u2605\u989c\u8272\u662f\u5728**\u6279\u5904\u7406\u5237\u51fa\u65f6**\u8bfb\u7684 \u2713
     * \u21d2 \u2605\u6574\u6279\uff08\u2605\u5305\u62ec\u5b9e\u4f53\uff09\u4e00\u8d77\u88ab\u67d3\u9ed1 \u2717\u3002
     *
     * <p>\u2605\u73b0\u5728 \u2713\uff1a\u2605\u76f4\u63a5\u8c03\u2605``EntityRenderDispatcher#render`` \u2713
     * \u5bf9\u4e00\u4e2a**\u4ec5\u5ba2\u6237\u7aef\u7684\u66ff\u8eab\u7bad**\uff08\u2605``new StarfallArrow`` \u2717
     * \u2605\u4e0d\u52a0\u5165\u4e16\u754c / \u2605\u4e0d\u53d1\u5305 \u2713\uff09\u21d2 \u2605\u7528\u7684\u5c31\u662f
     * \u2605**\u4f60\u753b\u7684\u90a3\u5957\u6a21\u578b\u4e0e\u8d34\u56fe** \u2713\uff0c\u2605\u4e0d\u6539\u4efb\u4f55\u989c\u8272 \u2713\u3002
     */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!REMAIN.containsKey(entity.getUUID())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        com.mofengbaizhi.tinkersnewlife.content.entity.StarfallArrow dummy =
                new com.mofengbaizhi.tinkersnewlife.content.entity.StarfallArrow(
                        com.mofengbaizhi.tinkersnewlife.content.ModEntities.STARFALL_ARROW.get(), mc.level);
        var pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(0.0D, entity.getBbHeight() * 0.62D, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(entity.getYRot() + 18.0F));
            pose.mulPose(Axis.XP.rotationDegrees(28.0F));           // \u2605\u659c\u7740\u624e\u8fdb\u53bb \u2713
            pose.scale(1.15F, 1.15F, 1.15F);
            mc.getEntityRenderDispatcher().render(dummy, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F,
                    pose, event.getMultiBufferSource(), event.getPackedLight());
        } catch (Throwable ignored) {
        } finally {
            pose.popPose();
        }
    }
}
