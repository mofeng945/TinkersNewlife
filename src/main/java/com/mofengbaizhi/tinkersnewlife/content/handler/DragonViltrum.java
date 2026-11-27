package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class DragonViltrum {
    public static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("dragon_viltrum"));
    private static final String ENABLED = "tnlDragonViltrum";
    private static final String READY_AT = "tnlViltrumReadyAt";
    private static final float CHANCE = .05f;
    private static final int COOLDOWN = 400;
    private static final Map<UUID, Throw> THROWS = new HashMap<>();
    private static final Map<UUID, Throw> VICTIMS = new HashMap<>();
    private DragonViltrum() {}

    private static final class Throw {
        final ServerLevel level;
        final LivingEntity dragon, victim;
        final DragonViltrumFlight.Plan plan;
        int tick;
        Throw(ServerLevel level, LivingEntity dragon, LivingEntity victim, DragonViltrumFlight.Plan plan) {
            this.level = level; this.dragon = dragon; this.victim = victim; this.plan = plan;
        }
    }

    public static void mark(Entity dragon, boolean enabled) {
        if (enabled) dragon.getPersistentData().putBoolean(ENABLED, true);
        else {
            dragon.getPersistentData().remove(ENABLED);
            var active = THROWS.get(dragon.getUUID());
            if (active != null) end(active);
        }
    }

    private static void end(Throw active) {
        THROWS.remove(active.dragon.getUUID(), active);
        VICTIMS.remove(active.victim.getUUID(), active);
    }

    @SubscribeEvent(priority = EventPriority.MONITOR)
    public static void hit(LivingDamageEvent event) {
        var source = event.getSource();
        var victim = event.getEntity();
        if (source instanceof DragDamage || !source.is(DamageTypes.MOB_ATTACK)
                || !(source.getEntity() instanceof TamableAnimal dragon) || source.getDirectEntity() != dragon
                || !(dragon.level() instanceof ServerLevel level) || !dragon.isTame()
                || !dragon.getPersistentData().getBoolean(ENABLED) || dragon.getPersistentData().getLong(READY_AT) > level.getGameTime()
                || !(event.getAmount() > 0) || !Float.isFinite(event.getAmount()) || event.getAmount() >= victim.getHealth()
                || !eligible(dragon, victim) || !stageThree(dragon) || dragon.getRandom().nextFloat() >= CHANCE) return;
        var plan = DragonViltrumFlight.create(level, dragon, victim);
        if (plan == null) return;
        dragon.getPersistentData().putLong(READY_AT, level.getGameTime() + COOLDOWN);
        var active = new Throw(level, dragon, victim, plan);
        THROWS.put(dragon.getUUID(), active);
        VICTIMS.put(victim.getUUID(), active);
        level.playSound(null, dragon.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1f, .8f);
    }

    private static boolean eligible(TamableAnimal dragon, LivingEntity victim) {
        return victim != dragon && victim.level() == dragon.level() && victim.isAlive() && !victim.isSpectator()
                && !dragon.isAlliedTo(victim) && !victim.isAlliedTo(dragon) && !dragon.isOwnedBy(victim)
                && !dragon.isVehicle() && !dragon.isPassenger() && !dragon.isOrderedToSit()
                && !victim.isVehicle() && !victim.isPassenger()
                && victim.getBbWidth() < EntityType.IRON_GOLEM.getWidth() && victim.getBbHeight() < EntityType.IRON_GOLEM.getHeight()
                && !THROWS.containsKey(dragon.getUUID()) && !VICTIMS.containsKey(dragon.getUUID())
                && !THROWS.containsKey(victim.getUUID()) && !VICTIMS.containsKey(victim.getUUID());
    }

    private static boolean stageThree(LivingEntity dragon) {
        try { return ((Number)dragon.getClass().getMethod("getDragonStage").invoke(dragon)).intValue() >= 3; }
        catch (ReflectiveOperationException ignored) { return false; }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || THROWS.isEmpty()) return;
        for (var active : java.util.List.copyOf(THROWS.values())) {
            var dragon = active.dragon;
            var victim = active.victim;
            if (!dragon.isAlive() || !victim.isAlive() || dragon.isRemoved() || victim.isRemoved()
                    || dragon.level() != active.level || victim.level() != active.level
                    || !dragon.getPersistentData().getBoolean(ENABLED) || victim.isSpectator()
                    || dragon.isVehicle() || dragon.isPassenger() || victim.isVehicle() || victim.isPassenger()) {
                end(active);
                continue;
            }
            if (dragon instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
            if (victim instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
            if (!active.plan.step(active.tick)) { end(active); continue; }
            if (active.plan.isDragging(active.tick)) {
                if (active.tick == 0 || !active.plan.isDragging(active.tick - 1)) {
                    active.level.playSound(null, victim.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, .65f, 1.25f);
                }
                active.level.sendParticles(ParticleTypes.POOF, victim.getX(), victim.getY() + .12, victim.getZ(), 4, .25, .08, .25, .03);
                victim.hurt(new DragDamage(dragon), (float)(dragon.getAttributeValue(Attributes.ATTACK_DAMAGE) * .05));
            }
            if (++active.tick >= active.plan.duration() || !victim.isAlive()) end(active);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void suppressConcurrentAttacks(LivingAttackEvent event) {
        if (event.getSource() instanceof DragDamage || event.getEntity().level().isClientSide) return;
        var attacker = event.getSource().getEntity();
        if (attacker != null && (THROWS.containsKey(attacker.getUUID()) || VICTIMS.containsKey(attacker.getUUID()))) event.setCanceled(true);
    }

    public static boolean isDragDamage(DamageSource source) { return source instanceof DragDamage; }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void protectTerrain(net.minecraftforge.event.entity.EntityMobGriefingEvent event) {
        if (THROWS.containsKey(event.getEntity().getUUID())) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    @SubscribeEvent public static void shutdown(ServerStoppedEvent event) { THROWS.clear(); VICTIMS.clear(); }

    private static final class DragDamage extends DamageSource {
        DragDamage(LivingEntity dragon) { super(dragon.damageSources().mobAttack(dragon).typeHolder(), dragon); }
        // Only this damage sequence bypasses repeated-hit cooldown; normal physical defenses still apply.
        @Override public boolean is(TagKey<DamageType> tag) { return tag.equals(DamageTypeTags.BYPASSES_COOLDOWN) || super.is(tag); }
    }
}
