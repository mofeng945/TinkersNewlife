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

    /** \u2605\u5728\u751f\u7269\u8eab\u4e0a\u753b\u4e00\u652f\u67d3\u9ed1\u7684\u7bad \u2713 */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!REMAIN.containsKey(entity.getUUID())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0.0D, entity.getBbHeight() * 0.62D, 0.0D);
        event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(38.0F));
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(20.0F));
        RenderSystem.setShaderColor(0.12F, 0.12F, 0.12F, 1.0F);          // \u2605\u67d3\u9ed1 \u2713
        ItemRenderer ir = mc.getItemRenderer();
        ir.renderStatic(new ItemStack(Items.ARROW), ItemDisplayContext.GROUND, 0xF000F0,
                OverlayTexture.NO_OVERLAY, event.getPoseStack(), event.getMultiBufferSource(),
                entity.level(), entity.getId());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        event.getPoseStack().popPose();
    }
}
