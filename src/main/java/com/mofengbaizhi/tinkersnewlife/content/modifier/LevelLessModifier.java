package com.mofengbaizhi.tinkersnewlife.content.modifier;

import net.minecraft.network.chat.Component;
import slimeknights.tconstruct.library.modifiers.impl.SingleLevelModifier;

/**
 * <b>无等级强化</b>的公共基类：<b>名字里永远不带等级数字</b> ✓（§732 新增）
 *
 * <h2>为什么不能只用匠魂的 {@link SingleLevelModifier}</h2>
 * 3.11 里 {@code SingleLevelModifier} <b>只覆写了</b> {@code getDisplayName(int level)} 一个方法 ✓：
 * <pre>
 *   if (level == 1) return getDisplayName();      // 不带数字 ✓
 *   else            return Modifier.getDisplayName(level);   // 退回默认 ⇒ 带罗马数字 ✗
 * </pre>
 * 而基类 {@code Modifier#getDisplayName(int)} 走的是 {@code ModifierLevelDisplay.DEFAULT}，
 * 它用 {@code RomanNumeralHelper.getNumeral(level)} <b>拼罗马数字</b> ✓（字节码查过 ✓）。
 *
 * <h2>为什么等级会变成 2、3（真实原因 ✗）</h2>
 * 材料特性是<b>按部件累加</b>的 ✓：{@code MaterialTraitsModule#addTraits} 每个部件各加一次，
 * {@code ModifierNBT.Builder#add} 再用 {@code ModifierEntry#merge} 合并，而 merge 的实现是
 * <b>{@code level + other.level}</b>（{@code iadd} ✓）。
 * ⇒ 一件盔甲「镶板 + 锁链基底」都用虚空金属 ⇒ 该特性直接 <b>2 级</b> ✗；
 * 一把工具「头 + 手柄 + 绑定结」都用虚空金属 ⇒ {@code default} 特性 <b>3 级</b> ✗。
 * 匠魂本体也一样（它那些是"越高越强"的强化 ✓，带数字是对的 ✓），
 * <b>但我们这些强化是"有/无"型</b>（行为判定全是 {@code level > 0} ✓），带数字只会误导玩家 ✗。
 *
 * <h2>所以</h2>
 * 本基类把 {@code getDisplayName(int)} <b>无条件</b>退回不带数字的名字 ✓；
 * 行为侧一个字不改 ✓（这些强化的判定本来就只看"有没有" ✓）。
 */
public abstract class LevelLessModifier extends SingleLevelModifier {

    /** 不论匠魂给的 level 是几，显示名都不带等级数字 ✓ */
    @Override
    public Component getDisplayName(int level) {
        return getDisplayName();
    }
}
