package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientTitles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** §1305 头衔选择界面：点一下切换是否展示（★最多 3 枚 ✓ 底部提交 ✓）。 */
public class TitlesSelectScreen extends Screen {

    private final Screen parent;
    private final List<String> pick = new ArrayList<>(ClientTitles.selected);

    public TitlesSelectScreen(Screen parent) {
        super(Component.translatable("screen.tinkersnewlife.titles"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int col = 0;
        int row = 0;
        for (String id : ClientTitles.held) {
            final String titleId = id;
            int x = this.width / 2 - 154 + col * 156;
            int y = 40 + row * 24;
            this.addRenderableWidget(Button.builder(label(titleId), b -> {
                if (pick.contains(titleId)) {
                    pick.remove(titleId);
                } else if (pick.size() < 3) {
                    pick.add(titleId);
                }
                b.setMessage(label(titleId));
            }).bounds(x, y, 150, 20).build());
            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            TinkersNewlife.CHANNEL.sendToServer(
                    new com.mofengbaizhi.tinkersnewlife.network.curse.PacketTitlesSelect(pick));
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private Component label(String id) {
        String mark = pick.contains(id) ? "\u2714 " : "  ";
        return Component.literal(mark).append(Component.translatable("title.tinkersnewlife." + id));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("screen.tinkersnewlife.titles.hint"),
                this.width / 2, 28, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }
}