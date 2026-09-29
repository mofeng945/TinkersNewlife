package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;

/**
 * 词条·<b>兵士佩刀</b>（§808 · 唐横刀自带 · <b>无等级</b> ✓）——
 * 注册成 {@code StaticModifier}（不升级 ⇒ 提示里不显示等级 ✓ 正合"无等级"口径 ✓）。
 *
 * <p>实际逻辑全在 {@code content/modifier/events/SoldiersSaberHandler} ✓：
 * <ul>
 *   <li>手持时<b>实体交互距离 +1 格</b>（在 {@code TangHengDaoItem#getAttributeModifiers} 里给属性 ✓）；</li>
 *   <li><b>4 格内越近"附加的段数"越多</b>：4格 0 段 / 3格 1 段 / 2格 2 段 / 1格 3 段 / 贴身 4 段 ✓
 *       每段 = <b>工具面板 × 50%</b> ✓ 且<b>各自独立结算一次伤害</b> ✓（不是把总加成塞进同一次 ✗）；</li>
 *   <li>每段挥出一道<b>灰色刀光</b> ✓（{@code SoldierSlashEntity} 弧形面片 ＋ 弧光贴图 ⇒ 仿拔刀剑 ✓
 *       <b>不是</b> {@code ParticleTypes.SWEEP_ATTACK} 横扫粒子 ✗ —— 见 §810）。</li>
 * </ul>
 * 与 {@link DarkMetalBreakerModifier} 同款写法：本类只当"标记/展示"，逻辑在 handler ✓。
 */
public class SoldiersSaberModifier extends BaseCombatModifier {

    /**
     * <b>无等级</b> ✓（用户口径 §818）——不论匠魂给的 level 是几，名字后面都<b>不拼罗马数字</b> ✓。
     * <p>为什么要显式写：材料特性是<b>按部件累加</b>的（{@code ModifierEntry#merge} = level + level ✓）
     * ⇒ 一件"镶板 + 锁链基底"都是该材料的盔甲会让 level 变成 2 ✗，于是退回基类的
     * {@code ModifierLevelDisplay.DEFAULT} 拼出「某特性 II」✗（匠魂官方方案 {@code NO_LEVELS} 可解 ✓）。
     */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay.NO_LEVELS
                .nameForLevel(this, level);
    }
}
