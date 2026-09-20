package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceHandler;
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
 * 耐久条 = 善恶进度（0 灰 / 中间白 / 满恶黑 的染色在第三期做 ✓ 本轮先给条 ✓）；
 * 物品 NBT 里的 {@code tn_alignment} 只是<b>镜像</b>（权威值在玩家持久数据 ✓），
 * 好让条/染色/物品栏上方的颜色条能<b>只读物品</b>就画出来 ✓。
 */
public class ConscienceItem extends Item implements ICurioItem {

    /** 进度条上限（0~100；{@link ConscienceHandler#BAR_ZERO}=50 表示 0% ✓） */
    public static final int BAR_MAX = 100;

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
        return v >= ConscienceHandler.BAR_ZERO ? 0x55FF55 : 0xFF5555;   // 善绿 / 恶红 ✓
    }

    // ============================================================
    //  提示：暂时只有名字 + 紫色 flavor ✓（善恶百分比与红蓝效果清单在第二/三期 ✓）
    // ============================================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.tinkersnewlife.conscience.flavor")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
