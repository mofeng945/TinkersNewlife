package com.mofengbaizhi.tinkersnewlife.client.handler;
import com.mofengbaizhi.tinkersnewlife.client.screen.SilentGloveScreen;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdInput;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientProjectionData;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiOpenGui;
import com.mofengbaizhi.tinkersnewlife.client.input.KeyBindings;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseReverseTechnique;
import com.mofengbaizhi.tinkersnewlife.client.screen.BagScreen;

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
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketDragonStaffUse;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketOpenBag;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketSwitchFlyingSwordMode;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketSwitchTechnique;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketToggleDomain;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseTechnique;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketUseSkill;
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
            // ⭐ 狱门疆视觉方块（实体渲染载体）：占位贴图含透明，走 cutout
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.mofengbaizhi.tinkersnewlife.content.ModBlocks.GOURD_JAIL_VISUAL.get(),
                    net.minecraft.client.renderer.RenderType.cutout());
            LOGGER.debug("[TinkersNewlife] 所有 GUI 屏幕已注册");
        });
    }

    /** 注册咒力 HUD 覆盖层 + 傀儡血条 HUD */
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("curse_hud", ClientCurseData::render);
        event.registerAboveAll("puppet_hud", com.mofengbaizhi.tinkersnewlife.client.hud.PuppetHudRenderer::render);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DREADSTEEL_SLASH.get(), DreadsteelSlashRenderer::new);
        event.registerEntityRenderer(ModEntities.FLYING_SWORD.get(), FlyingSwordRenderer::new);
        event.registerEntityRenderer(ModEntities.FLYING_SWORD_FOOT.get(), FlyingSwordFootRenderer::new);
        event.registerEntityRenderer(ModEntities.YO_YO.get(), YoYoRenderer::new);
        event.registerEntityRenderer(ModEntities.DOMAIN_VISUAL.get(), DomainVisualRenderer::new);
        // 灶·开 火焰箭：复用原版箭矢渲染（无着火贴图，火焰感由尾迹粒子表现）
        event.registerEntityRenderer(ModEntities.FLAME_ARROW.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.FlameArrowRenderer::new);
        // 赤血操术·超新星 血球：微小血红圆球
        event.registerEntityRenderer(ModEntities.BLOOD_NOVA.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.BloodNovaRenderer::new);
        // 十影术式 式神：全部复用原版渲染器（模型/动画/纹理/碰撞箱原版）
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_WOLF.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.ShikigamiWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_PHANTOM.get(),
                net.minecraft.client.renderer.entity.PhantomRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_SILVERFISH.get(),
                net.minecraft.client.renderer.entity.SilverfishRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_FROG.get(),
                net.minecraft.client.renderer.entity.FrogRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_PIG.get(),
                net.minecraft.client.renderer.entity.PigRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_RABBIT.get(),
                net.minecraft.client.renderer.entity.RabbitRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_GOAT.get(),
                net.minecraft.client.renderer.entity.GoatRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_COW.get(),
                net.minecraft.client.renderer.entity.CowRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_SHEEP.get(),
                net.minecraft.client.renderer.entity.SheepRenderer::new);
        event.registerEntityRenderer(ModEntities.SHIKIGAMI_IRON_GOLEM.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.ShikigamiIronGolemRenderer::new);
        // 黑鸟操术 黑鸟：复用原版蝙蝠渲染
        event.registerEntityRenderer(ModEntities.BLACK_BIRD.get(),
                net.minecraft.client.renderer.entity.BatRenderer::new);
        // 傀儡操术 铁傀儡/雪傀儡/雪球：复用原版渲染
        event.registerEntityRenderer(ModEntities.PUPPET_IRON_GOLEM.get(),
                net.minecraft.client.renderer.entity.IronGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.PUPPET_SNOW_GOLEM.get(),
                net.minecraft.client.renderer.entity.SnowGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.PUPPET_SNOWBALL.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        // 炎熔操术 自爆幻翼（焰羽）：1/8 缩放渲染
        event.registerEntityRenderer(ModEntities.FLAME_PHANTOM.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.FlamePhantomRenderer::new);
        // 咒灵操术 黑色漩涡弹：无模型渲染（粒子表现）
        event.registerEntityRenderer(ModEntities.SPIRIT_VORTEX.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.SpiritVortexRenderer::new);
        // 投射咒法 玩家虚影：蓝色半透明人形
        event.registerEntityRenderer(ModEntities.PROJECTION_PHANTOM.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.ProjectionPhantomRenderer::new);
        // 无下限 苍/赫/茈 球体：按类型着色发光圆球
        event.registerEntityRenderer(ModEntities.CURSED_ORB.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.CursedOrbRenderer::new);
        // 雅各布天梯 法阵：纯粒子视觉，占位渲染器
        event.registerEntityRenderer(ModEntities.JACOB_LADDER.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.JacobLadderRenderer::new);
        // 狱门疆：小立方体
        event.registerEntityRenderer(ModEntities.GOURD_JAIL.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.GourdJailRenderer::new);
        // 墨默（武器商人）：套用玩家模型 + 玩家贴图动画
        event.registerEntityRenderer(ModEntities.MOMO_MERCHANT.get(),
                com.mofengbaizhi.tinkersnewlife.client.renderer.MomoMerchantRenderer::new);
    }

    // ========== Forge 事件（按键等） ==========
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
    public static class ForgeEvents {

        private static final Logger LOGGER = LoggerFactory.getLogger(ForgeEvents.class);

        /** 术式按键上一次状态（边沿检测：按下发 press、松开发 release，支撑蓄力术式） */
        private static boolean lastTechniqueDown = false;
        /** 术式反转按键上一次状态（F 键边沿检测） */
        private static boolean lastReverseDown = false;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                lastTechniqueDown = false;
                return;
            }
            // ⭐ 投射咒法罚站：禁用鼠标键盘（清零移动/跳跃输入 + 锁定视角）
            if (com.mofengbaizhi.tinkersnewlife.client.data.ClientProjectionData.isStunned()) {
                net.minecraft.client.player.Input input = player.input;
                input.leftImpulse = 0;
                input.forwardImpulse = 0;
                input.jumping = false;
                input.shiftKeyDown = false;
                player.xxa = 0;
                player.zza = 0;
                player.setYRot(com.mofengbaizhi.tinkersnewlife.client.data.ClientProjectionData.getStunYaw());
                player.setXRot(com.mofengbaizhi.tinkersnewlife.client.data.ClientProjectionData.getStunPitch());
            }
            // ⭐ 黑鸟操控：相机绑定黑鸟时，每 tick 发送玩家输入驱动其飞行（含视角）
            if (Minecraft.getInstance().cameraEntity instanceof com.mofengbaizhi.tinkersnewlife.content.entity.BlackBirdEntity) {
                net.minecraft.client.player.Input input = player.input;
                TinkersNewlife.CHANNEL.sendToServer(new com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdInput(
                        input.forwardImpulse, input.leftImpulse, input.jumping, input.shiftKeyDown,
                        player.getYRot(), player.getXRot()));
            }
            // ⭐ 傀儡操控：相机绑定铁/雪傀儡时，每 tick 发送输入（含攻击/使用键）
            if (Minecraft.getInstance().cameraEntity instanceof com.mofengbaizhi.tinkersnewlife.content.entity.PuppetIronGolem
                    || Minecraft.getInstance().cameraEntity instanceof com.mofengbaizhi.tinkersnewlife.content.entity.PuppetSnowGolem) {
                net.minecraft.client.player.Input input = player.input;
                net.minecraft.client.Minecraft mc = Minecraft.getInstance();
                boolean left = mc.options.keyAttack.isDown();
                boolean right = mc.options.keyUse.isDown();
                TinkersNewlife.CHANNEL.sendToServer(new com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetInput(
                        input.forwardImpulse, input.leftImpulse, input.jumping, input.shiftKeyDown,
                        left, right, player.getYRot(), player.getXRot()));
            }
            // ⭐ 术式按键：按下=开始（即时释放或蓄力），松开=蓄力发射
            boolean down = KeyBindings.USE_TECHNIQUE.get().isDown();
            if (down && !lastTechniqueDown) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketUseTechnique(true));
            } else if (!down && lastTechniqueDown) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketUseTechnique(false));
            }
            lastTechniqueDown = down;

            // ⭐ 术式反转按键（F）：按下=开始蓄力反转术式，松开=发射
            boolean reverseDown = KeyBindings.REVERSE_TECHNIQUE.get().isDown();
            if (reverseDown && !lastReverseDown) {
                TinkersNewlife.CHANNEL.sendToServer(new com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseReverseTechnique(true));
            } else if (!reverseDown && lastReverseDown) {
                TinkersNewlife.CHANNEL.sendToServer(new com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseReverseTechnique(false));
            }
            lastReverseDown = reverseDown;
        }

        /** 投射咒法罚站 / 傀儡操控：取消玩家自身攻击/交互输入（傀儡动作由输入包驱动） */
        @SubscribeEvent
        public static void onInteractionInput(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
            if (com.mofengbaizhi.tinkersnewlife.client.data.ClientProjectionData.isStunned()) {
                event.setCanceled(true);
                return;
            }
            net.minecraft.world.entity.Entity cam = Minecraft.getInstance().cameraEntity;
            if (cam instanceof com.mofengbaizhi.tinkersnewlife.content.entity.PuppetIronGolem
                    || cam instanceof com.mofengbaizhi.tinkersnewlife.content.entity.PuppetSnowGolem) {
                event.setCanceled(true);
            }
        }

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
            // ✅ 切换当前术式（核心上有多个术式时循环选择）
            if (KeyBindings.SWITCH_TECHNIQUE.get().consumeClick()) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketSwitchTechnique());
            }
            // ✅ 无为转变：P 键打开形态选择界面
            if (KeyBindings.OPEN_WU_WEI.get().consumeClick()) {
                TinkersNewlife.CHANNEL.sendToServer(new com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiOpenGui());
            }
            // ✅ 模块化魔杖·巫法模式：J 开聚晶包 / R 循环聚晶（仅主手为魔杖且处于巫法模式）
            ItemStack staffHand = player.getMainHandItem();
            boolean goetyStaff = staffHand.getItem()
                    instanceof com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem
                    && com.mofengbaizhi.tinkersnewlife.content.goety.ModularStaffGoety.getMode(staffHand)
                    == com.mofengbaizhi.tinkersnewlife.content.goety.ModularStaffGoety.MODE_GOETY;
            if (goetyStaff && KeyBindings.STAFF_POUCH.get().consumeClick()) {
                TinkersNewlife.CHANNEL.sendToServer(
                        new com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetyAction(0, 0));
            }
            if (goetyStaff && KeyBindings.STAFF_CYCLE.get().consumeClick()) {
                TinkersNewlife.CHANNEL.sendToServer(
                        new com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetyAction(1, 0));
            }
            // ✅ 术式按键已移至 onClientTick（按下/松开边沿检测，支撑蓄力术式）
        }
    }
}