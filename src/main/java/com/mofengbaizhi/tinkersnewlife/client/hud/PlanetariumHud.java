package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 星象仪信息块（咒术 HUD 的<b>可独立拖动</b>附加块 ✓ 用户口径 ✓）。
 *
 * <p>只有<b>佩戴星象仪</b>时才显示 ✓（没戴 ⇒ 不画、也不占拖动框 ✓）。内容两行：
 * <pre>
 *   ☾ 今日：满月
 *     当前时间：21:30
 * </pre>
 * 第一行前面画<b>当日月相的小图标</b>（就是物品那 8 张图里对应的一张 ✓ 复用同一套贴图 ✓ 不额外画图 ✗）。
 *
 * <h2>数据来源（全部客户端本地 ✓ 零包 ✓）</h2>
 * <ul>
 *   <li>月相：{@code level.getMoonPhase()} ✓（0..7 ✓ 世界时间算出来的 ✓）</li>
 *   <li>游戏内时间：{@code level.getDayTime()} ✓ ⇒ {@code 06:00 = 天亮}（0 tick）✓
 *       换算 {@code ((dayTime % 24000) + 6000) % 24000} ⇒ 取 {@code hh:mm} ✓</li>
 *   <li>是否佩戴：{@code CuriosApi.getCuriosInventory(player)} 查 {@code charm} / {@code curio} 槽 ✓
 *       （照「心」的 {@code ConscienceHudOverlay} 同一套写法 ✓）</li>
 * </ul>
 */
public final class PlanetariumHud {

    /** 块宽（拖动命中框用 ✓；两行文字都不会超过它 ✓） */
    public static final int WIDTH = 112;
    /** 两种行高：图标行 16 / 文字行 10 */
    public static final int ICON_H = 16;
    public static final int TEXT_H = 10;

    /**
     * ⭐ 图标直接<b>渲染星象仪物品本身</b> ✓（用户口径：「HUD 直接用画好的物品材质不就行了，为什么还要自己再画一遍」✗）
     * <p>物品模型是<b>两层</b>（`layer0` 底盘 + `layer1` 星象图 ✓ 见 `models/item/planetarium.json`）✓
     * ⇒ 交给 {@link ItemRenderer} 画，HUD 就只有<b>一处</b>图标来源 ✓：
     * <ul>
     *   <li>连月相属性（`tinkersnewlife:planetarium` 谓词）也由渲染管线自己算 ✓ 这里不碰 ✓</li>
     *   <li>以后你把底盘/星象图画成什么样，HUD <b>自动一样</b> ✓ 永远不会两边不一致 ✓（自己再画一遍就一定会 ✗）</li>
     * </ul>
     */
    private static final ItemStack ICON = new ItemStack(ModItems.PLANETARIUM.get());

    private PlanetariumHud() {}

    // ============================================================
    //  位置（编辑界面里用实时值 ✓ 否则用配置值 ✓ 与咒力条同一套规矩）
    // ============================================================

