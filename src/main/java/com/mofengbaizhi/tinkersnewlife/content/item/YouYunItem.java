package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 咒具「游云」（《咒术回战》）：原版武器逻辑（非匠魂工具，继承 {@link CursedToolItem}）。
 * <ul>
 *   <li>基础伤害 10 点，攻击速度 2（剑类基础 4.0 + modifier -2.0）</li>
 *   <li>每一击破甲（=突破盾牌格挡，像原版斧头使盾失效）+ 固定增伤 120% + 对亡灵额外 +10%
 *       （攻击管线见 {@code content/handler/YouYunAttackHandler}，用 AttackEntityEvent
 *       取消原版、改结算物理伤害 + 命中破盾）</li>
 *   <li>会将目标额外击退（本次伤害/100 格）</li>
 *   <li>打的是物理伤害；<b>不能</b>像天逆鉾那样穿透无下限·无限的防御（{@link #ignoresInfinity()} 保持 false）</li>
 * </ul>
 */
public class YouYunItem extends CursedToolItem {

    public static final Tier TIER = new Tier() {
        @Override public int getUses() { return 0; } // 无限耐久（isDamageable=false 实际不消耗）
        @Override public float getSpeed() { return 6.0F; }
        @Override public float getAttackDamageBonus() { return 0.0F; }
        @Override public int getLevel() { return 4; }
        @Override public int getEnchantmentValue() { return 18; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    public YouYunItem(Properties properties) {
        // 总攻击伤害 10（tier bonus=0，直接传总伤）；攻速 modifier -2.0 → 攻击速度 = 4.0 - 2.0 = 2.0
        super(TIER, 10, -2.0F, properties);
    }

    /** 游云不突破无下限防御（天逆鉾才 true） */
    @Override
    public boolean ignoresInfinity() {
        return false;
    }
}
