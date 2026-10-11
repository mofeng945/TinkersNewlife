package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientTitles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * §1326 头衔选择界面：**从上到下竖排** ＋ **滚轮翻页**。
 *
 * <p>规则：点一下切换是否展示 ✓ 最多 3 枚 ✓ 底部"完成"提交 ✗
 * "恢复自动"提交空选择（⇒ 服务端回退成"稀有度前 3"✓）。
 * 头衔多了不用怕：滚轮上下卷 ✓ 同时显示"当前范围/总数"。
 */
public class TitlesSelectScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int LIST_TOP = 48;

    private final Screen parent;
    private final List<String> pick = new ArrayList<>();
    private boolean pickLoaded = false;
    private int firstVisible = 0;

    public TitlesSelectScreen(Screen parent) {
        super(Component.translatable("screen.tinkersnewlife.titles"));
        this.parent = parent;
    }

    private int rowsPerPage() {
        return Math.max(1, (this.height - LIST_TOP - 46) / ROW_H);
    }

    @Override
    protected void init() {
        // ★只在第一次读"当前选中"（★rebuildWidgets 会再进 init ✗ ⭐ 不能被清掉 ✓）
        if (!pickLoaded) {
            pick.clear();
            pick.addAll(ClientTitles.selected);
            pickLoaded = true;
        }
        int rows = rowsPerPage();
        int total = ClientTitles.held.size();
        firstVisible = Mth.clamp(firstVisible, 0, Math.max(0, total - rows));

        int y = LIST_TOP;
        for (int i = firstVisible; i < Math.min(total, firstVisible + rows); i++) {
            final String titleId = ClientTitles.held.get(i);
            this.addRenderableWidget(Button.builder(label(titleId), b -> {
                if (pick.contains(titleId)) {
                    pick.remove(titleId);
                } else if (pick.size() < 3) {
                    pick.add(titleId);
                }
                b.setMessage(label(titleId));
            }).bounds(this.width / 2 - 80, y, 160, 20).build());
            y += ROW_H;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            TinkersNewlife.CHANNEL.sendToServer(
                    new com.mofengbaizhi.tinkersnewlife.network.curse.PacketTitlesSelect(pick));
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }).bounds(this.width / 2 - 124, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.tinkersnewlife.titles.auto"), b -> {
            TinkersNewlife.CHANNEL.sendToServer(
                    new com.mofengbaizhi.tinkersnewlife.network.curse.PacketTitlesSelect(new ArrayList<>()));
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }).bounds(this.width / 2 + 24, this.height - 30, 100, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int rows = rowsPerPage();
        int total = ClientTitles.held.size();
        int next = Mth.clamp(firstVisible - (int) Math.signum(delta), 0, Math.max(0, total - rows));
        if (next != firstVisible) {
            firstVisible = next;
            this.rebuildWidgets();
        }
        return true;
    }

    private Component label(String id) {
        String mark = pick.contains(id) ? "\u2714 " : "  ";
        // §1328 按稀有度上色（★稀有度表在通用类里 ✗ ⭐ 客户端直接读 ✓）
        return Component.literal(mark).withStyle(net.minecraft.ChatFormatting.GRAY)
                .append(com.mofengbaizhi.tinkersnewlife.content.curse.CurseTitles.colored(id));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("screen.tinkersnewlife.titles.hint", pick.size()),
                this.width / 2, 30, 0xA0A0A0);
        int rows = rowsPerPage();
        int total = ClientTitles.held.size();
        if (total > rows) {
            int from = firstVisible + 1;
            int to = Math.min(total, firstVisible + rows);
            g.drawCenteredString(this.font, Component.literal("\u25b2 " + from + "-" + to + " / " + total + " \u25bc"),
                    this.width / 2, this.height - 44, 0x8080FF);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}