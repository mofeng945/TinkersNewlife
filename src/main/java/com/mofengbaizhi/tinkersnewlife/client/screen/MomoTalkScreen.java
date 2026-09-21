package com.mofengbaizhi.tinkersnewlife.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 墨默的**对话树**（用户口径 §455 D/E ✓ 文案逐字照抄自备忘录 §455 D ✓）。
 *
 * <ul>
 *   <li>开场白按好感度 6 档 ✓（第 5 条含《玩家名》✓ 由客户端自己填 ✓）；</li>
 *   <li>选项 6 档 × 3 = 18 条 ✓ **未达好感的不显示** ✓（负好感根本进不来 ✓ 菜单那道已经挡了 ✓）；</li>
 *   <li>选中后回答**一个字一个字出现**（打字机 ✓）；**按空格跳过**动画 ✓；**打完后按空格回到对话界面** ✓；</li>
 *   <li>选项**超出屏幕可用滚轮滑动** ✓；**每个界面都有回退按钮** ✓（答案页 ⇒ 回选项页 ✓ 选项页 ⇒ 关闭 ✓）。</li>
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

    private static final int ROW_H = 18;
    private static final int PANEL_W = 270;
    private static final int PANEL_H = 180;

    private record Entry(String q, String a) {}

    private final int favor;
    private final String playerName;
    private final List<Entry> entries = new ArrayList<>();

    /** 0 = 选项页 ✓；>0 = 正在看第 (page-1) 条回答 ✓ */
    private int page = 0;
    /** 打字机：已显示字符数（每 tick +1.5 ✓） */
    private float reveal = 0F;
    private String answerText = "";
    private int scroll = 0;
    private int maxScroll = 0;
    private int listX, listY, listW, listH, backX, backY;

    public MomoTalkScreen(int favor, String playerName) {
        super(Component.translatable("menu.tinkersnewlife.momo"));
        this.favor = favor;
        this.playerName = playerName == null ? "" : playerName;
        for (int t = 0; t < TIER.length; t++) {
            if (favor < TIER[t]) continue;                        // 未达好感 ⇒ 整档不显示 ✓
            for (int i = 0; i < QUESTIONS[t].length; i++) {
                entries.add(new Entry(QUESTIONS[t][i], ANSWERS[t][i]));
            }
        }
    }

    private String greeting() {
        int best = 0;
        for (int t = 0; t < TIER.length; t++) if (favor >= TIER[t]) best = t;
        return String.format(GREET[best], playerName);
    }

    private int left() { return (this.width - PANEL_W) / 2; }
    private int top() { return (this.height - PANEL_H) / 2; }
    private boolean typing() { return page > 0 && reveal < answerText.length(); }

    @Override
    protected void init() {
        super.init();
        listX = left() + 8;
        listY = top() + 34;
        listW = PANEL_W - 16;
        listH = PANEL_H - 44 - 22;
        backX = left() + PANEL_W - 62;
        backY = top() + PANEL_H - 24;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (page > 0 && reveal < answerText.length()) {
            reveal = Math.min(answerText.length(), reveal + 1.5F);   // 一个字一个字 ✓
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int x = left();
        int y = top();
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFFC6C6C6);
        graphics.fill(x, y, x + PANEL_W, y + 17, 0xFF404040);
        graphics.drawString(this.font, "墨默", x + 8, y + 5, 0xFFFFFF, false);

        if (page == 0) renderList(graphics, mouseX, mouseY);
        else renderAnswer(graphics);

        boolean backHover = hovering(backX, backY, 54, 16, mouseX, mouseY);
        graphics.fill(backX, backY, backX + 54, backY + 16, backHover ? 0xFF8FA8D8 : 0xFF6E6E6E);
        String back = page == 0 ? "关闭" : "回退";
        graphics.drawString(this.font, back, backX + 27 - this.font.width(back) / 2, backY + 4, 0x202020, false);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, greeting(), listX, top() + 21, 0xFF303030, false);
        int visible = Math.max(1, listH / ROW_H);
        maxScroll = Math.max(0, entries.size() - visible);
        if (scroll > maxScroll) scroll = maxScroll;
        for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
            Entry e = entries.get(i + scroll);
            int ry = listY + i * ROW_H;
            boolean hover = hovering(listX, ry, listW, ROW_H - 2, mouseX, mouseY);
            graphics.fill(listX, ry, listX + listW, ry + ROW_H - 2, hover ? 0xFF8FA8D8 : 0xFF6E6E6E);
            graphics.drawString(this.font, "· " + e.q(), listX + 6, ry + 4, 0xFFFFFF, false);
        }
        if (maxScroll > 0) {
            graphics.drawString(this.font, "滚轮翻动（" + (scroll + 1) + "/" + (maxScroll + 1) + "）",
                    listX, top() + PANEL_H - 38, 0x606060, false);
        }
    }

    private void renderAnswer(GuiGraphics graphics) {
        graphics.drawString(this.font, entries.get(page - 1).q(), listX, top() + 21, 0xFF303030, false);
        List<FormattedCharSequence> lines = this.font.split(Component.literal(answerText), listW);
        int budget = Math.max(0, (int) reveal);
        int ly = listY;
        for (FormattedCharSequence line : lines) {
            if (budget <= 0) break;
            String s = flatten(line);
            int n = Math.min(budget, s.length());
            graphics.drawString(this.font, s.substring(0, n), listX, ly, 0xFFFFFF, false);
            budget -= s.length();
            ly += 10;
            if (ly > top() + PANEL_H - 46) break;
        }
        graphics.drawString(this.font, typing() ? "按空格跳过" : "按空格继续", listX, top() + PANEL_H - 38, 0x606060, false);
    }

    /** Screen 在 1.20.1 没有 isHovering（那是 AbstractContainerScreen 的）⇒ 自己判 */
    private boolean hovering(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
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
        if (keyCode == 32) {                                       // 空格 ✓
            if (page > 0) {
                if (typing()) {
                    reveal = answerText.length();                  // 跳过打字动画 ✓
                } else {
                    page = 0;                                      // 显示完 ⇒ 回到对话界面 ✓
                }
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
            if (hovering(backX, backY, 54, 16, mouseX, mouseY)) {
                if (page > 0) {
                    page = 0;                                      // 回退 ⇒ 回选项页 ✓
                } else {
                    this.onClose();
                }
                return true;
            }
            if (page == 0) {
                int visible = Math.max(1, listH / ROW_H);
                for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
                    int ry = listY + i * ROW_H;
                    if (hovering(listX, ry, listW, ROW_H - 2, mouseX, mouseY)) {
                        page = i + scroll + 1;
                        answerText = entries.get(page - 1).a();
                        reveal = 0F;                               // 从头开始打字 ✓
                        return true;
                    }
                }
            } else if (typing()) {
                reveal = answerText.length();                      // 点一下也能跳过 ✓
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
