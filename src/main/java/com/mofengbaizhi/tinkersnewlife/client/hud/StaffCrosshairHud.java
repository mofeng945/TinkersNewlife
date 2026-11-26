package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.Locale;

/**
 * TNL1306 手持「模块化魔杖」时，在屏幕正中心**补画一枚原版准心**（客户端 HUD）。
 *
 * <h2>用户口径</h2>
 * 「手持模块化魔杖时，如果加了动态准心会导致瞄不准怪物，写渲染让手持时屏幕中心的准星保持渲染」✓
 *
 * <h2>为什么要单独画</h2>
 * 「动态准心（Dynamic Crosshair）」这类模组会把原版准心换成一枚**随目标变化的准心**
 * （对准怪物时形状/位置会变、甚至隐藏）⇒ 魔杖靠屏幕中心瞄准时就会「瞄不准」✗
 * 本类在**准心 overlay 之后**再补画一枚**固定居中**的原版准心 ✓
 * ⇒ 无论动态准心怎么变，屏幕中心永远有一枚可信的准星 ✓
 *
 * <h2>只在这些条件下画（与 {@code Gui#renderCrosshair} 的口径对齐）</h2>
 * <ul>
 *   <li>主手或副手拿着 {@link ModularStaffItem}；</li>
 *   <li>第一人称、没有打开任何界面、没有 F1 隐藏 HUD、不是在旁观模式、没有开 F3 调试屏；</li>
 *   <li>**只有装了动态准心类模组时才画** —— 否则原版准心本来就在，重复画只会变粗 ✓</li>
 * </ul>
 *
 * <h2>画法</h2>
 * 完全照抄原版 {@code Gui#renderCrosshair} 的那一句 ✓：
 * <pre>
 *   RenderSystem.blendFuncSeparate(ONE_MINUS_DST_COLOR, ONE_MINUS_SRC_COLOR, ONE, ZERO);
 *   blit(textures/gui/icons.png, (w - 15) / 2, (h - 15) / 2, 0, 0, 15, 15);
 *   RenderSystem.defaultBlendFunc();
 * </pre>
 * ⇒ 与不装动态准心时看到的那枚**一模一样**（不是自绘方块 ✓ 不需要任何贴图/语言键 ✓）
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class StaffCrosshairHud {

    private StaffCrosshairHud() {
    }

    /** 原版 HUD 贴图（{@code Gui#GUI_ICONS_LOCATION} 同一个） */
    private static final ResourceLocation GUI_ICONS_LOCATION = new ResourceLocation("textures/gui/icons.png");

    /** 原版准心贴图区域：左上 (0,0)、15×15 ✓ */
    private static final int CROSSHAIR_SIZE = 15;

    /** 是否装了「动态准心」类模组（懒检测，null = 还没查过） */
    private static Boolean tnl$dynamicCrosshairLoaded;

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        // ① 只挂在自己需要的那个 overlay 上（和本仓 EnergyConverterHud / LongShortBladeHud 同一套写法）
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        // ② 与 Gui#renderCrosshair 一致：只有第一人称才需要准心
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            return;
        }
        // ③ 打开界面时原版不画准心（我们的补画也不该出现）
        if (mc.screen != null) {
            return;
        }
        // ④ 旁观模式：原版只在特定情况下给旁观者画准心，这里不补
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        // ⑤ F3 调试屏：原版画的是 3D 调试准心，别叠一枚平面准心
        if (mc.options.renderDebug) {
            return;
        }
        try {
            if (!tnl$holdingStaff(player)) {
                return;
            }
            if (!tnl$hasDynamicCrosshairMod()) {
                return;      // 没装动态准心 ⇒ 原版准心已经在屏幕中心，不需要重复画
            }
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            // 原版同款混合：ONE_MINUS_DST_COLOR × ONE_MINUS_SRC_COLOR ⇒ 反色准心，任何背景都看得见
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            event.getGuiGraphics().blit(GUI_ICONS_LOCATION,
                    (screenW - CROSSHAIR_SIZE) / 2, (screenH - CROSSHAIR_SIZE) / 2,
                    0, 0, CROSSHAIR_SIZE, CROSSHAIR_SIZE);
            RenderSystem.defaultBlendFunc();
        } catch (Throwable ignored) {
            // HUD 出错绝不崩客户端（照本仓其它 HUD 的口径）
            RenderSystem.defaultBlendFunc();
        }
    }

    /** 主手或副手拿着模块化魔杖 */
    private static boolean tnl$holdingStaff(Player player) {
        return player.getMainHandItem().getItem() instanceof ModularStaffItem
                || player.getOffhandItem().getItem() instanceof ModularStaffItem;
    }

    /**
     * 是否装了「动态准心」类模组：按 mod id / 显示名匹配（中英文都认），
     * 因为不同发行版 id 可能写成 {@code dynamiccrosshair} / {@code dynamic_crosshair} 等 ✓
     */
    private static boolean tnl$hasDynamicCrosshairMod() {
        if (tnl$dynamicCrosshairLoaded == null) {
            boolean found = false;
            try {
                for (IModInfo info : ModList.get().getMods()) {
                    String id = info.getModId() == null ? "" : info.getModId().toLowerCase(Locale.ROOT);
                    String name = info.getDisplayName() == null ? "" : info.getDisplayName().toLowerCase(Locale.ROOT);
                    if (id.contains("dynamiccrosshair") || id.contains("dynamic_crosshair")
                            || name.contains("dynamic crosshair") || name.contains("动态准心")) {
                        found = true;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                found = false;
            }
            tnl$dynamicCrosshairLoaded = found;
        }
        return tnl$dynamicCrosshairLoaded;
    }
}
