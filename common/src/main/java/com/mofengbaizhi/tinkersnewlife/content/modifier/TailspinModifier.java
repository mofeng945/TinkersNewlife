package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

public final class TailspinModifier extends NoLevelsModifier implements MeleeHitModifierHook {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("tailspin"));
    private static final float EXTRA_KNOCKBACK = 1.5f; // Vanilla applies 0.5 knockback strength per level.
    private static final int DEBUFF_DURATION = 60;

    public static boolean applies(IToolStackView tool, ToolAttackContext context) {
        return IntegrationLoader.isIceAndFire() && !context.isProjectile() && !context.isExtraAttack()
                && !tool.isBroken() && tool.hasTag(TinkerTags.Items.MELEE) && tool.getModifierLevel(ID) > 0;
    }

    @Override protected void registerHooks(ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook(this, ModifierHooks.MELEE_HIT);
        builder.addHook((ToolStatsModifierHook) (tool, entry, stats) -> {
            if (IntegrationLoader.isIceAndFire() && tool.hasTag(TinkerTags.Items.MELEE)) ToolStats.ATTACK_DAMAGE.multiply(stats, .95);
        }, ModifierHooks.TOOL_STATS);
    }

    @Override public float beforeMeleeHit(IToolStackView tool, ModifierEntry entry, ToolAttackContext context,
                                          float damage, float baseKnockback, float knockback) {
        return applies(tool, context) ? knockback + EXTRA_KNOCKBACK : knockback;
    }

    @Override public void afterMeleeHit(IToolStackView tool, ModifierEntry entry, ToolAttackContext context, float damage) {
        var attacker = context.getAttacker();
        if (attacker.level().isClientSide || !applies(tool, context)) return;
        if (context.getLivingTarget() != null) disorient(context.getLivingTarget(), attacker);
        RaptorPounceModifier.performSweep(tool, context, damage, true);
        recoil(attacker);
    }

    @Override public void failedMeleeHit(IToolStackView tool, ModifierEntry entry, ToolAttackContext context, float damage) {
        var attacker = context.getAttacker();
        if (attacker.level().isClientSide || !applies(tool, context)) return;
        RaptorPounceModifier.performSweep(tool, context, damage, true);
        recoil(attacker);
    }

    static void disorient(LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DEBUFF_DURATION), attacker);
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DEBUFF_DURATION), attacker);
    }

    private static void recoil(LivingEntity attacker) {
        if (!attacker.isInWater()) return;
        var push = attacker.getLookAngle().scale(-.8);
        attacker.push(push.x, push.y, push.z);
        attacker.hurtMarked = true;
    }
}
