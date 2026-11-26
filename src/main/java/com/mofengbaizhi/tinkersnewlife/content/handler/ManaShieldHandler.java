package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.content.modifier.ManaShieldTrait;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 巫师套装特性·<b>魔力护盾</b>的<b>属性侧</b>结算：每件 <b>生命上限 +2</b> ✓。
 *
 * <p>减伤与"效果时长减半"不走这里（前者是 TCon 护甲钩子 {@code MODIFY_DAMAGE} ✓ 在
 * {@link ManaShieldTrait#modifyDamageTaken} ✓；后者在 {@code mixin.ManaShieldEffectMixin} ✓）。
 *
 * <p>⭐ 与「魔力涌动」{@link WizardArmorSetHandler} 同一套做法：
 * <b>瞬时属性修饰符</b>（固定 UUID ⇒ 刷新即替换 ✓ 幂等 ✓ 不是往玩家身上塞状态 ✗），
 * 由 {@code WizardArmorSetHandler} 的每秒扫描<b>顺带</b>调用 ✓
 * —— 合并成一次实体遍历，避免为每个特性各扫一遍全世界 ✗。
 *
 * <p>⚠ 生命上限是<b>原版</b>属性 ✓ 不需要软依赖；但脱甲时把上限降回去会让当前血量被夹到新上限 ✓
 * （原版行为 ✓ 与"脱下加血装备"一致 ✓）。
 */
public final class ManaShieldHandler {

    private ManaShieldHandler() {}

    /** 修饰符身份（固定 UUID ✓ 保证"刷新 = 替换" ✓ 不叠加 ✗） */
    private static final UUID HEALTH_UUID = UUID.fromString("7a13c0de-0004-4a11-9d10-1c5e0f5a0004");

    /** 该实体身上有没有我们挂的生命上限修饰符（用于脱甲后的清理 ✓） */
    public static boolean hasOurModifier(LivingEntity living) {
        AttributeInstance hp = living.getAttribute(Attributes.MAX_HEALTH);
        return hp != null && hp.getModifier(HEALTH_UUID) != null;
    }

    /** 按件数刷新生命上限修饰符（0 件 ⇒ 移除 ✓ 幂等 ✓） */
    public static void applyShield(LivingEntity living, int pieces) {
        AttributeInstance hp = living.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        double amount = pieces * (double) ManaShieldTrait.HEALTH_PER_PIECE;
        AttributeModifier old = hp.getModifier(HEALTH_UUID);
        if (amount <= 0.0D) {
            if (old != null) hp.removeModifier(HEALTH_UUID);
            return;
        }
        if (old != null && old.getAmount() == amount) return;   // 没变就别动 ✗（避免每帧同步）
        if (old != null) hp.removeModifier(HEALTH_UUID);
        hp.addTransientModifier(new AttributeModifier(HEALTH_UUID, "tn_wizard_mana_shield_hp", amount,
                AttributeModifier.Operation.ADDITION));
    }
}
