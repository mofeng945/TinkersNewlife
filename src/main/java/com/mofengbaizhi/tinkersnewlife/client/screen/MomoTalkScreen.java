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

    /** 逐条对话的表情：[档位][该档第几条问题] ⇒ {@link MomoArt} 的表情下标 ✓ */
    private static final int[][] EXPR = {
            { 0, 0, 3 },   // 自我介绍 N / 地点 N / 变故 A
            { 1, 2, 3 },   // 圣女 H / 喜好 B / 世界 A
            { 3, 2, 1 },   // 咒术 A / 高维度 B（笨蛋！）/ 镰刀 H（女朋友送的）
            { 0, 1, 4 },   // 收集格赫罗斯 N / 教会 H / 关于我 S
            { 0, 0, 5 },   // 格赫罗斯散落 N / 没看到怪物 N / 能免费吗 D（你在说什么小猪话）
            { 0, 3, 0 }    // 计划 N / 真相 A / 未来 N
    };

    /** 九宫格气泡（自绘纯色版 ✓ 手绘同路径同名即可替换 ✓）—— 真身在 {@link MomoArt} ✓ 两屏共用 */
    private static final ResourceLocation BUBBLE = MomoArt.BUBBLE;
    private static final ResourceLocation OPTION = MomoArt.OPTION;
    private static final int PAD = MomoArt.PAD;      // 气泡内边距
    private static final int ROW_H = 26;

    /**
     * §493 用户口径「字可以大一点，粗一点，让格式不要这么僵硬」：
     * <ul>
     *   <li><b>放大</b>：文字统一走 {@link #TEXT_SCALE} 倍缩放（`pose().scale`）⇒ 比原版 8px 字大一圈 ✓；</li>
     *   <li><b>加粗</b>：正文一律 `ChatFormatting.BOLD` ✓（**换行宽度也按加粗算**，否则行尾会溢出色框 ✗）；</li>
     *   <li><b>不那么僵硬</b>：气泡**按内容自适应宽度**（不再人人一板 2/3 宽 ✓ 上限仍是 2/3 ✓）、
     *       去掉选项前面的「·」、行距加宽、文字带阴影 ⇒ 像聊天窗而不是表格 ✓。</li>
     * </ul>
     */
    private static final float TEXT_SCALE = 1.25F;
    /** 每行占高（= 原版 10 × 缩放 ✓） */
    private static final int LINE_H = Math.round(10 * TEXT_SCALE);
    /** 她的字偏暖、你的字偏冷（再补一层层次 ✓） */
    private static final int MOMO_TEXT = 0x2B2118;
    private static final int PLAYER_TEXT = 0x1C2430;
    private static final int HINT_TEXT = 0xCCCCCC;

    /** §492 层次：她的气泡加一层**暖色**、你的提问加一层**冷色** ✓（同一套九宫格皮，但一眼分得清谁在说 ✓） */
    private static final int MOMO_TINT = 0x1CFFD9A0;
    private static final int PLAYER_TINT = 0x2E8FB8E8;

    private record Entry(String q, String a, int tier, int expr) {}

    private final int favor;
    private final String playerName;
    private final List<Entry> entries = new ArrayList<>();

    private int page = 0;                    // 0 = 选项页；>0 = 看第 (page-1) 条回答
    private float reveal = 0F;
    private String answerText = "";
    private int scroll = 0;
    private int maxScroll = 0;

    private int panelX, panelW;              // 左侧文字区（右侧留给立绘）
    private int bubbleW, momoX, playerX;     // 气泡宽（空白区 2/3）+ 她的 X（靠右）/ 你的 X（靠左）✓
    private int portraitW, portraitH;        // init() 里算好的立绘尺寸 ✓
    private int listY, listH;                // listY = 开场白气泡顶（回答页 = 回答气泡顶 ✓）
    private int rowsTop;                     // 选项第一行的 Y ✓
    private int greetH;                      // 开场白气泡高度 ✓
    private List<FormattedCharSequence> greetLines;   // 开场白换行结果（init 里算好 ✓）
    private int backX, backY;
    private int tailY;                       // 尖角竖直位置（跟着当前气泡算 ✓）

    public MomoTalkScreen(int favor, String playerName) {
        super(Component.translatable("menu.tinkersnewlife.momo"));
        this.favor = favor;
        this.playerName = playerName == null ? "" : playerName;
        for (int t = 0; t < TIER.length; t++) {
            if (favor < TIER[t]) continue;
            for (int i = 0; i < QUESTIONS[t].length; i++) {
                entries.add(new Entry(QUESTIONS[t][i], ANSWERS[t][i], t, EXPR[t][i]));
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

    /** 换行用的最大字宽（**反算回未缩放的字体单位** ⇒ 缩放后正好塞进气泡内边距里 ✓） */
    private int wrapW() {
        return Math.max(40, (int) ((bubbleW - PAD * 2) / TEXT_SCALE));
    }

    /** 按加粗算好的换行（加粗比常规宽约 1px/字 ⇒ **必须按加粗换行**，否则行尾溢出色框 ✗） */
    private List<FormattedCharSequence> wrap(String text) {
        return this.font.split(Component.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD), wrapW());
    }

    /** 整段文字在屏上的实际宽度（已含缩放 ✓ 用于气泡自适应宽度 ✓） */
    private int textW(List<FormattedCharSequence> lines) {
        int max = 0;
        for (FormattedCharSequence line : lines) {
            max = Math.max(max, this.font.width(line));
        }
        return Math.round(max * TEXT_SCALE);
    }

    /** 气泡宽度 = 内容宽 + 内边距，**上限仍是空白区 2/3** ✓（这样长短句气泡宽窄不一，不呆板 ✓） */
    private int bubbleWidthFor(List<FormattedCharSequence> lines) {
        return Math.min(bubbleW, textW(lines) + PAD * 2);
    }

    /** 画一行（带缩放 + 阴影 ✓）；`bold` 用于打字机那种"丢掉了样式"的纯文本 ✓ */
    private void text(GuiGraphics g, FormattedCharSequence seq, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1F);
        g.drawString(this.font, seq, Math.round(x / TEXT_SCALE), Math.round(y / TEXT_SCALE), color, true);
        g.pose().popPose();
    }

    /** 同上，但吃纯文本（打字机用 ✓ `bold` = 补回加粗样式 ✓） */
    private void text(GuiGraphics g, String s, int x, int y, int color, boolean bold) {
        g.pose().pushPose();
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1F);
        g.drawString(this.font, bold ? "§l" + s : s, Math.round(x / TEXT_SCALE), Math.round(y / TEXT_SCALE), color, true);
        g.pose().popPose();
    }

    /** 选项那一条气泡的宽度（点击判定和绘制共用 ✓ 内容自适应 ✓） */
    private int optionW(Entry e) {
        return Math.min(bubbleW, Math.round(this.font.width("§l" + e.q()) * TEXT_SCALE) + PAD * 2);
    }

    @Override
    protected void init() {
        super.init();
        // 立绘尺寸（MomoArt 统一算 ✓）
        int[] ps = MomoArt.portraitSize(this.width, this.height);
        portraitW = ps[0];
        portraitH = ps[1];
        panelX = 24;
        panelW = Math.max(200, this.width - portraitW - 48);
        // §492 对话层次（用户口径）：**气泡宽度 ≤ 空白区 2/3**；**她的话靠右**（贴着立绘 ✓）、**你的提问靠左** ✓
        bubbleW = Math.max(150, panelW * 2 / 3);
        momoX = panelX + panelW - bubbleW;      // 她的气泡右缘 = 空白区右缘 ⇒ 尖角正好从这儿指出去 ✓
        playerX = panelX;                       // 你的气泡左缘 ✓
        // §490：开场白气泡**正对她脸居中** ⇒ 尖角百分百指到她脸上 ✓；选项从气泡下方开始排 ✓
        this.greetLines = wrap(greeting());
        this.greetH = this.greetLines.size() * LINE_H + PAD * 2;
        listY = Math.max(10, MomoArt.faceY(this.width, this.height) - this.greetH / 2);
        rowsTop = listY + this.greetH + 12;
        listH = Math.max(72, this.height - rowsTop - 44);
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

    /** 九宫格（真身在 {@link MomoArt} ✓） */
    private void nine(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        MomoArt.nine(g, tex, x, y, w, h);
    }

    /** @param expr {@link MomoArt} 表情下标（逐条对话各不相同 ✓） */
    private void renderPortrait(GuiGraphics g, int expr) {
        MomoArt.portrait(g, expr, this.width, this.height);
        // 气泡尖角：指向立绘方向（颜色 = 气泡米色 ✓）
        MomoArt.tail(g, panelX + panelW, tailY, 0xFFF7F3E7);
    }

    /**
     * 当前该露哪张脸：看回答 ⇒ 那条回答自己的表情 ✓；
     * 选项页 ⇒ **只按好感度**（{@link MomoArt#exprForFavor}）✓
     * —— ⚠️ **悬停选项不改脸**（用户口径：「鼠标放置在对话选项上时表情不应该发生变化」；
     * 原来那套"悬停预览表情"已删 ✓ 选项高亮框保留 ✓）。
     */
    private int currentExpr() {
        if (page > 0) return entries.get(page - 1).expr();
        return MomoArt.exprForFavor(favor);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0x99000000);      // 压暗背景

        // 尖角对准**她的脸**（§490 用户口径：原来对不上她的头 ✗）：
        // 取脸高，再夹进"当前这个气泡的竖直范围"里 —— 脸在气泡范围内就正好指脸，否则摆在最近的那条边 ✓
        int faceY = MomoArt.faceY(this.width, this.height);
        if (page == 0) {
            int top = listY;
            tailY = Math.max(top + 8, Math.min(top + greetH - 8, faceY));
        } else {
            int bh = Math.min(listH, wrap(answerText).size() * LINE_H + PAD * 2);
            tailY = Math.max(listY + 8, Math.min(listY + bh - 8, faceY));
        }
        renderPortrait(graphics, currentExpr());

        if (page == 0) renderList(graphics, mouseX, mouseY);
        else renderAnswer(graphics);
        boolean backHover = hovering(backX, backY, 90, 20, mouseX, mouseY);
        nine(graphics, OPTION, backX, backY, 90, 20);
        if (backHover) graphics.fill(backX + 2, backY + 2, backX + 88, backY + 18, 0x33FFFFFF);
        String back = page == 0 ? "关闭" : "回退";
        int backW = Math.round(this.font.width(back) * TEXT_SCALE);
        text(graphics, back, backX + 45 - backW / 2, backY + (20 - LINE_H) / 2, 0x202020, false);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        // 她的话：右对齐、按内容自适应宽 ✓
        List<FormattedCharSequence> head = this.greetLines;
        int headH = head.size() * LINE_H + PAD * 2;
        int headW = bubbleWidthFor(head);
        int headX = panelX + panelW - headW;
        int headY = listY;                       // §490：开场白就在 listY（她脸那一带 ✓）
        nine(g, BUBBLE, headX, headY, headW, headH);
        g.fill(headX + 2, headY + 2, headX + headW - 2, headY + headH - 2, MOMO_TINT);
        int ly = headY + PAD;
        for (FormattedCharSequence line : head) {
            text(g, line, headX + PAD, ly, MOMO_TEXT);
            ly += LINE_H;
        }

        int visible = Math.max(1, listH / ROW_H);
        maxScroll = Math.max(0, entries.size() - visible);
        if (scroll > maxScroll) scroll = maxScroll;
        for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
            Entry e = entries.get(i + scroll);
            int ry = rowsTop + i * ROW_H;
            int rw = optionW(e);                             // 你的提问：靠左、跟内容一样宽 ✓
            boolean hov = hovering(playerX, ry, rw, ROW_H - 6, mouseX, mouseY);
            nine(g, OPTION, playerX, ry, rw, ROW_H - 6);
            g.fill(playerX + 2, ry + 2, playerX + rw - 2, ry + ROW_H - 8, PLAYER_TINT);
            if (hov) g.fill(playerX + 2, ry + 2, playerX + rw - 2, ry + ROW_H - 8, 0x33FFFFFF);
            text(g, "§l" + e.q(), playerX + PAD, ry + (ROW_H - 6 - LINE_H) / 2 + 1, PLAYER_TEXT, false);
        }
        if (maxScroll > 0) {
            text(g, "滚轮翻动（" + (scroll + 1) + "/" + (maxScroll + 1) + "）",
                    playerX, Math.min(this.height - 48, rowsTop + visible * ROW_H + 2), HINT_TEXT, false);
        }
    }

    private void renderAnswer(GuiGraphics g) {
        // 你的提问（左 ✓ 冷色 ✓ 自适应宽 ✓）
        List<FormattedCharSequence> q = wrap(entries.get(page - 1).q());
        int qh = q.size() * LINE_H + PAD * 2;
        int qw = bubbleWidthFor(q);
        int qy0 = Math.max(6, listY - qh - 12);
        nine(g, OPTION, playerX, qy0, qw, qh);
        g.fill(playerX + 2, qy0 + 2, playerX + qw - 2, qy0 + qh - 2, PLAYER_TINT);
        int qy = qy0 + PAD;
        for (FormattedCharSequence line : q) {
            text(g, line, playerX + PAD, qy, PLAYER_TEXT);
            qy += LINE_H;
        }

        // 她的回答（右 ✓ 暖色 ✓ 自适应宽 ✓ 打字机 ✓）
        List<FormattedCharSequence> lines = wrap(answerText);
        int bh = Math.min(listH, lines.size() * LINE_H + PAD * 2);
        int bw = bubbleWidthFor(lines);
        int bx = panelX + panelW - bw;
        nine(g, BUBBLE, bx, listY, bw, bh);
        g.fill(bx + 2, listY + 2, bx + bw - 2, listY + bh - 2, MOMO_TINT);
        int budget = Math.max(0, (int) reveal);
        int ly = listY + PAD;
        for (FormattedCharSequence line : lines) {
            if (budget <= 0) break;
            String s = flatten(line);
            int n = Math.min(budget, s.length());
            text(g, s.substring(0, n), bx + PAD, ly, MOMO_TEXT, true);
            budget -= s.length();
            ly += LINE_H;
            if (ly > listY + bh - PAD) break;
        }
        text(g, typing() ? "按空格跳过" : "按空格继续", bx + PAD, listY + bh + 4, HINT_TEXT, false);
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
                    Entry e = entries.get(i + scroll);
                    int ry = rowsTop + i * ROW_H;
                    if (hovering(playerX, ry, optionW(e), ROW_H - 6, mouseX, mouseY)) {
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
