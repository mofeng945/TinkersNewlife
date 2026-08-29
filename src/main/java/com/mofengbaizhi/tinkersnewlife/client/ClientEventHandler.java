package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.DreadsteelSlashRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.DomainVisualRenderer;
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
import com.mofengbaizhi.tinkersnewlife.network.PacketToggleDomain;
import com.mofengbaizhi.tinkersnewlife.network.PacketUseTechnique;
import com.mofengbaizhi.tinkersnewlife.network.PacketUseSkill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventHandler.class);

    /** 量子背包按键边沿检测：记录上次按下状态，仅在"松开→按下"时触发一次，按住期间不重复发包 */
    private static boolean lastBagKeyDown = false;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.BAG_CONTAINER.get(), BagScreen::new);
            // ✅ 注册噤默手套 GUI
            MenuScreens.register(ModMenus.SILENT_GLOVE_CONTAINER.get(), SilentGloveScreen::new);
            // ⭐ 血液方块：透明纹理必须走 cutout 渲染层（默认 solid 层不做 alpha 测试，
            // 透明像素的 RGB 会被原样画出 → 看起来是纯黑背景）
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.mofengbaizhi.tinkersnewlife.content.ModBlocks.BLOOD_REDSTONE.get(),
                    net.minecraft.client.renderer.RenderType.cutout());
            LOGGER.debug("[TinkersNewlife] 所有 GUI 屏幕已注册");
        });
    }

    /** 注册咒力 HUD 覆盖层 */
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("curse_hud", ClientCurseData::render);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DREADSTEEL_SLASH.get(), DreadsteelSlashRenderer::new);
        event.registerEntityRenderer(ModEntities.FLYING_SWORD.get(), FlyingSwordRenderer::new);
        event.registerEntityRenderer(ModEntities.FLYING_SWORD_FOOT.get(), FlyingSwordFootRenderer::new);
        event.registerEntityRenderer(ModEntities.YO_YO.get(), YoYoRenderer::new);
        event.registerEntityRenderer(ModEntities.DOMAIN_VISUAL.get(), DomainVisualRenderer::new);
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

            // ⭐ 量子背包：边沿检测（按住只触发一次，松开再按再次触发），彻底避免按住时重复 openScreen 导致闪烁
            boolean bagKeyDown = KeyBindings.OPEN_BAG.get().isDown();
            if (bagKeyDown && !lastBagKeyDown) {
                ItemStack mainHand = player.getMainHandItem();
                ItemStack offHand = player.getOffhandItem();
                int hand = -1;
                if (QuantumBagModifier.getBagLevel(mainHand) > 0) {
                    hand = 0;
                } else if (QuantumBagModifier.getBagLevel(offHand) > 0) {
                    hand = 1;
                }
                if (hand >= 0) {
                    TinkersNewlife.CHANNEL.sendToServer(new PacketOpenBag(hand));
                }
            }
            lastBagKeyDown = bagKeyDown;

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

            // ✅ 坐杀搏徒：展开/关闭领域
            if (KeyBindings.TOGGLE_DOMAIN.get().consumeClick()) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketToggleDomain());
            }

            // ✅ 术式释放（解等）：对看向的实体释放术式
            if (KeyBindings.USE_TECHNIQUE.get().consumeClick()) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketUseTechnique());
            }
        }
    }
}