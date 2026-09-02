package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.PacketWuWeiSelect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 无为转变 形态选择界面：
 * 列出玩家击杀记录中的生物（EntityType id）。点击一项 → 记录为当前形态并关闭 UI，
 * 随后按术式键 C 对自己施放顺转 / 按 F 对目标施放反转。
 */
public class WuWeiScreen extends Screen {

    private record Slot(int index, int x, int y, int w, int h) {}

    private final List<String> forms;
    private final List<Slot> slots = new ArrayList<>();
    private static final int W = 180;
    private static final int H = 22;
    private static final int GAP = 4;

    public WuWeiScreen(List<String> forms) {
        super(Component.translatable("screen.tinkersnewlife.wu_wei"));
        this.forms = forms == null ? new ArrayList<>() : forms;
    }

    @Override
    protected void init() {
        slots.clear();
        int startX = (width - W) / 2;
        int startY = 48;
        for (int i = 0; i < forms.size(); i++) {
            slots.add(new Slot(i, startX, startY + i * (H + GAP), W, H));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.wu_wei.title"),
                (width - font.width(Component.translatable("screen.tinkersnewlife.wu_wei.title"))) / 2, 12, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.wu_wei.hint"),
                (width - font.width(Component.translatable("screen.tinkersnewlife.wu_wei.hint"))) / 2, 26, 0xAAAAAA);
        if (forms.isEmpty()) {
            String noData = Component.translatable("screen.tinkersnewlife.wu_wei.empty").getString();
            graphics.drawString(font, noData, (width - font.width(noData)) / 2, 60, 0xFF5555);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        for (Slot s : slots) {
            boolean hover = mouseX >= s.x && mouseX <= s.x + s.w && mouseY >= s.y && mouseY <= s.y + s.h;
            graphics.fill(s.x, s.y, s.x + s.w, s.y + s.h, hover ? 0xFF4A4A6A : 0xFF33334A);
            String name = displayName(forms.get(s.index));
            graphics.drawString(font, name, s.x + 6, s.y + 6, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Slot s : slots) {
            if (mouseX >= s.x && mouseX <= s.x + s.w && mouseY >= s.y && mouseY <= s.y + s.h) {
                String formId = forms.get(s.index);
                TinkersNewlife.CHANNEL.sendToServer(new PacketWuWeiSelect(formId));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 形态显示名：EntityType 本地化键，找不到则显示注册名 */
    private static String displayName(String entityTypeId) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(entityTypeId));
        if (type == null) return entityTypeId;
        return Component.translatable(type.getDescriptionId()).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
