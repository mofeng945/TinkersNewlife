package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
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

import java.lang.reflect.Method;
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

    // ==================== 辅助方法：获取弹射物对应的武器 ====================
    private static ItemStack getProjectileWeapon(Projectile projectile, Player shooter) {
        // 1. 尝试从弹射物本身获取物品（标枪、三叉戟、匠魂标枪等）
        try {
            // 三叉戟、匠魂标枪等可能有 getPickupItem() 方法
            Method method = projectile.getClass().getMethod("getPickupItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}
        try {
            // 匠魂标枪可能通过 getItem() 获取
            Method method = projectile.getClass().getMethod("getItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}

        // 2. 若弹射物无物品，则从玩家主手获取（弓/弩）
        ItemStack mainHand = shooter.getMainHandItem();
        if (!mainHand.isEmpty()) {
            return mainHand;
        }
        // 3. 若主手为空，尝试副手
        ItemStack offHand = shooter.getOffhandItem();
        if (!offHand.isEmpty()) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    // ==================== 近战触发 ====================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        Player player = (Player) event.getSource().getEntity();
        LivingEntity target = (LivingEntity) event.getEntity();
        if (target.level().isClientSide) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;
        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;

        int level = tool.getModifierLevel(CHARM_ID);
        if (level <= 0) return;

        applyCharm(player, target, level, tool);
    }

    // ==================== 弹射物触发（已修正） ====================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult)) return;
        EntityHitResult entityHit = (EntityHitResult) event.getRayTraceResult();
        if (!(entityHit.getEntity() instanceof LivingEntity)) return;
        if (!(event.getProjectile().getOwner() instanceof Player)) return;
        LivingEntity target = (LivingEntity) entityHit.getEntity();
        Player player = (Player) event.getProjectile().getOwner();
        if (target.level().isClientSide) return;

        // ★ 获取弹射物对应的武器（标枪本体 或 弓/弩）
        ItemStack stack = getProjectileWeapon(event.getProjectile(), player);
        if (stack.isEmpty()) return;
        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;

        int level = tool.getModifierLevel(CHARM_ID);
        if (level <= 0) return;

        applyCharm(player, target, level, tool);
    }

    // ==================== 通用逻辑 ====================
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

    // ==================== 拦截攻击 ====================
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

    // ==================== 清理与粒子 ====================
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
        for (var level : event.getServer().getAllLevels()) {
            var e = level.getEntity(uuid);
            if (e instanceof LivingEntity) return (LivingEntity) e;
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