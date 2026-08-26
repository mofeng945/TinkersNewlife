package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CharmHandler {

    private static final ModifierId CHARM_ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "charm"));
    private static final ResourceLocation KEY_LAST_CHARM_TIME = new ResourceLocation(TinkersNewlife.MOD_ID, "last_charm_time");

    private static final int CHARM_DURATION_BASE = 10 * 20;
    private static final int CHARM_DURATION_PER_LEVEL = 5 * 20;
    private static final int COOLDOWN = 30 * 20;

    private static final Map<UUID, CharmData> CHARMED_ENTITIES = new ConcurrentHashMap<>();

    private static class CharmData {
        final UUID casterUUID;
        int remainingTicks;

        CharmData(UUID casterUUID, int duration) {
            this.casterUUID = casterUUID;
            this.remainingTicks = duration;
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        // LivingHurtEvent.getEntity() 返回类型即 LivingEntity，无需 instanceof
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        // ⭐ 统一取工具（近战/弹射双路径 + 校验）
        ToolStack tool = ToolHelper.getCombatTool(event.getSource(), player);
        if (tool == null) return;

        int level = tool.getModifierLevel(CHARM_ID);
        if (level <= 0) return;

        applyCharm(player, target, level, tool);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getProjectile().getOwner() instanceof Player player)) return;
        if (target.level().isClientSide) return;

        // ⭐ 统一取工具（弹射路径 + 校验）
        ToolStack tool = ToolHelper.getCombatTool(event.getProjectile(), player);
        if (tool == null) return;

        int level = tool.getModifierLevel(CHARM_ID);
        if (level <= 0) return;

        applyCharm(player, target, level, tool);
    }

    private static void applyCharm(Player player, LivingEntity target, int level, ToolStack tool) {
        UUID targetUUID = target.getUUID();
        CharmData existing = CHARMED_ENTITIES.get(targetUUID);
        if (existing != null && existing.casterUUID.equals(player.getUUID())) return;

        int lastCharmTime = tool.getPersistentData().getInt(KEY_LAST_CHARM_TIME);
        int currentTime = (int) target.level().getGameTime();
        if (currentTime - lastCharmTime < COOLDOWN) return;

        tool.getPersistentData().putInt(KEY_LAST_CHARM_TIME, currentTime);

        int duration = CHARM_DURATION_BASE + (level - 1) * CHARM_DURATION_PER_LEVEL;
        CHARMED_ENTITIES.put(targetUUID, new CharmData(player.getUUID(), duration));

        target.addEffect(new MobEffectInstance(ModEffects.CHARM.get(), duration, 0, false, true, true));
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;
        LivingEntity target = (LivingEntity) event.getEntity();
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();

        CharmData data = CHARMED_ENTITIES.get(attacker.getUUID());
        if (data == null) return;

        if (target.getUUID().equals(data.casterUUID)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        CharmData data = CHARMED_ENTITIES.remove(uuid);
        if (data != null) {
            event.getEntity().removeEffect(ModEffects.CHARM.get());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Map.Entry<UUID, CharmData> entry : CHARMED_ENTITIES.entrySet()) {
            UUID uuid = entry.getKey();
            CharmData data = entry.getValue();

            LivingEntity entity = findEntityByUUID(event, uuid);
            if (entity == null || !entity.isAlive()) {
                CHARMED_ENTITIES.remove(uuid);
                continue;
            }

            if (data.remainingTicks % 5 == 0) {
                spawnHeartParticles(entity);
            }

            data.remainingTicks--;
            if (data.remainingTicks <= 0) {
                CHARMED_ENTITIES.remove(uuid);
                entity.removeEffect(ModEffects.CHARM.get());
            }
        }
    }

    private static LivingEntity findEntityByUUID(TickEvent.ServerTickEvent event, UUID uuid) {
        if (event.getServer() == null) return null;
        // getAllLevels() 返回 Iterable<ServerLevel>，可直接用 ServerLevel.getEntity(uuid)（O(1)）
        for (net.minecraft.server.level.ServerLevel serverLevel : event.getServer().getAllLevels()) {
            var e = serverLevel.getEntity(uuid);
            if (e instanceof LivingEntity living) return living;
        }
        return null;
    }

    private static void spawnHeartParticles(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel)) return;
        ServerLevel serverLevel = (ServerLevel) entity.level();
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.7;
        double z = entity.getZ();

        for (int i = 0; i < 3; i++) {
            double dx = (entity.level().random.nextDouble() - 0.5) * 0.8;
            double dz = (entity.level().random.nextDouble() - 0.5) * 0.8;
            double dy = entity.level().random.nextDouble() * 0.4;
            serverLevel.sendParticles(ParticleTypes.HEART, x + dx, y + dy, z + dz, 0, 0, 0, 0, 0);
        }
    }
}