package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FusRoDahHandler {

    private static final ModifierId FUS_RO_DAH = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "fus_ro_dah"));

    private static final double BASE_THRUST = 1.5;
    private static final double THRUST_PER_LEVEL = 1.0;
    private static final float BASE_DAMAGE = 2.0f;
    private static final float DAMAGE_PER_LEVEL = 2.0f;
    private static final float SHOCKWAVE_RADIUS_BASE = 3.0f;
    private static final float SHOCKWAVE_RADIUS_PER_LEVEL = 1.0f;
    private static final float SHOCKWAVE_DAMAGE_BASE = 1.0f;
    private static final float UPWARD_THRUST_MULTIPLIER = 0.4f;
    private static final int SONIC_PARTICLE_COUNT = 30;
    private static final int EXPLOSION_PARTICLE_COUNT = 50;
    private static final float SOUND_VOLUME = 2.0f;
    private static final float SOUND_PITCH = 0.8f;

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (player.level().isClientSide) return;

        // ⭐ 统一取工具（近战/弹射双路径 + 校验），主手无武器时兜底取佩戴的咒力核心
        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), player, FUS_RO_DAH);
        if (tool == null) return;

        int level = tool.getModifierLevel(FUS_RO_DAH);
        if (level > 0) applyFusRoDah(player, target, level);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getProjectile().getOwner() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // ⭐ 统一取工具（弹射路径 + 校验），主手无武器时兜底取佩戴的咒力核心
        ToolStack tool = ToolHelper.getCombatToolWith(event.getProjectile(), player, FUS_RO_DAH);
        if (tool == null) return;

        int level = tool.getModifierLevel(FUS_RO_DAH);
        if (level > 0) applyFusRoDah(player, target, level);
    }

    private static void applyFusRoDah(Player attacker, LivingEntity target, int level) {
        Level levelObj = target.level();

        float volume = SOUND_VOLUME + (level - 1) * 0.3f;
        float pitch = SOUND_PITCH - (level - 1) * 0.05f;
        levelObj.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, volume, pitch);

        if (levelObj instanceof ServerLevel serverLevel) {
            spawnParticles(serverLevel, attacker, target, level);
        }

        Vec3 direction = attacker.getLookAngle().normalize();
        double thrust = BASE_THRUST + (level - 1) * THRUST_PER_LEVEL;
        Vec3 velocity = new Vec3(
                direction.x * thrust,
                direction.y * thrust + UPWARD_THRUST_MULTIPLIER * thrust,
                direction.z * thrust
        );
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;

        float damage = BASE_DAMAGE + (level - 1) * DAMAGE_PER_LEVEL;
        target.hurt(levelObj.damageSources().generic(), damage);

        float shockwaveRadius = SHOCKWAVE_RADIUS_BASE + (level - 1) * SHOCKWAVE_RADIUS_PER_LEVEL;
        float shockwaveDamage = SHOCKWAVE_DAMAGE_BASE * level;

        List<Entity> entities = levelObj.getEntities(target,
                target.getBoundingBox().inflate(shockwaveRadius),
                e -> e instanceof LivingEntity && e != attacker && e != target);

        for (Entity entity : entities) {
            Vec3 toEntity = entity.position().subtract(target.position());
            double distance = toEntity.length();
            if (distance < 0.1) continue;

            double strength = thrust * (1.0 - distance / shockwaveRadius);
            Vec3 pushVec = toEntity.normalize().scale(strength * 0.5);

            entity.setDeltaMovement(
                    entity.getDeltaMovement().x + pushVec.x,
                    entity.getDeltaMovement().y + pushVec.y + UPWARD_THRUST_MULTIPLIER * strength * 0.3,
                    entity.getDeltaMovement().z + pushVec.z
            );
            entity.hurtMarked = true;

            if (entity instanceof LivingEntity living) {
                living.hurt(levelObj.damageSources().generic(), shockwaveDamage * 0.5f);
            }
        }
    }

    private static void spawnParticles(ServerLevel serverLevel, LivingEntity attacker, Entity target, int level) {
        Vec3 from = attacker.getEyePosition();
        Vec3 to = target.position();
        Vec3 direction = to.subtract(from).normalize();
        double distance = from.distanceTo(to);

        for (int i = 0; i < SONIC_PARTICLE_COUNT; i++) {
            double progress = i / (double) SONIC_PARTICLE_COUNT;
            Vec3 pos = from.add(direction.scale(progress * distance));
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (serverLevel.random.nextDouble() - 0.5) * 0.5;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.5;
            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                    pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                    1, 0, 0, 0, 0);
        }

        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);

        for (int i = 0; i < EXPLOSION_PARTICLE_COUNT; i++) {
            double radius = serverLevel.random.nextDouble() * 1.5;
            double theta = serverLevel.random.nextDouble() * 2 * Math.PI;
            double phi = serverLevel.random.nextDouble() * Math.PI;
            double dx = radius * Math.sin(phi) * Math.cos(theta);
            double dy = radius * Math.cos(phi) * 0.5 + 0.5;
            double dz = radius * Math.sin(phi) * Math.sin(theta);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    target.getX() + dx, target.getY() + dy, target.getZ() + dz,
                    1, 0, 0, 0, 0);
        }

        float shockwaveRadius = SHOCKWAVE_RADIUS_BASE + (level - 1) * SHOCKWAVE_RADIUS_PER_LEVEL;
        for (int i = 0; i < 36; i++) {
            double angle = i * Math.PI / 18;
            double dx = shockwaveRadius * Math.cos(angle);
            double dz = shockwaveRadius * Math.sin(angle);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    target.getX() + dx, target.getY() + 0.2, target.getZ() + dz,
                    2, 0, 0.05, 0, 0.05);
        }
    }
}