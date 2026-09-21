package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 墨默的**对话树**（用户口径 §455 D/E ✓ 文案逐字照抄 ✓ **立绘 + 九宫格气泡版** ✓）。
 *
 * <ul>
 *   <li><b>立绘</b>：右侧、底部对齐、按屏幕高等比缩放 ✓ 表情按好感档位切换 ✓
 *       （0/10 normal ✓ 20 happy ✓ 30 blush ✓ 40 awkward ✓ 50 surprised ✓）；</li>
 *   <li><b>气泡</b>：九宫格贴图 {@code bubble_momo.png}（半径 9 / 19×19 ✓）⇒ 任意长回答都能撑开 ✓ 内边距 9px ✓；</li>
 *   <li><b>选项</b>：{@code bubble_option.png} 九宫格按钮 ✓ 悬停叠亮层 ✓ **未达好感整档不显示** ✓ 超出可滚轮 ✓；</li>
 *   <li><b>打字机</b>：每 tick +1.5 字 ✓ **空格跳过**动画 ✓ **打完后空格回到选项页** ✓；**回退按钮每屏都有** ✓；</li>
 *   <li>背景压暗一层半透明黑 ✓（有立绘这样才看得清 ✓）。</li>
 * </ul>
 */
public class MomoTalkScreen extends Screen {

    private static final int[] TIER = { 0, 10, 20, 30, 40, 50 };

    private static final String[] GREET = {
            "啊……请问你想知道些什么呢？我会尽可能回答你……",
            "啊，今天你又想知道些什么呢？",
            "嗯嗯，我会尽量回答你的！",
            "聊聊天吧？",
            "哎呀，%s，想知道什么直接问我就好啦~",
            "今天有什么收获？又想聊聊天吗？"
    };

    private static final String[][] QUESTIONS = {
            { "你是……？", "这里是什么地方？", "这个世界为什么和我想象中不太一样？" },
            { "关于你的穿着？", "你的喜好？", "关于这个世界？" },
            { "关于咒术？", "什么是高纬度存在？", "你的镰刀？" },
            { "为什么收集格赫罗斯？", "你的教会？", "关于我？" },
            { "为什么格赫罗斯会散落？", "为什么我没有看到很多克苏鲁变化的生物？", "能免费吗？" },
            { "你的计划？", "真相……", "未来？" }
    };

    private static final String[][] ANSWERS = {
            {
                    "我叫墨默，目前是个……武器商人？你会有什么需要的吗？",
                    "这是崭新世界啊，看来又是一位刚刚降临的半神呢……",
                    "emm，这个世界出现了一些不太好的变故，为此我也很苦恼呢……"
            },
            {
                    "啊，其实我同时还是一个教会的圣女，目前正在游荡大地寻找一些东西。",
                    "emm，私密问题，诶嘿嘿？",
                    "相信你也发现了，这个世界正在被一些高维度的存在入侵……怪物们变强，许多古老的技艺和武器出现，"
                            + "伴随着还有一些奇怪的力量体系……尽快变强吧，不然你可能会后悔……"
            },
            {
                    "啊，这是我用这个世界新出现的力量搓出来的模仿产物，和原版差距有点大，你先凑活着用用吧？",
                    "就，克，克苏鲁神化你总听说过吧？里面的外神……类似于那样的……名字？名字说出来你可能要掉san的，笨蛋！",
                    "这把镰刀啊？其实不是按你那样子把格赫罗斯矿石浇在铁器上制造的。这把镰刀的本体是我的女朋友送给我的生日礼物，"
                            + "我只是把我共生的格赫罗斯附着在上面加效果而已，诶嘿嘿~"
            },
            {
                    "啊，因为格赫罗斯最后一片有意识的部分和我共生，要获得能够击败邪神的力量就得找回这些身躯碎片……",
                    "emm，那是名为星梦教的额……教会？说起来你可能不信，我是盲目痴愚之神派来杀打扰祂清梦的邪神的（",
                    "不知道呢，这片界域里有大量类似的小世界，我遇到了很多和你相似的存在，世界意志告诉我你们是复苏的半神……"
                            + "我也不知道什么是半神呀？"
            },
            {
                    "父神大人说是这群邪神因为忌惮格赫罗斯的力量合力将它击碎了……",
                    "这片界域的规则很有秩序，那群邪神力量有限，所以做不到呢……",
                    "哈？你在说什么小猪话？我一个卖武器的免费送你东西？"
            },
            {
                    "躯体收集的差不多了呢，可能马上就要和邪神们开战了吧。",
                    "其实这里和你交流的只是一具分身……很抱歉这段日子有些敷衍你了……"
                            + "想必你也亲身感受过邪神力量，那些恶念缠身的时刻……\n"
                            + "如果我将邪神击败了，所有的污染事件也会随之消失，为此，我希望你能与我站在同一战线。\n"
                            + "我不想与你为敌……",
                    "成功之后，我可能会回我的世界吧，至于失败……我想也不能失败了……"
            }
    };

