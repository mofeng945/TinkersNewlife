package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.shared.TinkerEffects;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Only successful weapon hits proc. In particular, a held weapon cannot lend its catalyst to an unrelated shot. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class CrystalCatalystCombat {
    private CrystalCatalystCombat() {}
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("crystal_catalyst"));
    private static final Map<ServerLevel, List<LivingEntity>> DROWNING = new IdentityHashMap<>();

    public static final MeleeHitModifierHook MELEE = new MeleeHitModifierHook() {
        @Override public void afterMeleeHit(IToolStackView tool, ModifierEntry entry, ToolAttackContext context, float damage) {
            if (!context.isProjectile() && damage > 0 && !tool.isBroken()) {
                apply(context.getAttacker(), context.getLivingTarget(), entry.getLevel());
            }
        }
    };

    private static int shotRank(Projectile shot) { return EntityModifierCapability.getOrEmpty(shot).getLevel(ID); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void projectileDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getAmount() > 0) || event.getSource().is(DamageTypes.DROWN)) return;
        if (event.getSource().getDirectEntity() instanceof Projectile shot && event.getSource().getEntity() instanceof LivingEntity shooter) {
            apply(shooter, event.getEntity(), shotRank(shot));
        }
    }

    /** Endermen normally dodge arrows before a damage event; pre-apply Enderference on physical contact in the End.
     * TCon's lower-priority impact handler then deals its own normal arrow damage. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void endermanContact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || arrow.level().isClientSide
                || !(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof EnderMan target)
                || !(arrow.getOwner() instanceof LivingEntity shooter) || !IntegrationLoader.isIceAndFire()) return;
        int rank = shotRank(arrow);
        if (rank > 0 && shooter.level().getBiome(shooter.blockPosition()).is(BiomeTags.IS_END)) {
            effect(target, TinkerEffects.enderference.get(), rank, shooter);
        }
    }

    private static void apply(LivingEntity attacker, LivingEntity target, int rank) {
        if (rank <= 0 || target == null || target == attacker || !target.isAlive() || !(attacker.level() instanceof ServerLevel level)
                || target.level() != level || !IntegrationLoader.isIceAndFire()) return;
        Holder<Biome> biome = level.getBiome(attacker.blockPosition());
        if (biome.is(Tags.Biomes.IS_DESERT)) {
            effect(target, MobEffects.HUNGER, rank, attacker);
            if (target.getMobType() == MobType.UNDEAD) {
                var head = target.getItemBySlot(EquipmentSlot.HEAD);
                if (head.isEmpty()) target.setSecondsOnFire(8);
                else if (head.isDamageableItem() && target.getRandom().nextBoolean()) {
                    head.hurtAndBreak(1, target, e -> e.broadcastBreakEvent(EquipmentSlot.HEAD));
                }
            }
        }
        if (biome.is(BiomeTags.IS_JUNGLE) || biome.is(Tags.Biomes.IS_SWAMP)) effect(target, MobEffects.POISON, rank, attacker);
        if (biome.is(Tags.Biomes.IS_COLD)) effect(target, ModEffects.FROST.get(), rank, attacker);
        if (biome.is(BiomeTags.IS_OCEAN)) {
            target.setAirSupply(0);
            // Finish the bonus after the underlying hit, once for each ocean hit, not by recursively invoking attack().
            DROWNING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(target);
        }
        if (biome.is(BiomeTags.IS_NETHER)) effect(target, MobEffects.WITHER, rank, attacker);
        if (biome.is(BiomeTags.IS_END)) effect(target, TinkerEffects.enderference.get(), rank, attacker);
    }

    private static void effect(LivingEntity target, MobEffect effect, int rank, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(effect, (int)Math.min(40L * rank, Integer.MAX_VALUE), 0), attacker);
    }

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var pending = DROWNING.remove(level);
        if (pending == null) return;
        // Keep the vanilla drowning damage type and normal defenses. Ignore only the immediately preceding hit's i-frames.
        var drown = new DamageSource(level.damageSources().drown().typeHolder()) {
            @Override public boolean is(TagKey<DamageType> tag) { return DamageTypeTags.BYPASSES_COOLDOWN.equals(tag) || super.is(tag); }
        };
        for (LivingEntity target : pending) {
            if (target.level() == level && target.isAlive() && !target.isRemoved()) target.hurt(drown, 2f);
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) DROWNING.remove(level);
    }
}
