package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.weapon.SweepWeaponAttack;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.tools.TinkerModifiers;

public final class RaptorPounceModifier extends NoLevelsModifier implements MeleeHitModifierHook {
    private static final ModifierId ID = new ModifierId(new ResourceLocation("tinkersnewlife", "raptor_pounce"));
    private static final ResourceLocation SWEEP_PERCENT = new ResourceLocation("tconstruct", "sweep_percent");

    public static boolean applies(IToolStackView tool, ToolAttackContext context) {
        return !context.isProjectile() && !context.isExtraAttack() && IntegrationLoader.isIceAndFire()
                && !tool.isBroken() && tool.hasTag(TinkerTags.Items.MELEE) && tool.getModifierLevel(ID) > 0;
    }

    @Override protected void registerHooks(ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook(this, ModifierHooks.MELEE_HIT);
    }

    @Override public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damage) {
        sweep(tool, context, damage);
    }

    @Override public void failedMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damage) {
        sweep(tool, context, damage);
    }

    private static void sweep(IToolStackView tool, ToolAttackContext context, float damage) {
        LivingEntity attacker = context.getAttacker();
        if (attacker.level().isClientSide || !applies(tool, context)) return;
        var target = context.getTarget();
        if (target == null) return;

        var hitHook = tool.getHook(ToolHooks.MELEE_HIT);
        float range = hitHook instanceof SweepWeaponAttack sweep ? sweep.range() : 1.0f;
        range += Math.max(tool.getModifierLevel(TinkerModifiers.expanded.getId()),
                tool.getVolatileData().getInt(new ResourceLocation("tconstruct", "expanded")));
        float percent = tool.getVolatileData().getFloat(SWEEP_PERCENT);
        float sweepDamage = percent > 0
                ? percent > 1 ? damage : Math.min(damage, 1 + percent * damage)
                : TinkerModifiers.sweeping.get().getSweepingDamage(tool, damage);
        float yaw = attacker.getYRot() * Mth.DEG_TO_RAD;
        double maxDistance = 2 + range;
        var source = context.makeDamageSource();
        for (LivingEntity neighbor : attacker.level().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(range, 0.25, range))) {
            if (neighbor == attacker || neighbor == target || neighbor == context.getLivingTarget()
                    || !neighbor.isAlive() || neighbor.isSpectator() || attacker.isAlliedTo(neighbor)
                    || (neighbor instanceof ArmorStand stand && stand.isMarker())
                    || attacker.distanceToSqr(neighbor) >= maxDistance * maxDistance) continue;
            neighbor.knockback(0.4, Mth.sin(yaw), -Mth.cos(yaw));
            neighbor.hurt(source, sweepDamage);
        }
        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, attacker.getSoundSource(), 1.0f, 1.0f);
        if (attacker instanceof Player player) player.sweepAttack();
    }
}