    /** 立绘：每档一张 + 原始尺寸（`blit` 缩放要显式给纹理尺寸；六张尺寸不全一样 ⇒ 查表 ✓） */
    private static final String[] PORTRAIT = {
            "momo_normal", "momo_normal", "momo_happy", "momo_blush", "momo_awkward", "momo_surprised"
    };
    private static final int[][] PORTRAIT_SIZE = {
            { 363, 800 }, { 363, 800 }, { 587, 800 }, { 587, 800 }, { 587, 800 }, { 587, 800 }
    };

    /** 九宫格气泡（自绘纯色版 ✓ 手绘同路径同名即可替换 ✓） */
    private static final ResourceLocation BUBBLE = new ResourceLocation(TinkersNewlife.MOD_ID, "textures/gui/momo/bubble_momo.png");
    private static final ResourceLocation OPTION = new ResourceLocation(TinkersNewlife.MOD_ID, "textures/gui/momo/bubble_option.png");
    private static final int NINE = 19;      // 贴图边长
    private static final int RADIUS = 9;     // 圆角半径（= 四角切片大小 ✓）
    private static final int PAD = 9;        // 气泡内边距
    private static final int ROW_H = 24;

    private record Entry(String q, String a, int tier) {}

    private final int favor;
    private final String playerName;
    private final List<Entry> entries = new ArrayList<>();

    private int page = 0;                    // 0 = 选项页；>0 = 看第 (page-1) 条回答
    private float reveal = 0F;
    private String answerText = "";
    private int scroll = 0;
    private int maxScroll = 0;

    private int panelX, panelW;              // 左侧文字区（右侧留给立绘）
    private int listY, listH;
    private int backX, backY;

    public MomoTalkScreen(int favor, String playerName) {
        super(Component.translatable("menu.tinkersnewlife.momo"));
        this.favor = favor;
        this.playerName = playerName == null ? "" : playerName;
        for (int t = 0; t < TIER.length; t++) {
            if (favor < TIER[t]) continue;
            for (int i = 0; i < QUESTIONS[t].length; i++) {
                entries.add(new Entry(QUESTIONS[t][i], ANSWERS[t][i], t));
            }
        }
    }

    private int tierIndex() {
        int best = 0;
        for (int t = 0; t < TIER.length; t++) if (favor >= TIER[t]) best = t;
        return best;
    }

    private String greeting() {
        return String.format(GREET[tierIndex()], playerName);
    }

    private boolean typing() { return page > 0 && reveal < answerText.length(); }

