package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.client.data.CursedSpeechClientData;
import com.mofengbaizhi.tinkersnewlife.content.cursespeech.CursedSpeechRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 咒言编辑界面（反转键 F 打开）：
 * 六行 = 六段（咏叹词/敬称/对象/祈求语/核心义/结谢语），每行显示当前词条；
 * 点击某行 → 进入该段词库选择（{@link CursedWordPickerScreen}），选定即保存并刷新预览。
 * 顶部实时预览整句咒言。
 */
public class CursedSpeechScreen extends AbstractRowListScreen<Integer> {

    private static final int W = 300;
    private static final int ROW_H = 28;
    private static final int LIST_TOP = 74;
    private static final int BOTTOM_PAD = 20;

    private final List<String> learned;

    public CursedSpeechScreen(List<String> learned, List<String> chant) {
        super(Component.translatable("screen.tinkersnewlife.cursed_speech.title"),
                List.of(0, 1, 2, 3, 4, 5), W, ROW_H, ROW_H, LIST_TOP, BOTTOM_PAD);
        this.learned = learned == null ? List.of() : learned;
        CursedSpeechClientData.set(learned, chant);
    }

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.cursed_speech.title"), 12, 0xFFFFFF);
        // 咒言实时预览
        Component preview = Component.literal(buildChant());
        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.cursed_speech.preview"),
                20, 30, 0x9A9A9A);
        graphics.drawString(font, preview, 20, 44, 0xFFD4924B);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.cursed_speech.hint"), 62, 0x9A9A9A);
    }

    @Override
    protected void drawRow(GuiGraphics graphics, Integer index, int row, int x, int y, int w, int h,
                           boolean hover, double mouseX, double mouseY) {
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF4A4A6A : 0xFF33334A);
        CursedSpeechRegistry.Part part = CursedSpeechRegistry.Part.values()[index];
        Component partName = Component.translatable("screen.tinkersnewlife.cursed_speech.part_" + part.name().toLowerCase());
        graphics.drawString(font, partName, x + 10, y + 8, 0x7CFF7C);
        String wordId = CursedSpeechClientData.part(index);
        Component wordName = wordId == null || wordId.isEmpty()
                ? Component.translatable("screen.tinkersnewlife.cursed_speech.none").withStyle(s -> s.withColor(0xE05555))
                : Component.translatable(CursedSpeechRegistry.get(wordId).langKey());
        int color = wordId == null || wordId.isEmpty() ? 0xE05555 : 0xFFFFFF;
        graphics.drawString(font, wordName, x + 90, y + 8, color);
        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.cursed_speech.click"),
                x + w - 12 - font.width(Component.translatable("screen.tinkersnewlife.cursed_speech.click")),
                y + 8, 0x9A9A9A);
    }

    @Override
    protected void onRowClick(int index, Integer row) {
        CursedSpeechRegistry.Part part = CursedSpeechRegistry.Part.values()[index];
        // 该段已学词条
        List<String> candidates = learned.stream()
                .map(CursedSpeechRegistry::get)
                .filter(w -> w != null && w.part() == part)
                .map(CursedSpeechRegistry.Word::id)
                .toList();
        if (candidates.isEmpty()) {
            // 无已学 → 提示
            return;
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new CursedWordPickerScreen(part, candidates, index));
    }

    /** 拼当前咒言展示文本 */
    private String buildChant() {
        StringBuilder sb = new StringBuilder();
        String[] chant = new String[6];
        for (int i = 0; i < 6; i++) chant[i] = CursedSpeechClientData.part(i);
        if (chant[0].isEmpty()) return Component.translatable("screen.tinkersnewlife.cursed_speech.incomplete").getString();
        sb.append(name(chant[0])).append("，");
        if (!chant[1].isEmpty()) sb.append(name(chant[1]));
        if (!chant[2].isEmpty()) sb.append(name(chant[2]));
        sb.append("，");
        if (!chant[3].isEmpty()) sb.append(name(chant[3]));
        if (!chant[4].isEmpty()) sb.append(name(chant[4]));
        sb.append("，");
        if (!chant[5].isEmpty()) sb.append(name(chant[5]));
        sb.append("！");
        return sb.toString();
    }

    private static String name(String id) {
        CursedSpeechRegistry.Word w = CursedSpeechRegistry.get(id);
        return w == null ? "?" : Component.translatable(w.langKey()).getString();
    }

    private void drawCentered(GuiGraphics graphics, Component c, int y, int color) {
        graphics.drawString(font, c, (width - font.width(c)) / 2, y, color);
    }
}
