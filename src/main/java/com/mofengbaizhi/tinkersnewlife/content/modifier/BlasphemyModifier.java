package com.mofengbaizhi.tinkersnewlife.content.modifier;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.armor.ModifyDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.build.ModifierSlotModule;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * 无槽位强化·渎神：安装不占槽，给护甲 +2 防御槽，代价是受到的伤害 +10%。
 * 代价走 TCon 护甲 {@link ModifierHooks#MODIFY_DAMAGE} 钩子（护甲减免与吸收结算之后，
 * 作用于最终伤害；多件穿戴时逐件链式叠乘——与终末烙印的 AttributeModule 方案不同，
 * 受伤倍率无法用属性表达，故走伤害钩子）。
 * 单级（配方 level 1），不可洗掉、不可剥离。
 */
public class BlasphemyModifier extends Modifier implements ModifyDamageModifierHook {

    /** 每级受到的伤害倍率：+10% */
    private static final float DAMAGE_TAKEN_PER_LEVEL = 1.10f;

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        // +2 防御槽（volatile data，作用于护甲）
        hookBuilder.addModule(ModifierSlotModule.slot(SlotType.DEFENSE).amount(2, 0));
        // 受到的伤害 +10%（MODIFY_DAMAGE：护甲减免/吸收之后、最接近最终伤害）
        hookBuilder.addHook(this, ModifierHooks.MODIFY_DAMAGE);
    }

    @Override
    public float modifyDamageTaken(IToolStackView tool, ModifierEntry modifier, EquipmentContext context,
                                   EquipmentSlot slotType, DamageSource source, float amount,
                                   boolean isDirectDamage) {
        if (amount > 0) {
            amount *= (float) Math.pow(DAMAGE_TAKEN_PER_LEVEL, modifier.getLevel());
        }
        return amount;
    }

}
