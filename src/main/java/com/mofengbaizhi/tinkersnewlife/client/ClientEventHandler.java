package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.DreadsteelSlashRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FlyingSwordFootRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FlyingSwordRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.YoYoRenderer;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.ModMenus;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;
import com.mofengbaizhi.tinkersnewlife.content.modifier.QuantumBagModifier;
import com.mofengbaizhi.tinkersnewlife.network.PacketDragonStaffUse;
import com.mofengbaizhi.tinkersnewlife.network.PacketOpenBag;
import com.mofengbaizhi.tinkersnewlife.network.PacketSwitchFlyingSwordMode;
import com.mofengbaizhi.tinkersnewlife.network.PacketUseSkill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventHandler.class);

    /** 量子背包打开防抖：按住按键时 consumeClick 每 tick 都返回 true，避免持续发包导致服务端重复打开 GUI */
    private static long lastBagOpenMillis = 0;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.BAG_CONTAINER.get(), BagScreen::new);
            // ✅ 注册噤默手套 GUI
            MenuScreens.register(ModMenus.SILENT_GLOVE_CONTAINER.get(), SilentGloveScreen::new);
            LOGGER.debug("[TinkersNewlife] 所有 GUI 屏幕已注册");
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DREADSTEEL_SLASH.get(), DreadsteelSlashRenderer::new);
        event.registerEntityRenderer(ModEntities.FLYING_SWORD.get(), FlyingSwordRenderer::new);
        event.registerEntityRenderer(ModEntities.FLYING_SWORD_FOOT.get(), FlyingSwordFootRenderer::new);
        event.registerEntityRenderer(ModEntities.YO_YO.get(), YoYoRenderer::new);
    }

    // ========== Forge 事件（按键等） ==========
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
    public static class ForgeEvents {

        private static final Logger LOGGER = LoggerFactory.getLogger(ForgeEvents.class);

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;

            if (KeyBindings.USE_SKILL.get().consumeClick()) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketUseSkill());
            }

            if (KeyBindings.DRAGON_STAFF_USE.get().consumeClick()) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketDragonStaffUse());
            }

            if (KeyBindings.OPEN_BAG.get().consumeClick()) {
                ItemStack mainHand = player.getMainHandItem();
                ItemStack offHand = player.getOffhandItem();
                int hand = -1;
                if (QuantumBagModifier.getBagLevel(mainHand) > 0) {
                    hand = 0;
                } else if (QuantumBagModifier.getBagLevel(offHand) > 0) {
                    hand = 1;
                }
                if (hand >= 0) {
                    // ⭐ 客户端防抖：300ms 内不重复发包，防止按住按键持续触发服务端 openScreen（局域网模式更明显）
                    long now = System.currentTimeMillis();
                    if (now - lastBagOpenMillis >= 300) {
                        lastBagOpenMillis = now;
                        TinkersNewlife.CHANNEL.sendToServer(new PacketOpenBag(hand));
                    }
                }
            }

            if (KeyBindings.SWITCH_FLYING_SWORD_MODE.get().consumeClick()) {
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.getItem() instanceof FlyingSwordItem) {
                    TinkersNewlife.CHANNEL.sendToServer(new PacketSwitchFlyingSwordMode(0));
                } else {
                    ItemStack offHand = player.getOffhandItem();
                    if (offHand.getItem() instanceof FlyingSwordItem) {
                        TinkersNewlife.CHANNEL.sendToServer(new PacketSwitchFlyingSwordMode(1));
                    }
                }
            }
        }
    }
}