package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.*;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class WitherGazeModifier extends NoLevelsModifier implements MeleeHitModifierHook {
    private static final int DURATION = 100;
    private static final int RADIUS = 6;
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("wither_gaze"));
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> BEAM_DAMAGE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    TinkersNewlife.prefix("wither_gaze"));
    private record Link(UUID caster, UUID target) {}
    private record Cast(LivingEntity caster, LivingEntity target, LivingEntity[] branches) {}
    private static final class Beam {
        final LivingEntity caster, target;
        LivingEntity source;
        long expires;
        long pulse;
        Beam(LivingEntity caster, LivingEntity source, LivingEntity target, long now) {
            this.caster = caster;
            this.source = source;
            this.target = target;
            this.pulse = now + 1;
        }
    }
    private static final class State {
        final Map<Link, Beam> beams = new HashMap<>();
        final List<Cast> pending = new ArrayList<>();
    }
    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();

    @Override protected void registerHooks(ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook(this, ModifierHooks.MELEE_HIT);
    }

    private static boolean eligible(LivingEntity caster, LivingEntity target) {
        return target != caster && target.isAlive() && !target.isSpectator() && !caster.isAlliedTo(target)
                && !(target instanceof ArmorStand)
                && (!(caster instanceof Player player) || !(target instanceof Player other) || player.canHarmPlayer(other));
    }

    @Override public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damage) {
        if (!context.isProjectile() && !context.isExtraAttack()) {
            queueCast(context.getAttacker(), context.getLivingTarget());
        }
    }

    /** Custom weapon attacks may call hurt directly and never reach the Tinkers melee hook. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDirectDamage(LivingDamageEvent event) {
        var source = event.getSource();
        if (event.getAmount() <= 0 || !(source.getEntity() instanceof LivingEntity caster)
                || source.getDirectEntity() != caster
                || !(caster.level() instanceof ServerLevel)
                || !(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) return;
        var tool = ToolHelper.getToolStack(caster.getMainHandItem());
        if (tool != null && !tool.isBroken() && tool.getModifierLevel(ID) > 0) queueCast(caster, event.getEntity());
    }

    private static void queueCast(LivingEntity caster, LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level) || target == null
                || !IntegrationLoader.isIceAndFire() || !eligible(caster, target)) return;
        var state = STATES.computeIfAbsent(level, ignored -> new State());
        if (state.pending.stream().anyMatch(cast -> cast.caster == caster && cast.target == target)) return;
        var branches = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(RADIUS),
                candidate -> candidate != target && eligible(caster, candidate) && target.distanceToSqr(candidate) <= RADIUS * RADIUS);
        branches.sort(Comparator.comparingDouble(target::distanceToSqr));
        state.pending.add(new Cast(caster, target,
                branches.stream().limit(5).toArray(LivingEntity[]::new)));
    }

    private static boolean present(ServerLevel level, LivingEntity entity) {
        return entity.isAlive() && !entity.isRemoved() && entity.level() == level && level.getEntity(entity.getId()) == entity;
    }

    private static void refresh(State state, LivingEntity caster, LivingEntity source, LivingEntity target, long now) {
        var key = new Link(caster.getUUID(), target.getUUID());
        var beam = state.beams.get(key);
        if (beam == null || beam.expires <= now || beam.caster != caster || beam.target != target) {
            if (beam != null) sync(beam, 0);
            beam = new Beam(caster, source, target, now);
            state.beams.put(key, beam);
        } else if (beam.source != source && (source == caster || !present((ServerLevel) caster.level(), beam.source))) {
            sync(beam, 0);
            beam.source = source;
        }
        beam.expires = Math.max(beam.expires, now + DURATION);
        sync(beam, DURATION);
        beam.target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 2), caster);
    }

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var state = STATES.get(level);
        if (state == null) return;
        long now = level.getGameTime();
        var casts = List.copyOf(state.pending);
        state.pending.clear();
        for (var cast : casts) {
            if (!present(level, cast.caster) || !present(level, cast.target)) continue;
            refresh(state, cast.caster, cast.caster, cast.target, now);
            long branchCount = state.beams.values().stream().filter(beam -> beam.caster == cast.caster
                    && beam.source == cast.target && beam.expires > now && present(level, beam.target)
                    && eligible(cast.caster, beam.target)).count();
            for (var branch : cast.branches) {
                if (!present(level, branch) || !eligible(cast.caster, branch)) continue;
                var existing = state.beams.get(new Link(cast.caster.getUUID(), branch.getUUID()));
                boolean linked = existing != null && existing.expires > now && present(level, existing.source)
                        && existing.target == branch;
                if (!linked && branchCount >= 5) continue;
                refresh(state, cast.caster, cast.target, branch, now);
                if (!linked) branchCount++;
            }
        }
        var iterator = state.beams.values().iterator();
        while (iterator.hasNext()) {
            var beam = iterator.next();
            if (beam.expires <= now || !present(level, beam.caster) || !present(level, beam.source)
                    || !present(level, beam.target) || !eligible(beam.caster, beam.target)) {
                sync(beam, 0);
                iterator.remove();
                continue;
            }
            if (now >= beam.pulse) {
                beam.pulse = now + 20;
                var type = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(BEAM_DAMAGE);
                beam.target.hurt(new net.minecraft.world.damagesource.DamageSource(type, beam.source, beam.caster), 2);
            }
            if (now % 10 == 0 && beam.target.isAlive()) {
                beam.target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 2), beam.caster);
            }
            if (now % 20 == 0) sync(beam, (int)(beam.expires - now));
        }
        if (state.beams.isEmpty() && state.pending.isEmpty()) STATES.remove(level);
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }

    private static void sync(Beam beam, int duration) {
        var source = beam.source;
        TinkersNewlife.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                source.getX(), source.getY(), source.getZ(), 64, source.level().dimension())),
                new Sync(source.getId(), beam.target.getId(), duration));
    }

    public record Sync(int source, int target, int duration) {
        public Sync(FriendlyByteBuf buffer) { this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()); }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(source);
            buffer.writeVarInt(target);
            buffer.writeVarInt(duration);
        }
        public void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.mofengbaizhi.tinkersnewlife.client.renderer.WitherGazeRenderer.update(source, target, duration)));
            context.setPacketHandled(true);
        }
    }
}