    private boolean hovering(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void init() {
        super.init();
        int portraitW = Math.min((int) (this.width * 0.42F), 420);   // 立绘占右侧约 42% 宽
        panelX = 24;
        panelW = Math.max(200, this.width - portraitW - 48);
        listY = (int) (this.height * 0.30F);
        listH = (int) (this.height * 0.58F);
        backX = panelX;
        backY = this.height - 34;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (page > 0 && reveal < answerText.length()) reveal = Math.min(answerText.length(), reveal + 1.5F);
    }

    /** 九宫格拼装（半径 9 / 19×19 ✓ 中间 1 行/列拉伸 ✓） */
    private void nine(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        int r = RADIUS, t = NINE;
        w = Math.max(w, r * 2 + 2);
        h = Math.max(h, r * 2 + 2);
        g.blit(tex, x, y, 0F, 0F, r, r, t, t);
        g.blit(tex, x + w - r, y, (float) (t - r), 0F, r, r, t, t);
        g.blit(tex, x, y + h - r, 0F, (float) (t - r), r, r, t, t);
        g.blit(tex, x + w - r, y + h - r, (float) (t - r), (float) (t - r), r, r, t, t);
        g.blit(tex, x + r, y, (float) r, 0F, w - r * 2, r, t, t);
        g.blit(tex, x + r, y + h - r, (float) r, (float) (t - r), w - r * 2, r, t, t);
        g.blit(tex, x, y + r, 0F, (float) r, r, h - r * 2, t, t);
        g.blit(tex, x + w - r, y + r, (float) (t - r), (float) r, r, h - r * 2, t, t);
        g.blit(tex, x + r, y + r, (float) r, (float) r, w - r * 2, h - r * 2, t, t);
    }

    private void renderPortrait(GuiGraphics g) {
        int t = tierIndex();
        try {
            ResourceLocation rl = new ResourceLocation(TinkersNewlife.MOD_ID,
                    "textures/gui/momo/" + PORTRAIT[t] + ".png");
            int texW = PORTRAIT_SIZE[t][0], texH = PORTRAIT_SIZE[t][1];
            int targetH = (int) (this.height * 0.78F);
            int targetW = Math.max(1, Math.round(texW * (targetH / (float) texH)));
            int x = this.width - targetW - 16;
            int y = this.height - targetH;
            g.blit(rl, x, y, 0F, 0F, targetW, targetH, texW, texH);
            // 气泡尖角：指向立绘方向的纯色小三角（不用贴图，随气泡颜色）
            int tailY = page == 0 ? listY - 6 : listY + 34;
            for (int i = 0; i < 8; i++) {
                g.fill(panelX + panelW + i, tailY - (8 - i) / 2, panelX + panelW + i + 1, tailY + (8 - i) / 2 + 1, 0xFFF7F3E7);
            }
        } catch (Throwable ignored) {
            // 立绘缺失不该让整个界面崩 ⇒ 静默降级为纯气泡
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0x99000000);      // 压暗背景
        renderPortrait(graphics);

        if (page == 0) renderList(graphics, mouseX, mouseY);
        else renderAnswer(graphics);

        boolean backHover = hovering(backX, backY, 90, 20, mouseX, mouseY);
        nine(graphics, OPTION, backX, backY, 90, 20);
        if (backHover) graphics.fill(backX + 2, backY + 2, backX + 88, backY + 18, 0x33FFFFFF);
        String back = page == 0 ? "关闭" : "回退";
        graphics.drawString(this.font, back, backX + 45 - this.font.width(back) / 2, backY + 6, 0x202020, false);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        List<FormattedCharSequence> head = this.font.split(Component.literal(greeting()), panelW - PAD * 2);
        int headH = head.size() * 10 + PAD * 2;
        int headY = listY - headH - 12;
        nine(g, BUBBLE, panelX, headY, panelW, headH);
        int ly = headY + PAD;
        for (FormattedCharSequence line : head) {
            g.drawString(this.font, line, panelX + PAD, ly, 0x202020, false);
            ly += 10;
        }

        int visible = Math.max(1, listH / ROW_H);
        maxScroll = Math.max(0, entries.size() - visible);
        if (scroll > maxScroll) scroll = maxScroll;
        for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
            Entry e = entries.get(i + scroll);
            int ry = listY + 6 + i * ROW_H;
            boolean hov = hovering(panelX, ry, panelW, ROW_H - 4, mouseX, mouseY);
            nine(g, OPTION, panelX, ry, panelW, ROW_H - 4);
            if (hov) g.fill(panelX + 2, ry + 2, panelX + panelW - 2, ry + ROW_H - 6, 0x33FFFFFF);
            g.drawString(this.font, "· " + e.q(), panelX + PAD, ry + 6, 0x202020, false);
        }
        if (maxScroll > 0) {
            g.drawString(this.font, "滚轮翻动（" + (scroll + 1) + "/" + (maxScroll + 1) + "）",
                    panelX, listY + listH + 2, 0xCCCCCC, false);
        }
    }

    private void renderAnswer(GuiGraphics g) {
        List<FormattedCharSequence> q = this.font.split(Component.literal(entries.get(page - 1).q()), panelW - PAD * 2);
        int qh = q.size() * 10 + PAD * 2;
        int qy0 = listY - qh - 10;
        nine(g, OPTION, panelX, qy0, panelW, qh);
        int qy = qy0 + PAD;
        for (FormattedCharSequence line : q) {
            g.drawString(this.font, line, panelX + PAD, qy, 0x202020, false);
            qy += 10;
        }

        List<FormattedCharSequence> lines = this.font.split(Component.literal(answerText), panelW - PAD * 2);
        int bh = Math.min(listH, lines.size() * 10 + PAD * 2);
        nine(g, BUBBLE, panelX, listY, panelW, bh);
        int budget = Math.max(0, (int) reveal);
        int ly = listY + PAD;
        for (FormattedCharSequence line : lines) {
            if (budget <= 0) break;
            String s = flatten(line);
            int n = Math.min(budget, s.length());
            g.drawString(this.font, s.substring(0, n), panelX + PAD, ly, 0x202020, false);
            budget -= s.length();
            ly += 10;
            if (ly > listY + bh - PAD) break;
        }
        g.drawString(this.font, typing() ? "按空格跳过" : "按空格继续",
                panelX, listY + bh + 4, 0xCCCCCC, false);
    }

    private static String flatten(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32) {
            if (page > 0) {
                if (typing()) reveal = answerText.length();
                else page = 0;
            }
            return true;
        }
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (page == 0 && maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hovering(backX, backY, 90, 20, mouseX, mouseY)) {
                if (page > 0) page = 0;
                else this.onClose();
                return true;
            }
            if (page == 0) {
                int visible = Math.max(1, listH / ROW_H);
                for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
                    int ry = listY + 6 + i * ROW_H;
                    if (hovering(panelX, ry, panelW, ROW_H - 4, mouseX, mouseY)) {
                        page = i + scroll + 1;
                        answerText = entries.get(page - 1).a();
                        reveal = 0F;
                        return true;
                    }
                }
            } else if (typing()) {
                reveal = answerText.length();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
