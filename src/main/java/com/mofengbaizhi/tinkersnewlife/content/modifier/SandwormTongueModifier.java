package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class SandwormTongueModifier extends NoLevelsModifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("sandworm_tongue"));
    private static final ResourceKey<DamageType> BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, TinkersNewlife.prefix("sandworm_blast"));
    private static final double RADIUS = 2;
    private record Impact(LivingEntity caster, LivingEntity target, Vec3 center, float damage) {}
    private static final Map<ServerLevel, List<Impact>> PENDING = new IdentityHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hit(LivingHurtEvent event) {
        var target = event.getEntity();
        var source = event.getSource();
        if (!(target.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity caster)
                || !IntegrationLoader.isIceAndFire() || source.is(BLAST) || !canHit(caster, target)
                || !Float.isFinite(event.getAmount()) || event.getAmount() <= 0) return;
        boolean active;
        if (source.getDirectEntity() instanceof Projectile projectile) {
            // The launcher modifiers travel with the projectile; swapping bows before impact has no effect.
            active = EntityModifierCapability.getOrEmpty(projectile).getLevel(ID) > 0;
        } else if (source.getDirectEntity() == caster && (source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) {
            active = ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(caster.getMainHandItem()), ID) > 0;
        } else return;
        if (!active) return;
        float blastDamage = (float)Math.min(Float.MAX_VALUE, event.getAmount() * 1.5);
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(new Impact(caster, target,
                target.position().add(0, target.getBbHeight() * .5, 0), blastDamage));
    }

    private static boolean canHit(LivingEntity caster, LivingEntity target) {
        return target != caster && target.isAlive() && !target.isSpectator() && !caster.isAlliedTo(target)
                && (!(caster instanceof Player player) || !(target instanceof Player other) || player.canHarmPlayer(other));
    }

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var impacts = PENDING.remove(level);
        if (impacts == null) return;
        for (var hit : impacts) explode(level, hit);
    }

    private static void explode(ServerLevel level, Impact hit) {
        var caster = hit.caster();
        var target = hit.target();
        var center = hit.center();
        if (caster.level() != level || caster.isRemoved()) return;
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLAST);
        var source = new DamageSource(type, caster, caster) {
            @Override public Vec3 getSourcePosition() { return center; }
        };
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1, 1);
        // Custom blast: no block mutations, fire, or vanilla damage falloff.
        var area = new net.minecraft.world.phys.AABB(center, center).inflate(RADIUS);
        for (var victim : level.getEntitiesOfClass(LivingEntity.class, area, e -> canHit(caster, e))) {
            var box = victim.getBoundingBox();
            double x = center.x - net.minecraft.util.Mth.clamp(center.x, box.minX, box.maxX);
            double y = center.y - net.minecraft.util.Mth.clamp(center.y, box.minY, box.maxY);
            double z = center.z - net.minecraft.util.Mth.clamp(center.z, box.minZ, box.maxZ);
            if (x*x + y*y + z*z <= RADIUS*RADIUS) victim.hurt(source, hit.damage());
        }
        if (target.level() != level || !canHit(caster, target)) return;
        Vec3 delta = caster.position().subtract(target.position());
        double distance = delta.length();
        if (distance > 1.5) {
            // Move before Stun freezes motion. Sweep in short collision-checked increments.
            double pull = Math.min(32, distance - 1.5);
            int steps = (int)Math.ceil(pull / .5);
            Vec3 step = delta.scale(pull / (steps * distance));
            for (int i = 0; i < steps; i++) {
                Vec3 before = target.position();
                target.move(MoverType.SELF, step);
                if (target.position().subtract(before).distanceToSqr(step) > 1e-6) break;
            }
            if (target instanceof ServerPlayer player) player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        StunHandler.apply(target, 60, StunHandler.Mode.FULL);
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }
}
