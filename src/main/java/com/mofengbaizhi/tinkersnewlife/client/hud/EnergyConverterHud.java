package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.ConverterCoreHolder;
import com.mofengbaizhi.tinkersnewlife.content.block.EeConverterCore;
import com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        try {
            float partial = event.getPartialTick();
            Vec3 eye = mc.player.getEyePosition(partial);
            Vec3 look = mc.player.getViewVector(partial);

            for (double d = 0.0D; d <= REACH; d += STEP) {
                BlockPos at = BlockPos.containing(eye.add(look.scale(d)));
                BlockState state = mc.level.getBlockState(at);
                if (!state.hasProperty(EnergyConverterBlock.FACING)) continue;          // 不是转化器 ⇒ 继续往后找 ✓
                if (!(mc.level.getBlockEntity(at) instanceof ConverterCoreHolder holder)) return;
                EeConverterCore core = holder.converterCore();
                int fe = core.getEnergyStored();
                int max = core.getMaxEnergyStored();

                long now = System.currentTimeMillis();
                if (lastFe < 0) { lastFe = fe; lastMs = now; }
                else if (now - lastMs >= SAMPLE_MS) {
                    perSecond = (int) Math.round((fe - lastFe) * 1000.0D / (double) (now - lastMs));
                    lastFe = fe;
                    lastMs = now;
                }

                int cx = mc.getWindow().getGuiScaledWidth() / 2;
                int cy = mc.getWindow().getGuiScaledHeight() / 2;
                event.getGuiGraphics().drawString(mc.font, "§b万用能量转化器", cx + 12, cy + 10, 0xFFFFFFFF);
                event.getGuiGraphics().drawString(mc.font, "§fFE 池  §e" + fe + " §7/ " + max,
                        cx + 12, cy + 21, 0xFFFFFFFF);
                String flow = perSecond >= 0 ? ("§a+" + perSecond) : ("§c" + perSecond);
                event.getGuiGraphics().drawString(mc.font, "§7变化  " + flow + " §7FE/秒",
                        cx + 12, cy + 32, 0xFFFFFFFF);
                return;
            }
        } catch (Throwable ignored) {
            // fail-safe：HUD 出错绝不崩客户端 ✓
        }
    }
}