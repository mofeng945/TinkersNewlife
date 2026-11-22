package com.mofengbaizhi.tinkersnewlife.content.modifier;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.item.ModifiableArrowItem;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.entity.ModifiableArrow;

import javax.annotation.Nullable;

/** 羽蛇之力：属性属于箭，不属于发射器。 */
public class AmphitherePowerModifier extends NoLevelsModifier implements ToolStatsModifierHook, ProjectileHitModifierHook {
    @Override
    protected void registerHooks(ModuleHookMap.Builder builder) {
        builder.addHook(this, ModifierHooks.TOOL_STATS);
        builder.addHook(this, ModifierHooks.PROJECTILE_HIT);
    }

    @Override
    public void addToolStats(IToolContext context, ModifierEntry modifier, ModifierStatsBuilder builder) {
        if (ModList.get().isLoaded("iceandfire") && context.getItem() instanceof ModifiableArrowItem) {
            ToolStats.PROJECTILE_DAMAGE.add(builder, 4f);
        }
    }

    @Override
    public boolean onProjectileHitEntity(ModifierNBT modifiers, ModDataNBT data, ModifierEntry modifier,
                                         Projectile projectile, EntityHitResult hit, @Nullable LivingEntity shooter,
                                         @Nullable LivingEntity target, boolean notBlocked) {
        // TCon merges launcher and ammo modifiers on the entity. Check the stored arrow stack itself.
        if (!notBlocked || target == null || !(projectile.level() instanceof ServerLevel level)
                || !(projectile instanceof ModifiableArrow arrow) || !ModList.get().isLoaded("iceandfire")
                || ToolStack.from(arrow.getDisplayTool()).getModifierLevel(getId()) == 0) return false;

        level.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX(), target.getY(0.5), target.getZ(),
                48, 0.45, 0.4, 0.45, 0.08);
        Vec3 direction = projectile.getDeltaMovement();
        if (direction.horizontalDistanceSqr() < 1.0e-6 && shooter != null) {
            direction = target.position().subtract(shooter.position());
        }
        if (direction.horizontalDistanceSqr() >= 1.0e-6) {
            target.knockback(3.0, -direction.x, -direction.z);
            target.hurtMarked = true;
        }
        return false; // Preserve ordinary arrow impact and subsequent modifier hooks.
    }
}
