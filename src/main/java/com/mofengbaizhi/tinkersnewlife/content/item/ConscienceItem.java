package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceHandler;
import com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceThresholdHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 饰品·<b>心</b>（{@code tinkersnewlife:conscience}）—— 只吃「<b>心</b>」这个自定义饰品槽 ✓。
 *
 * <h2>不可卸下（模仿神秘遗物「七咒之戒」✓ 并拓展 ✓）</h2>
 * <ul>
 *   <li><b>① {@link #canUnequip} 恒 {@code false}</b> ✓（与 {@code com.aizistral.enigmaticlegacy.items.CursedRing}
 *       同款思路 ✓ ⇒ 玩家自己点不掉、拖不出 ✓）；</li>
 *   <li><b>② 服务端每 20 tick 兜底补回</b> ✓（{@link ConscienceHandler} ✓）——
 *       第三方 mod <b>绕过 curios API 直接写槽</b>（典型：诡厄巫法启示录的亚波伦把饰品收进
 *       「饰品储存水晶」✓ 见备忘录 §424 的反汇编结论 ✓）时也能补回来 ✓；
 *       善恶值存在<b>玩家持久数据</b>里 ✓ ⇒ 补一个全新的「心」<b>不丢任何进度</b> ✓。</li>
 * </ul>
 *
 * <h2>显示</h2>
 * 耐久条 = 善恶进度（长度按善恶值 ✓ 颜色按用户口径：<b>&lt; −10% 红 / &gt; +10% 蓝 / 其余绿</b> ✓）；
 * 物品 NBT 里的 {@code tn_alignment} 只是<b>镜像</b>（权威值在玩家持久数据 ✓），
 * 好让条/染色/物品栏上方的颜色条能<b>只读物品</b>就画出来 ✓。
 */
public class ConscienceItem extends Item implements ICurioItem {

    /** 进度条上限（0~100；{@link ConscienceHandler#BAR_ZERO}=50 表示 0% ✓） */
    public static final int BAR_MAX = 100;

    /**
     * 条色阈值（镜像 0~100 ⇒ 50 = 0% ✓ 用户口径）：
     * <b>低于 −10% 红 ✓ / 高于 +10% 蓝 ✓ / 其余（±10% 内）绿 ✓</b>。
     */
    public static final int BAR_RED_BELOW = ConscienceHandler.BAR_ZERO - 10;   // 镜像 < 40 = 善恶 < −10
    public static final int BAR_BLUE_ABOVE = ConscienceHandler.BAR_ZERO + 10;  // 镜像 > 60 = 善恶 > +10

    public ConscienceItem(Properties properties) {
        super(properties);
    }

    // ============================================================
    //  Curios：不可卸下 ✓（照七咒之戒 ✓）
    // ============================================================

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return false;
    }

    // ============================================================
    //  耐久条 = 善恶进度 ✓
    // ============================================================

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int v = ConscienceHandler.mirrorOf(stack, ConscienceHandler.BAR_ZERO);
        v = Math.max(0, Math.min(BAR_MAX, v));
        return Math.round(13.0F * v / BAR_MAX);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int v = ConscienceHandler.mirrorOf(stack, ConscienceHandler.BAR_ZERO);
        if (v < BAR_RED_BELOW) return 0xFF5555;     // 恶：< −10% → 红 ✓
        if (v > BAR_BLUE_ABOVE) return 0x5555FF;    // 善：> +10% → 蓝 ✓
        return 0x55FF55;                            // 中性：±10% 以内 → 绿 ✓
    }

    // ============================================================
    //  动态 tooltip（三期③ ✓ 用户口径）
    // ============================================================
    //
    //  平时（精简）：
    //      言行举止，无悔于心
    //      善恶 +32%
    //      ✔ 持续生命恢复 II
    //      ✔ 幸运值 +50%
    //      ✘ 时运等级 +1（还差 8%）
    //      按住 Shift 查看全部
    //  ⇒ **只列当前这一侧**（善恶为正 ⇒ 善侧 ✓ 为负 ⇒ 恶侧 ✓ 恰好 0 ⇒ 只留前两行 ✓）
    //     已达成逐条 ✔ ✓ 再加"下一个未达成档"一行（带还差多少 ✓）
    //
    //  按住 Shift：把两侧全部 10 档列出来 ✓ 已达成用对应色 ✓ 未达成深灰 ✓
    //
    //  ⚠ 用户口径：**不写档位百分比** ✗ **不写"善行/恶行"标题** ✗ **同一档只一行** ✓
    //  （恶 −45 的"不可名状 + 村民涨价"在文案里就用「 · 」连成一行 ✓）

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.tinkersnewlife.conscience.flavor")
                .withStyle(ChatFormatting.DARK_PURPLE));

        int alignment = ConscienceHandler.mirrorOf(stack, ConscienceHandler.BAR_ZERO) - ConscienceHandler.BAR_ZERO;
        tooltip.add(Component.translatable("item.tinkersnewlife.conscience.alignment",
                (alignment > 0 ? "+" : "") + alignment).withStyle(toneOf(alignment)));

        if (shiftDown()) {
            for (var tier : ConscienceThresholdHandler.TIERS_GOOD) {
                addTierLine(tooltip, tier, alignment);
            }
            for (var tier : ConscienceThresholdHandler.TIERS_EVIL) {
                addTierLine(tooltip, tier, alignment);
            }
            return;
        }

        if (alignment != 0) {
            for (var tier : alignment > 0 ? ConscienceThresholdHandler.TIERS_GOOD
                    : ConscienceThresholdHandler.TIERS_EVIL) {
                if (ConscienceThresholdHandler.reached(alignment, tier)) {
                    tooltip.add(Component.translatable(tier.key()).withStyle(toneOf(alignment)));
                }
            }
            var next = ConscienceThresholdHandler.nextTier(alignment);
            if (next == null) {
                tooltip.add(Component.translatable("item.tinkersnewlife.conscience.max")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                tooltip.add(Component.translatable("item.tinkersnewlife.conscience.gap",
                                Component.translatable(next.key()),
                                ConscienceThresholdHandler.gapTo(alignment, next))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.translatable("item.tinkersnewlife.conscience.expand")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 全表里的一行：达成 ⇒ ✔ + 对应色 ✓ 未达成 ⇒ ✘ + 深灰 ✓ */
    private static void addTierLine(List<Component> tooltip, ConscienceThresholdHandler.Tier tier, int alignment) {
        boolean on = ConscienceThresholdHandler.reached(alignment, tier);
        tooltip.add(Component.literal(on ? "✔ " : "✘ ")
                .append(Component.translatable(tier.key()))
                .withStyle(on ? toneOf(alignment) : ChatFormatting.DARK_GRAY));
    }

    private static ChatFormatting toneOf(int alignment) {
        return alignment > 0 ? ChatFormatting.BLUE : alignment < 0 ? ChatFormatting.RED : ChatFormatting.GRAY;
    }

    /**
     * Shift 是否按下 ✓。
     *
     * <p>⚠ 这个类在**公共源码集**里 ✗（服务端也会加载 ✓）⇒ 先查 dist ✓ 再看客户端类 ✗：
     * {@code Screen} 在服务端不存在 ✓ 真被碰到也是 {@code NoClassDefFoundError}（Error ✗）
     * ⇒ 用 {@code catch (Throwable)} 兜住 ✓（而且 dist 判断在前 ⇒ 服务端根本不会走到那一步 ✓）。
     */
    private static boolean shiftDown() {
        try {
            return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()
                    && net.minecraft.client.gui.screens.Screen.hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
