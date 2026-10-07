package com.mofengbaizhi.tinkersnewlife.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * <b>鞭痕</b>（§1064）—— 用户口径（2026-10-05）：
 * <blockquote>
 * 「<b>被鞭子抽中的敌人会随着被抽中的次数逐渐降低速度和攻击伤害，最高降低80%，也就是被抽8次，每次降低10%</b>」
 * </blockquote>
 *
 * <h2>做法</h2>
 * 用 <b>属性修改器</b>（{@link AttributeModifier} ✓）而不是每 tick 去改数值 ✓ ——
 * 这样 <b>AI 怪、玩家、召唤物一视同仁</b> ✓，也让 {@code /attribute} 与界面里看得见 ✓：
 * <ul>
 *   <li>层数 ＝ <b>amplifier ＋ 1</b> ✓（amplifier 0～7 ⇒ 1～8 层 ✓）；</li>
 *   <li>每层 <b>−10%</b> ✓（{@link #REDUCTION_PER_STACK} ✓，{@code MULTIPLY_TOTAL} ⇒ ×(1−0.1·层数) ✓）；
 *       ⇒ <b>8 层 ＝ −80%</b> ✓（{@link #MAX_STACKS} ✓）；</li>
 *   <li>作用于 <b>移动速度</b> 与 <b>攻击伤害</b> 两条属性 ✓（正是用户说的"速度和攻击伤害" ✓）；</li>
 *   <li>时长 {@link #DURATION_TICKS}（10 秒 ✓）⇒ <b>每次抽中刷新</b> ✓；停手 10 秒后自然消退 ✓
 *       （即"越抽越弱 ✓、停手会缓过来 ✓"）。</li>
 * </ul>
 *
 * <p>⚠ 三个坑都绕开了 ✓：
 * ① <b>同一 UUID 的修改器不能重复挂</b> ✗ ⇒ {@link #addAttributeModifiers} 里**先 remove 再 add** ✓
 *    （既幂等 ✓、又能在层数上升时把数值更新掉 ✓）；
 * ② 属性可能不存在（例如某些实体没有 {@code ATTACK_DAMAGE} ✗）⇒ 全部 null 检查 ✓；
 * ③ 1.20.1 的取属性方法是 {@code AttributeMap#getInstance} ✓（**不是** {@code getAttribute} ✗ —— 编译报错后改的 ✓）。
 */
public class WhipWeakenEffect extends MobEffect {

    /** 最多 8 层 ✓（用户口径 ✓） */
    public static final int MAX_STACKS = 8;
    /** 每层降低 10% ✓（用户口径 ✓） */
    public static final double REDUCTION_PER_STACK = 0.10D;

    /**
     * ⭐ §1118j <b>鞭痕"加成"：每一层让**下一次**被鞭子抽中时的伤害 +10%</b> ✓ —— 用户口径：
     * 「当实体身上有**鞭痕效果**时，下一次被鞭子抽中的伤害将提升**每级 10%**」✓
     *
     * <p>⚠ 刻意与 {@link #REDUCTION_PER_STACK} **分成两个常量** ✓（虽然现在数值相同 ✓）：
     * 一个管"减益"✓ 一个管"加成"✓ —— 以后你想单独调哪个都不会牵动另一个 ✓。
     * <p>⚠ 口径说明（实现选择 ✓）：**不消耗**鞭痕 ✗ —— 因为同一鞭紧接着就会再叠一层（§1064 ✓），
     * 若这里消费掉 ⇒ 与"每次命中 +1 层"互相抵消 ⇒ 层数永远长不起来 ✗（会破坏鞭痕本身 ✓）。
     * ⇒ 现实现 ＝ "只要目标带着鞭痕，这一鞭就按层数加成" ✓（8 层 ⇒ ×1.8 ✓ 连击越抽越疼 ✓）。
     */
    public static final double BONUS_PER_STACK = 0.10D;
    /** 每次抽中给的时长（tick ✓）：10 秒 ✓，被抽中即刷新 ✓ */
    public static final int DURATION_TICKS = 200;

    /** 固定 UUID ✓（先 remove 再 add ⇒ 不会重复挂 ✓，层数上升也能更新 ✓） */
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("5c2a7d91-3e4b-4a6f-8d10-9b7c2e4f1a33");
    private static final UUID DAMAGE_MODIFIER_ID = UUID.fromString("9d4e1b27-6a3c-4f58-b2d7-0e8c5a9f4b61");

    public WhipWeakenEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
        double reduction = REDUCTION_PER_STACK * (amplifier + 1);

        AttributeInstance speed = attributes.getInstance(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
            speed.addTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, "tinkersnewlife.whip_weaken.speed",
                    -reduction, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }

        AttributeInstance damage = attributes.getInstance(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(DAMAGE_MODIFIER_ID);
            damage.addTransientModifier(new AttributeModifier(
                    DAMAGE_MODIFIER_ID, "tinkersnewlife.whip_weaken.damage",
                    -reduction, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
        AttributeInstance speed = attributes.getInstance(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
        AttributeInstance damage = attributes.getInstance(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(DAMAGE_MODIFIER_ID);
        }
    }
}
