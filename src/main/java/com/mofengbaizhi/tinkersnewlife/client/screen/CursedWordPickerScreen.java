package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.CursedSpeechClientData;
import com.mofengbaizhi.tinkersnewlife.content.cursespeech.CursedSpeechRegistry;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketCursedSpeechSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 咒言某段词库选择：列出该段所有已学词条（按稀有度排序），点击即选定并返回编辑屏。
 */
public class CursedWordPickerScreen extends AbstractRowListScreen<String> {

    private static final int W = 280;
    private static final int ROW_H = 24;
    private static final int LIST_TOP = 40;
    private static final int BOTTOM_PAD = 16;

    private final CursedSpeechRegistry.Part part;
    private final int chantIndex;

    public CursedWordPickerScreen(CursedSpeechRegistry.Part part, List<String> candidates, int chantIndex) {
        super(Component.translatable("screen.tinkersnewlife.cursed_speech.picker_title"),
                candidates, W, ROW_H, ROW_H, LIST_TOP, BOTTOM_PAD);
        this.part = part;
        this.chantIndex = chantIndex;
    }

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        Component title = Component.translatable(
                "screen.tinkersnewlife.cursed_speech.part_" + part.name().toLowerCase());
        graphics.drawString(font, title, (width - font.width(title)) / 2, 12, 0xFFFFFF);
        Component hint = Component.translatable("screen.tinkersnewlife.cursed_speech.picker_hint");
        graphics.drawString(font, hint, (width - font.width(hint)) / 2, 26, 0x9A9A9A);
    }

    @Override
    protected void drawRow(GuiGraphics graphics, String wordId, int index,
                           int x, int y, int w, int h, boolean hover,
                           double mouseX, double mouseY) {
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF4A4A6A : 0xFF33334A);
        CursedSpeechRegistry.Word word = CursedSpeechRegistry.get(wordId);
        if (word == null) return;
        Component name = Component.translatable(word.langKey());
        graphics.drawString(font, name, x + 10, y + 7, 0xFFFFFF);
        // 右侧：稀有度标记
        String rarity = "★".repeat(Math.max(1, word.rarity() + 1));
        int color = rarityColor(word.rarity());
        graphics.drawString(font, rarity, x + w - 14 - font.width(rarity), y + 7, color);
        // 当前选中的段高亮左缘
        if (word.id().equals(CursedSpeechClientData.part(chantIndex))) {
            graphics.fill(x, y, x + 2, y + h, 0xFF7CFF7C);
        }
        // hover：显示该词条效果说明（核心义/对象有专属说明，其余显示段位通用说明）
        if (hover) {
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.translatable(word.langKey()).withStyle(s -> s.withColor(0xFFFFFF)));
            lines.add(Component.translatable("screen.tinkersnewlife.cursed_speech.rarity", word.rarity() + 1)
                    .withStyle(s -> s.withColor(rarityColor(word.rarity()))));
            lines.add(Component.translatable(effectDescKey(word)));
            graphics.renderTooltip(font, lines,
                    java.util.Optional.<net.minecraft.world.inventory.tooltip.TooltipComponent>empty(),
                    (int) mouseX, (int) mouseY);
        }
    }

    /** 词条效果说明 lang key：优先词条专属，缺失回退段位通用 */
    private static String effectDescKey(CursedSpeechRegistry.Word word) {
        String specific = "word.tinkersnewlife." + word.id() + ".desc";
        String partCommon = "word.tinkersnewlife.part_" + word.part().name().toLowerCase() + ".desc";
        // 资源翻译键不能动态探测存在性，采用统一策略：核心义/对象用专属键，其余用段位通用键
        if (word.part() == CursedSpeechRegistry.Part.CORE
                || word.part() == CursedSpeechRegistry.Part.TARGET) {
            return specific;
        }
        return partCommon;
    }

    private static int rarityColor(int rarity) {
        return switch (rarity) {
            case 0, 1 -> 0xFFFFFF;
            case 2 -> 0x55FFFF;
            case 3 -> 0xAA55FF;
            case 4 -> 0xFFAA00;
            default -> 0xFF5555;
        };
    }

    @Override
    protected void onRowClick(int index, String row) {
        TinkersNewlife.CHANNEL.sendToServer(new PacketCursedSpeechSelect(chantIndex, row));
        CursedSpeechClientData.setPart(chantIndex, row);
        // 返回编辑屏（本地缓存已更新）
        Minecraft.getInstance().setScreen(new CursedSpeechScreen(
                CursedSpeechClientData.learned(),
                java.util.Arrays.asList(
                        CursedSpeechClientData.part(0), CursedSpeechClientData.part(1),
                        CursedSpeechClientData.part(2), CursedSpeechClientData.part(3),
                        CursedSpeechClientData.part(4), CursedSpeechClientData.part(5))));
    }
}
