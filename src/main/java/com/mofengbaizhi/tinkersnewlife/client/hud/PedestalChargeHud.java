package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.block.ElderManaPedestalBlockEntity;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>准星对准悬浮水晶时，在准星下方显示充能进度</b>（用户口径 §554 ✓）。
 *
 * <h2>为什么不能直接用原版准星</h2>
 * 悬浮水晶是 **BER 画在台座上方 1.5 格处**（= 上方那格的正中心 ✓ §607d）的 ✗（见 {@code ElderManaPedestalRenderer}），
 * 它<b>不在任何方块的选中形状里</b> ✗ ⇒ 原版射线只会穿过它打到后面的方块 ✗。
 * 所以这里<b>自己沿视线走一条射线</b>（每 0.2 格采一次、最远 6 格 ✓），
 * 对沿途每一步检查"这格或它下面一格是不是魔力台座" ✓，是的话就把
 * <b>水晶所在的那个小盒子</b>（{@link #CRYSTAL_BOX}）与该射线做一次相交测试 ✓
 * —— 相交 ⇒ 说明准星确实落在悬浮水晶上 ✓ 才显示 ✓。
 *
 * <h2>显示什么</h2>
 * 水晶的 <b>已存 EE / 容量</b> + 百分比 ✓（数据来自客户端方块实体里那颗水晶的 NBT ✓ 服务端本来就同步 ✓
 * —— 缓存那部分**没同步**给客户端 ✗ 所以这里不显示 ✓，要显示得再补一次同步 ✓）。
 *
 * <p>只在客户端注册（{@code Dist.CLIENT} ✓）；每帧只做几十次方块查询 ⇒ 忽略不计 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PedestalChargeHud {

    private PedestalChargeHud() {}

    /** 射线最远距离（格）——与原版触及距离同量级 ⇒ "看得见就瞄得到" ✓ */
    private static final double REACH = 6.0D;
    /** 射线步长（格）——0.2 ⇒ 6 格只要 30 步 ✓ 够密不会漏 ✓ */
    private static final double STEP = 0.2D;

    /**
     * 悬浮水晶的小盒子（方块局部坐标 ✓）—— §607d 起渲染高度是 **1.5**（上方那格的正中心 ✓）
     * ⇒ 这个盒子整体**上移 0.4**（原来是 0.75..1.45 对齐 1.1 ✓ 现在 1.15..1.85 对齐 1.5 ✓ 尺寸没变 ✓）。
     * <p>⚠ 不改它就会出现"准星明明指着水晶却不出 HUD"✗（命中盒还在旧高度 ✓）。
     */
    private static final AABB CRYSTAL_BOX = new AABB(0.15D, 1.15D, 0.15D, 0.85D, 1.85D, 0.85D);

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        // 只在"准星"这一层画一次（这个事件每种 overlay 都会发一次 ✗）
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        try {
            Level level = mc.level;
            float partial = event.getPartialTick();
            Vec3 eye = mc.player.getEyePosition(partial);
            Vec3 look = mc.player.getViewVector(partial);

            for (double d = 0.0D; d <= REACH; d += STEP) {
                Vec3 at = eye.add(look.scale(d));
                BlockPos pos = BlockPos.containing(at);

                // 水晶浮在台座上方 ⇒ 这格与**下面一格**都可能是"台座本体" ✓
                BlockPos pedestal = isPedestal(level, pos) ? pos
                        : (isPedestal(level, pos.below()) ? pos.below() : null);
                if (pedestal == null) continue;

                AABB box = CRYSTAL_BOX.move(pedestal);
                if (box.clip(eye, eye.add(look.scale(REACH))).isEmpty()) continue;

                if (level.getBlockEntity(pedestal) instanceof ElderManaPedestalBlockEntity be) {
                    ItemStack crystal = be.getCrystal();
                    if (crystal.isEmpty()) return;                       // 空的 ⇒ 不显示（用户口径：显示**充能进度** ✓）
                    // §556 按**手里那件是什么**显示（水晶物品 1000 / 水晶方块 4000 ✓ 原来写死水晶 ✗）
                    boolean isBlockItem = crystal.is(com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get());
                    int ee = isBlockItem ? ElderCrystalStorage.getBlockItemEe(crystal)
                                         : ElderCrystalStorage.getCrystalEe(crystal);
                    int cap = isBlockItem ? ElderCrystalStorage.BLOCK_CAPACITY
                                          : ElderCrystalStorage.CRYSTAL_CAPACITY;
                    int pct = cap <= 0 ? 0 : (int) Math.round(ee * 100.0D / cap);

                    int cx = mc.getWindow().getGuiScaledWidth() / 2;
                    int cy = mc.getWindow().getGuiScaledHeight() / 2;
                    int screenW = mc.getWindow().getGuiScaledWidth();
                    // §602 文案全部走语言键 + 物品自己的本地化名 ✓
                    //   （原来写死"古老者水晶 / 古老者水晶方块"与"充能进度"✗ ⇒ 英文客户端会看到中文 ✓）
                    Component line1 = Component.translatable("gui.tinkersnewlife.pedestal_hud.crystal",
                            crystal.getHoverName(), ee, cap);
                    Component line2 = Component.translatable("gui.tinkersnewlife.pedestal_hud.progress", pct);
                    drawLine(event.getGuiGraphics(), mc.font, screenW, line1, cx + 12, cy + 10);
                    // ⚠ "%" 在 Java 这边拼 ✓ 不扔进语言键（否则得写 %% ✗ 少一层坑 ✓）
                    drawLine(event.getGuiGraphics(), mc.font, screenW,
                            Component.literal(line2.getString() + "%"), cx + 12, cy + 21);
                }
                return;   // 瞄到了台座（不管有没有水晶）就收工 ✓ 不继续往后穿 ✗
            }
        } catch (Throwable ignored) {
            // fail-safe：HUD 出错绝不崩客户端 ✓
        }
    }

    private static boolean isPedestal(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.ELDER_MANA_PEDESTAL.get());
    }

    /**
     * §602 画一行 HUD 文本：靠右会超出屏幕 ⇒ <b>自动往左让</b> ✓
     * （窄窗口 / 大 GUI 缩放下两行文字会被右边界切掉 ✗）。
     */
    private static void drawLine(net.minecraft.client.gui.GuiGraphics graphics,
                                 net.minecraft.client.gui.Font font, int screenW,
                                 Component text, int x, int y) {
        int width = font.width(text);
        int clamped = Math.min(x, Math.max(4, screenW - 4 - width));
        graphics.drawString(font, text, clamped, y, 0xFFFFFFFF);
    }
}
