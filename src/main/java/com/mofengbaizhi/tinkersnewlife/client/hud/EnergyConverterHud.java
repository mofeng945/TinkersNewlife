package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.block.ConverterCoreHolder;
import com.mofengbaizhi.tinkersnewlife.content.block.EeConverterCore;
import com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>准星对准"万用能量转化器"时显示 FE 池与产出速度</b>（§594）。
 *
 * <p>数据来源：客户端方块实体里的 {@link EeConverterCore} ✓（FE 池由 §593 的"变化即同步"每 10 tick 推过来 ✓）；
 * <b>每秒变化</b>是**本地测量**的 ✗（相邻两次采样的差值 / 时间 ✓）⇒ 不需要服务端再发包 ✓ 也不怕网络抖动 ✓
 * （代价：打开界面切换/刚放下的第一秒可能显示 0 ✓ 属正常 ✓）。
 *
 * <p>射线：沿视线每 0.25 格走一步、最远 6 格 ✓ 命中"带 {@code converter_facing} 属性的方块"即认为瞄到 ✓
 * （与 §554 台座 HUD 同一套路 ✓ 自己走射线 ⇒ 不受方块选中形状影响 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EnergyConverterHud {

    private EnergyConverterHud() {}

    private static final double REACH = 6.0D;
    private static final double STEP = 0.25D;
    /** 采样间隔（毫秒）：太短会抖 ✓ 500ms 够稳 ✓ */
    private static final long SAMPLE_MS = 500L;

    private static int lastFe = -1;
    private static long lastMs = 0L;
    private static int perSecond = 0;
    /** §602：上一次瞄到的是哪一格 —— 换了目标就必须把采样清零 ✗（否则会拿上一台的 FE 去算差值 ⇒ 头 500ms 显示一个荒唐数 ✓） */
    private static BlockPos lastPos = null;

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        try {
            float partial = event.getPartialTick();
            Vec3 eye = mc.player.getEyePosition(partial);
            Vec3 look = mc.player.getViewVector(partial);
            boolean found = false;

            for (double d = 0.0D; d <= REACH; d += STEP) {
                BlockPos at = BlockPos.containing(eye.add(look.scale(d)));
                BlockState state = mc.level.getBlockState(at);
                if (!state.hasProperty(EnergyConverterBlock.FACING)) continue;          // 不是转化器 ⇒ 继续往后找 ✓
                if (!(mc.level.getBlockEntity(at) instanceof ConverterCoreHolder holder)) break;
                found = true;
                EeConverterCore core = holder.converterCore();
                int fe = core.getEnergyStored();
                int max = core.getMaxEnergyStored();

                long now = System.currentTimeMillis();
                if (!at.equals(lastPos)) {                 // §602 换了目标（或刚瞄上）⇒ 采样清零 ✓ 从"这一刻"重新算 ✓
                    lastPos = at.immutable();
                    lastFe = fe;
                    lastMs = now;
                    perSecond = 0;
                } else if (now - lastMs >= SAMPLE_MS) {
                    perSecond = (int) Math.round((fe - lastFe) * 1000.0D / (double) (now - lastMs));
                    lastFe = fe;
                    lastMs = now;
                }

                int cx = mc.getWindow().getGuiScaledWidth() / 2;
                int cy = mc.getWindow().getGuiScaledHeight() / 2;
                int screenW = mc.getWindow().getGuiScaledWidth();
                // §602 文案全部走语言键 + 方块自己的本地化名 ✓（原来写死中文 ✗ 英文客户端会看到中文 ✓）
                drawLine(event.getGuiGraphics(), mc.font, screenW,
                        Component.literal("§b").append(ModBlocks.ENERGY_CONVERTER.get().getName()), cx + 12, cy + 10);
                drawLine(event.getGuiGraphics(), mc.font, screenW,
                        Component.translatable("gui.tinkersnewlife.converter_hud.pool", fe, max), cx + 12, cy + 21);
                String flow = perSecond >= 0 ? ("§a+" + perSecond) : ("§c" + perSecond);
                drawLine(event.getGuiGraphics(), mc.font, screenW,
                        Component.translatable("gui.tinkersnewlife.converter_hud.flow", flow), cx + 12, cy + 32);
                break;
            }
            if (!found) {                                  // §602 没瞄到 ⇒ 采样作废 ✓（免得离开一会儿再瞄回来算出一个跨越大段时间的怪值 ✗）
                lastPos = null;
                perSecond = 0;
            }
        } catch (Throwable ignored) {
            // fail-safe：HUD 出错绝不崩客户端 ✓
        }
    }

    /**
     * §602 画一行 HUD 文本：靠右会超出屏幕 ⇒ <b>自动往左让</b> ✓
     * （窄窗口 / 大 GUI 缩放下，原来那三行会跑出右边界被切掉 ✗）。
     */
    private static void drawLine(GuiGraphics graphics, Font font, int screenW, Component text, int x, int y) {
        int width = font.width(text);
        int clamped = Math.min(x, Math.max(4, screenW - 4 - width));
        graphics.drawString(font, text, clamped, y, 0xFFFFFFFF);
    }
}