    public static int baseX() {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof com.mofengbaizhi.tinkersnewlife.client.screen.CurseHudEditScreen edit) {
            return edit.getPlanetariumX();
        }
        return CurseHudConfig.getPlanetariumX();
    }

    public static int baseY() {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof com.mofengbaizhi.tinkersnewlife.client.screen.CurseHudEditScreen edit) {
            return edit.getPlanetariumY();
        }
        return CurseHudConfig.getPlanetariumY();
    }

    /** 整块高度（固定两行 ✓） */
    public static int totalHeight() {
        return ICON_H + TEXT_H;
    }

    // ============================================================
    //  是否该显示 / 该显示什么
    // ============================================================

    /** 玩家饰品槽里有没有星象仪（{@code charm} 与 {@code curio} 两个通用槽都查 ✓） */
    public static boolean hasPlanetarium(Player player) {
        if (player == null) return false;
        try {
            var inv = CuriosApi.getCuriosInventory(player).resolve();
            if (inv.isEmpty()) return false;
            for (top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler handler : inv.get().getCurios().values()) {
            var stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack s = stacks.getStackInSlot(i);
                if (!s.isEmpty() && s.getItem() == ModItems.PLANETARIUM.get()) return true;
            }
        }
        } catch (Throwable ignored) {
            // 饰品系统还没准备好 / 其它模组异常 ⇒ 当作没戴 ✓（HUD 不能因为查询失败崩掉 ✗）
        }
        return false;
    }

    /** 游戏内时间 ⇒ {@code hh:mm}（06:00 = 天亮 = dayTime 0 ✓） */
    public static String clock(Level level) {
        if (level == null) return "00:00";
        long t = level.getDayTime();
        long dayTicks = ((t % 24000L) + 24000L + 6000L) % 24000L;
        int hour = (int) (dayTicks / 1000L);
        int minute = (int) ((dayTicks % 1000L) * 60L / 1000L);
        return String.format("%02d:%02d", hour, minute);
    }

    // ============================================================
    //  绘制（HUD 与编辑界面共用同一份代码 ✓ 免得两边画得不一样 ✗）
    // ============================================================

    /** 只画内容（图标 + 两行字），不画编辑框 */
    public static void drawBox(GuiGraphics graphics, Font font, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        int phase = level == null ? 0 : level.getMoonPhase();

        // 第一行：物品图标（**直接渲染星象仪物品** ✓ 两层与月相都由物品模型自己负责 ✓）+「今日：<月相>」
        // ⚠ 物品图标是 16×16、文字行高只有 10 ⇒ 图标往上挪 4 像素才与第一行文字视觉居中对齐 ✓
        renderItemIcon(graphics, x, y - 4);
        Component today = Component.translatable("hud.tinkersnewlife.moon.today",
                com.mofengbaizhi.tinkersnewlife.content.item.PlanetariumItem.phaseName(phase));
        graphics.drawString(font, today, x + 18, y + 4, 0xE8E8FF);

        // 第二行：游戏内时间
        Component time = Component.translatable("hud.tinkersnewlife.moon.time", clock(level));
        graphics.drawString(font, time, x + 18, y + ICON_H + 1, 0xA8A8C0);
    }

    /**
     * 画那 16×16 的物品图标 —— <b>用原版的物品渲染管线</b> ✓ 不自己叠贴图 ✓。
     * <p>这样月相谓词（`tinkersnewlife:planetarium`）也由管线自己求值 ✓
     * ⇒ HUD 与物品栏/手上的图标<b>同源</b> ✓ 以后换素材无需改 HUD ✓。
     */
    private static void renderItemIcon(GuiGraphics graphics, int x, int y) {
        graphics.renderItem(ICON, x, y);
    }

    /** 编辑模式的外框（拖动命中看得见 ✓ 用色与咒力条的编辑框一致 ✓ 一眼看出是同一套东西 ✓） */
    public static void drawEditFrame(GuiGraphics graphics, int x, int y) {
        int w = WIDTH, h = totalHeight();
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x3300E5FF);
        // 虚线框
        for (int i = x - 2; i < x + w + 2; i += 4) {
            graphics.fill(i, y - 2, Math.min(i + 2, x + w + 2), y - 1, 0xFF66E0FF);
            graphics.fill(i, y + h + 1, Math.min(i + 2, x + w + 2), y + h + 2, 0xFF66E0FF);
        }
        for (int j = y - 2; j < y + h + 2; j += 4) {
            graphics.fill(x - 2, j, x - 1, Math.min(j + 2, y + h + 2), 0xFF66E0FF);
            graphics.fill(x + w + 1, j, x + w + 2, Math.min(j + 2, y + h + 2), 0xFF66E0FF);
        }
    }

    /** 拖动命中判断（编辑界面用 ✓ 比视觉框略宽松一点，好抓 ✓） */
    public static boolean inside(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x - 3 && mouseX <= x + WIDTH + 3
                && mouseY >= y - 3 && mouseY <= y + totalHeight() + 3;
    }

    // ============================================================
    //  实际 HUD 入口（没戴星象仪 ⇒ 直接返回 ✓）
    // ============================================================

    public static void render(GuiGraphics graphics, float partialTick) {
        if (!CurseHudConfig.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.level == null) return;
        if (!hasPlanetarium(mc.player)) return;      // 只有装配时才显示 ✓
        drawBox(graphics, mc.font, baseX(), baseY());
    }
}
