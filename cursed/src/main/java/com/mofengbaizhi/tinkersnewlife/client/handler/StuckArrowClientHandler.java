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
 * TNL927 ★★ 黑箭的**客户端**部分 ✓
 *
 * <ul>
 *   <li>★同步：★哪些生物身上有箭 + ★剩余时间 ✓；</li>
 *   <li>★渲染：★在生物身上画一支**染黑的箭** ✓；</li>
 *   <li>★拔除：★长按 **G 满 2 秒（40 tick）** ⇒ ★发包 ✓；★松手重置 ✓；</li>
 *   <li>★拔除期间：★取消**攻击/使用物品** ✓（★移动与转视角不受影响 ✓）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StuckArrowClientHandler {

    /** ★长按多久算成功（tick）✓ */
    private static final int HOLD_TICKS = 40;

    private static final Map<UUID, Integer> REMAIN = new HashMap<>();
    private static KeyMapping pullKey;
    private static int hold = 0;

    private StuckArrowClientHandler() {
    }

    /** ★服务端同步（★来自 PacketStuckArrow ✓） */
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
                TinkersNewlife.CHANNEL.sendToServer(new PacketPullArrow());   // ★拔掉 ✓
            }
        } else {
            hold = 0;                                                     // ★松手失败 ✓
        }
    }

    /** ★拔除期间不能攻击/用物品 ✓ */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (hold > 0 && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /**
     * TNL931 ★★ 直接**复用弓射出的箭的实体模型+贴图** ✓
     *
     * <p>★用户口径：★"你直接复用弓射出的箭的实体模型不就好了" ✓
     * ＋★"我说我画的那个" ✓
     *
     * <p>★之前为什么不对 ✗：★我自己画面片 / ★改
     * ``setShaderColor`` ✗ —— ★颜色是在**批处理刷出时**读的 ✓
     * ⇒ ★整批（★包括实体）一起被染黑 ✗。
     *
     * <p>★现在 ✓：★直接调★``EntityRenderDispatcher#render`` ✓
     * 对一个**仅客户端的替身箭**（★``new StarfallArrow`` ✗
     * ★不加入世界 / ★不发包 ✓）⇒ ★用的就是
     * ★**你画的那套模型与贴图** ✓，★不改任何颜色 ✓。
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
            pose.mulPose(Axis.XP.rotationDegrees(28.0F));           // ★斜着扎进去 ✓
            pose.scale(1.15F, 1.15F, 1.15F);
            mc.getEntityRenderDispatcher().render(dummy, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F,
                    pose, event.getMultiBufferSource(), event.getPackedLight());
        } catch (Throwable ignored) {
        } finally {
            pose.popPose();
        }
    }
}
