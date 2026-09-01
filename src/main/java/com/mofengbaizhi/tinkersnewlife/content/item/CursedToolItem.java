package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * 咒具基类（走原版武器逻辑，不定义匠魂工具）。
 * <p>
 * 所有咒具的公共特性：
 * - 无限耐久（永不损坏）
 * - 对亡灵生物特攻：命中亡灵额外 +6 点伤害
 * <p>
 * 子类通过 {@link #extraAttackBonus(LivingEntity)} 可追加额外特攻；
 * 天逆鉾等专属能力（领域破坏、无下限穿透、清除式神）由子类各自实现。
 */
public abstract class CursedToolItem extends SwordItem {

    /** 亡灵特攻固定加成 */
    protected static final float UNDEAD_BONUS = 6.0F;

    public CursedToolItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        // 总攻击伤害 = tier bonus + modifier；本模组咒具统一用 tier bonus=0，直接传总伤害
        super(tier, attackDamage, attackSpeed, properties);
    }

    /** 无限耐久：永不损坏 */
    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    /** 咒具可附魔（附魔台正常显示剑类附魔） */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    /** 附魔价值：取 tier 配置（附魔台生成选项的必要条件） */
    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return getTier().getEnchantmentValue();
    }

    /** 命中后：亡灵特攻 + 子类额外加成 */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean vanilla = super.hurtEnemy(stack, target, attacker);
        // 亡灵特攻（对亡灵额外 +6）
        if (target.getMobType() == MobType.UNDEAD) {
            target.hurt(target.damageSources().mobAttack(attacker), UNDEAD_BONUS + extraAttackBonus(target));
        } else {
            float extra = extraAttackBonus(target);
            if (extra > 0) {
                target.hurt(target.damageSources().mobAttack(attacker), extra);
            }
        }
        return vanilla;
    }

    /** 子类可覆写的额外特攻加成（默认 0） */
    protected float extraAttackBonus(LivingEntity target) {
        return 0.0F;
    }

    /** 是否手持任意咒具（供无下限等伤害判定） */
    public static boolean isHolding(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            ItemStack stack = player.getMainHandItem();
            return stack.getItem() instanceof CursedToolItem;
        }
        return false;
    }

    /** 该咒具能否穿透无下限防御（默认 false；天逆鉾覆写为 true） */
    public boolean ignoresInfinity() {
        return false;
    }
}
