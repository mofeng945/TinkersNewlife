package com.mofengbaizhi.tinkersnewlife.content.modifier.katana;

// 移植自 TiCEX (MIT): moffy.ticex.modifier.ModifierEnchantmentSupplier

import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.armor.ProtectionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.behavior.EnchantmentModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.behavior.ToolDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 「附魔供给」—— 逐字移植 TiCEX {@code ModifierEnchantmentSupplier}（MIT）。
 *
 * <p>它存在的唯一理由：拔刀剑把附魔<b>写在了物品 NBT 的 {@code Enchantments} 列表里</b> ✓，
 * 而匠魂工具本身不认这份 NBT ✗ ⇒ 这个修饰符把那份附魔"翻译"给匠魂的附魔/近战/护甲/耐久四条管线 ✓：
 * <ul>
 *   <li>{@code ENCHANTMENTS}：把物品 NBT 上的附魔并进匠魂的附魔视图 ✓；</li>
 *   <li>{@code MELEE_DAMAGE}／{@code MELEE_HIT}：锋利/亡灵杀手/节肢杀手的增伤、击退加成、火焰附加与命中后效果 ✓；</li>
 *   <li>{@code TOOL_DAMAGE}：耐久（Unbreaking）按原版概率免伤 ✓；</li>
 *   <li>{@code PROTECTION}：保护类附魔的减伤 ✓。</li>
 * </ul>
 * <p>★ {@link #shouldDisplay(boolean)} 恒 false ✓（它是**隐藏**的内部修饰符 ✓ 与原版一致 ✓）。
 */
public class ModifierEnchantmentSupplier extends NoLevelsModifier
        implements MeleeHitModifierHook, EnchantmentModifierHook, ToolDamageModifierHook,
        MeleeDamageModifierHook, ProtectionModifierHook {

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        hookBuilder.addHook(
                this,
                ModifierHooks.MELEE_HIT,
                ModifierHooks.ENCHANTMENTS,
                ModifierHooks.TOOL_DAMAGE,
                ModifierHooks.MELEE_DAMAGE,
                ModifierHooks.PROTECTION
        );
    }

    @Override
    public boolean shouldDisplay(boolean advanced) {
        return false;
    }

    @Override
    public float beforeMeleeHit(IToolStackView iToolStackView, ModifierEntry modifier, ToolAttackContext context, float damage, float baseKnockback, float knockback) {
        return knockback + EnchantmentHelper.getKnockbackBonus(context.getAttacker()) * 0.5f;
    }

    @Override
    public void afterMeleeHit(IToolStackView iToolStackView, ModifierEntry modifier, ToolAttackContext context, float damageDealt) {
        int fireAspect = EnchantmentHelper.getFireAspect(context.getAttacker());
        context.getTarget().setRemainingFireTicks(80 * fireAspect);
        EnchantmentHelper.doPostDamageEffects(context.getAttacker(), context.getTarget());
        if (context.getTarget() instanceof LivingEntity livingTarget) {
            EnchantmentHelper.doPostHurtEffects(livingTarget, context.getAttacker());
        }
    }

    @Override
    public int updateEnchantmentLevel(IToolStackView iToolStackView, ModifierEntry modifierEntry, Enchantment enchantment, int i) {
        int level = i;
        if (iToolStackView instanceof ToolStack tool) {
            ItemStack toolStack = tool.createStack();
            level += EnchantmentHelper.getTagEnchantmentLevel(enchantment, toolStack);
        }
        return level;
    }

    @Override
    public void updateEnchantments(IToolStackView iToolStackView, ModifierEntry modifierEntry, Map<Enchantment, Integer> map) {
        if (iToolStackView instanceof ToolStack tool) {
            ItemStack toolStack = tool.createStack();
            map.putAll(EnchantmentHelper.getEnchantments(toolStack));
        }
    }

    @Override
    public int onDamageTool(IToolStackView iToolStackView, ModifierEntry modifierEntry, int damage, @Nullable LivingEntity livingEntity) {
        if (iToolStackView instanceof ToolStack tool) {
            ItemStack toolStack = tool.createStack();
            int lvl = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.UNBREAKING, toolStack);
            int actual = 0;
            if (livingEntity != null) {
                RandomSource rand = livingEntity.getRandom();
                for (int i = 0; i < damage; i++) {
                    if (rand.nextInt(lvl + 1) == 0) actual++;
                }
            }
            return actual;
        }
        return damage;
    }

    @Override
    public float getProtectionModifier(IToolStackView iToolStackView, ModifierEntry modifierEntry, EquipmentContext equipmentContext, EquipmentSlot equipmentSlot, DamageSource damageSource, float v) {
        if (iToolStackView instanceof ToolStack tool) {
            ItemStack toolStack = tool.createStack();
            return v + EnchantmentHelper.getDamageProtection(List.of(toolStack), damageSource);
        }
        return v;
    }

    @Override
    public float getMeleeDamage(IToolStackView iToolStackView, ModifierEntry modifierEntry, ToolAttackContext toolAttackContext, float v, float v1) {
        if (iToolStackView instanceof ToolStack tool && toolAttackContext.getTarget() instanceof Mob mob) {
            ItemStack toolStack = tool.createStack();
            return v1 + EnchantmentHelper.getDamageBonus(toolStack, mob.getMobType());
        }
        return v1;
    }
}
