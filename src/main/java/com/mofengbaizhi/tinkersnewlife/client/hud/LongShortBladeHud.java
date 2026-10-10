package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ⭐ §1124 <b>长短刃「fever」进度条 HUD</b>（用户口径 ✓「fever 以进度条形式显示在玩家**准星下方一点点**，
 * 类似原版攻击指示器，但是位置在**指示器下方**」✓）。
 *
 * <h2>⚠ 不含任何输入/网络（⚠ 用户硬规矩 ✓）</h2>
 * 本类**只画 HUD** ✗ —— 不加键位 ✗ 不发包 ✗（F 键就是原版 {@code key.swapOffhand} ✓
 * 形态同步由 {@code LongShortBladeHandler.maintainPair} 每 tick 做 ✓）。
 *
 * <h2>⭐ 定位公式（反编译原版 {@code Gui} 得到的真值 ✓ 不是估的 ✓）</h2>
 * 原版 {@code Gui#renderCrosshair}（SRG {@code m_280130_} ✓ 第 410 行起 ✓）里，**准星与攻击指示器是同一个方法** ✓：
 * <pre>
 *   // 准星贴图 15x15
 *   blit(ICONS, (w - 15) / 2, (h - 15) / 2, 0, 0, 15, 15);
 *   if (attackIndicator == CROSSHAIR) {
 *       int y = h / 2 - 7 + 16;          // ⭐ = h/2 + 9      ⇒ 指示器顶部
 *       int x = w / 2 - 8;               // ⭐ = w/2 - 8
 *       if (满蓄力 && 瞄着活物) blit(x, y, 68, 94, 16, 16);   // ⚠ 16 高 ⇒ 占到 h/2 + 25 ✗
 *       else if (scale &lt; 1)  { blit(x, y, 36, 94, 16, 4);     // 平时是 16x4
 *                              blit(x, y, 52, 94, scale*17, 4); }
 *   }
 * </pre>
 * ⇒ ⭐ <b>本 HUD 的公式</b>：
 * <pre>
 *   x = (width  - BAR_W) / 2                              // BAR_W = 18 ⇒ w/2 - 9（比原版 16 宽 2 ✓ 居中 ✓）
 *   y = height / 2 + 9 + 16 + 1                           // = h/2 + 26
 *           ↑          ↑    ↑
 *       准星中心   指示器顶  满蓄力图标高度(16)   1px 间隙
 * </pre>
 * ⚠ **为什么取 +16 而不是"紧紧贴着 4 高的普通条"** ✗：原版在"满蓄力且准星瞄着活物"时会画**16×16**
 * （第 445 行 ✓），占到 {@code h/2+25} ✓ —— 若把本条放在 {@code h/2+15} ✗ 就会被那个图标**压住** ✗
 * （⚠ 层序上我方在 overlay 之后 ⇒ 会**反过来盖住原版图标** ✗ 更难看 ✓）
 * ⇒ ⭐ 取 {@code h/2+26} 可保证**与原版指示器任何形态都不重叠** ✓。
 * ⚠ 若用户实测觉得"离得太远" ✗ ⇒ 把 {@link #GAP} 调小或直接把 {@link #INDICATOR_TOP}＋{@link #INDICATOR_MAX_H}
 * 换成 4 即可（一行事 ✓ 已在此说明 ✓）。
 *
 * <h2>⭐ 数据来源与"读哪一只手"（⚠ 查过 handler 才定的 ✓）</h2>
 * fever 存在**物品自己的 NBT**（{@code lnb_fever} ✓）✓，而 {@code LongShortBladeHandler.onHurt}
 * 里**只往主手那一把写**（{@code weapon = player.getMainHandItem()} 第 232 行 ✓ 写在第 246 / 260 行 ✓），
 * 副手那把只是 {@code lnb_pair} 伙伴刀（{@code maintainPair} **只同步形态 ✗ 不同步 fever** ✗）✓
 * ⇒ ⭐ 本 HUD **优先读主手** ✓；主手不是这把武器时才退到副手 ✓（方便"只拿在副手"时也能看到 ✓）。
 * ⚠ **刻意不取两手最大值** ✗ —— 那样会显示一个**主手那把其实没有**的数值 ✓
 * ⇒ 会出现"条是满的但突刺/光环按不出来"的**误导** ✗。
 *
 * <h2>形态限制（⚠ 这是我的判断 ✓ 已在回信说明 ✗）</h2>
 * ⭐ <b>长刀与短刀两种形态都显示</b> ✓（{@link #REQUIRE_LONG_FORM} 默认 {@code false}）：
 * 短刀形态同样在攒 fever ✓ 且**杀戮光环两种形态都能放** ✓ ⇒ 只显示长刀会让短刀玩家看不到"能不能放大招" ✗。
 * ⚠ 若要严格照用户字面"只在长刀形态显示" ✗ ⇒ 把该常量改成 {@code true} 即可（一行 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LongShortBladeHud {

    private LongShortBladeHud() {}

    // ============================================================
    //  尺寸与定位（⚠ 全部是常量 ✓ 要调只动这里 ✓）
    // ============================================================

    /** 条外框宽（含 1px 边框 ✓；比原版指示器的 16 宽 2px ✓ 居中后两边各多 1px ✓） */
    private static final int BAR_W = 18;
    /** 条外框高（含 1px 边框 ✓；内条正好 4px 与原版一样高 ✓） */
    private static final int BAR_H = 6;
    /** 内条宽（前景最多画这么宽 ✓） */
    private static final int INNER_W = BAR_W - 2;
    /** 内条高 ✓ */
    private static final int INNER_H = BAR_H - 2;

    /** ⭐ 原版攻击指示器顶部相对屏幕中心的偏移（反编译实证 ✓ {@code h/2 - 7 + 16} ✓） */
    private static final int INDICATOR_TOP = 9;
    /** ⭐ 原版满蓄力图标的高度（16 ✓ 最坏情况占到这里 ✓） */
    private static final int INDICATOR_MAX_H = 16;
    /** ⭐ 再留 1px 间隙 ✓ */
    private static final int GAP = 1;

    /** ⭐ 是否"只在长刀形态显示"（⚠ 默认 false ＝ 两种形态都显示 ✓ 见类注释 ✓） */
    private static final boolean REQUIRE_LONG_FORM = false;

    // ============================================================
    //  配色（ARGB ✓）
    // ============================================================

    /** 外框（细边框 ✓ 浅灰） */
    private static final int COLOR_BORDER = 0xC0A0A0A0;
    /** 内底（暗 ✓ 半透明黑） */
    private static final int COLOR_BG = 0xC0101010;
    /** 阈值刻度（比底色稍亮一点点 ✓ 只画在内底上 ✓） */
    private static final int COLOR_MARK = 0x60FFFFFF;
    /** 未达突刺门槛（< 20 ✓ 暗红） */
    private static final int COLOR_LOW = 0xFFB03030;
    /** 可突刺（≥ 20 ✓ 橙） */
    private static final int COLOR_THRUST = 0xFFE07020;
    /** ⭐ 可放杀戮光环（≥ 50 ✓ 金） */
    private static final int COLOR_ULTIMATE = 0xFFFFC030;

    // ============================================================
    //  绘制
    // ============================================================

    /**
     * ⚠ 挂在**准星 overlay 之后** ✓（与 {@code EnergyConverterHud} / {@code PedestalChargeHud} 同一套 ✓）——
     * 反编译实证：准星与攻击指示器**都在** {@code Gui#renderCrosshair} 里 ✓
     * ⇒ ⭐ 本回调必然画在两者**之上** ✓ 不会被打架 ✗。
     */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        try {
            ItemStack blade = heldBlade(player);
            if (blade.isEmpty()) {
                return;   // ⭐ 没拿长短刃 ⇒ 整条不出现 ✓（用户口径 ✓）
            }
            if (REQUIRE_LONG_FORM && !LongShortBladeItem.isLong(blade)) {
                return;
            }
            int fever = LongShortBladeItem.getFever(blade);
            draw(event.getGuiGraphics(), mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight(), fever);
        } catch (Throwable ignored) {
            // fail-safe：HUD 出错绝不崩客户端 ✓（照本仓其它 HUD 口径 ✓）
        }
    }

    /**
     * ⭐ 找出"该显示哪一把" ✓：**主手优先** ✓（fever 只往主手写 ✓ 见类注释 ✓），主手不是才看副手 ✓。
     * <p>⚠ 返回的是**真实引用**（不是 copy ✓）⇒ 读到的就是 authority 值 ✓。
     */
    private static ItemStack heldBlade(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof LongShortBladeItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof LongShortBladeItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    /** 画一条"原版攻击指示器风格"的细框进度条 ✓（无任何文字 ⇒ 不需要语言键 ✓） */
    private static void draw(GuiGraphics graphics, int screenW, int screenH, int fever) {
        int x = (screenW - BAR_W) / 2;
        int y = screenH / 2 + INDICATOR_TOP + INDICATOR_MAX_H + GAP;

        // ① 外框（细边框 ✓）
        graphics.fill(x, y, x + BAR_W, y + BAR_H, COLOR_BORDER);
        // ② 内底（暗 ✓）
        int ix = x + 1;
        int iy = y + 1;
        graphics.fill(ix, iy, ix + INNER_W, iy + INNER_H, COLOR_BG);

        // ③ 阈值刻度（20 ＝ 可突刺 ✓ 50 ＝ 可放光环 ✓）—— 只画在内底上 ✓ 免得盖住前景 ✗
        drawMark(graphics, ix, iy, LongShortBladeItem.FEVER_COST_THRUST);
        drawMark(graphics, ix, iy, LongShortBladeItem.FEVER_COST_ULTIMATE);

        // ④ 前景（按 fever 比例 ✓ 宽度四舍五入 ✓ 0 就不画 ✓）
        int filled = (int) Math.round(INNER_W * (double) fever / (double) LongShortBladeItem.FEVER_MAX);
        if (filled <= 0) {
            return;
        }
        filled = Math.min(INNER_W, filled);
        graphics.fill(ix, iy, ix + filled, iy + INNER_H, colorFor(fever));
    }

    /** 把某个阈值画成内底上的一根 1px 竖线 ✓（位置按比例算 ✓） */
    private static void drawMark(GuiGraphics graphics, int ix, int iy, int threshold) {
        int at = (int) Math.round(INNER_W * (double) threshold / (double) LongShortBladeItem.FEVER_MAX);
        if (at <= 0 || at >= INNER_W) {
            return;
        }
        graphics.fill(ix + at, iy, ix + at + 1, iy + INNER_H, COLOR_MARK);
    }

    /**
     * ⭐ 颜色随门槛变化（⚠ 这是**额外**给的功能 ✓ 用户只要求"进度条" ✓）：
     * 未达突刺门槛＝暗红 ✓ 可突刺＝橙 ✓ **可放杀戮光环＝金** ✓
     * ⇒ 玩家不用记数字就能看出"现在能放什么" ✓。
     */
    private static int colorFor(int fever) {
        if (fever >= LongShortBladeItem.FEVER_COST_ULTIMATE) {
            return COLOR_ULTIMATE;
        }
        if (fever >= LongShortBladeItem.FEVER_COST_THRUST) {
            return COLOR_THRUST;
        }
        return COLOR_LOW;
    }
}
