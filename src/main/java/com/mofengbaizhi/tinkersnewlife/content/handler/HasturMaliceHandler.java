package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ProjectileWeaponHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HasturMaliceHandler {

    private static final ModifierId HASTUR_MALICE = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "hastur_malice")
    );

    // ==================== 风场参数 ====================
    private static final int COOLDOWN_TICKS = 20 * 20; 
    private static final int BASE_RANGE = 5;
    private static final int RANGE_PER_LEVEL = 1;
    private static final float BASE_DAMAGE = 5.0f;
    private static final float DAMAGE_PER_LEVEL = 5.0f;
    private static final int BASE_DURATION_TICKS = 10 * 20;
    private static final int DURATION_PER_LEVEL_TICKS = 5 * 20;
    private static final int DAMAGE_INTERVAL_TICKS = 10;

    // ==================== 状态存储 ====================
    private static final Map<UUID, Long> PLAYER_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, WindField> ACTIVE_FIELDS = new ConcurrentHashMap<>();

    private static class WindField {
        final UUID playerId;
        final Vec3 center;
        final int level;
        final int range;
        final float damage;
        final int maxDuration;
        int tickCount;

        WindField(Player player, Vec3 center, int level) {
            this.playerId = player.getUUID();
            this.center = center;
            this.level = level;
            this.range = BASE_RANGE + (level - 1) * RANGE_PER_LEVEL;
            this.damage = BASE_DAMAGE + (level - 1) * DAMAGE_PER_LEVEL;
            this.maxDuration = BASE_DURATION_TICKS + (level - 1) * DURATION_PER_LEVEL_TICKS;
            this.tickCount = 0;
        }

        boolean isExpired() {
            return tickCount >= maxDuration;
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Player player = null;
        Entity directEntity = event.getSource().getDirectEntity();
        if (directEntity instanceof Player) {
            player = (Player) directEntity;
        } else if (directEntity instanceof Projectile) {
            Entity owner = ((Projectile) directEntity).getOwner();
            if (owner instanceof Player) player = (Player) owner;
        }
        if (player == null) return;
        if (player.level().isClientSide()) return;

        LivingEntity target = event.getEntity();
        if (target == null) return;

        // ✅ 统一使用 ProjectileWeaponHelper 获取武器
        ItemStack stack = ItemStack.EMPTY;
        if (directEntity instanceof Projectile projectile) {
            stack = ProjectileWeaponHelper.getProjectileWeapon(projectile, player);
        } else {
            stack = player.getMainHandItem();
        }

        if (stack.isEmpty()) return;
        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return;

        int level = tool.getModifierLevel(HASTUR_MALICE);
        if (level <= 0) return;

        UUID playerId = player.getUUID();

        // 检查冷却
        long lastUse = PLAYER_COOLDOWNS.getOrDefault(playerId, 0L);
        long currentTick = player.level().getGameTime();
        if (currentTick - lastUse < COOLDOWN_TICKS) {
            return;
        }

        Vec3 center = target.position();
        WindField field = new WindField(player, center, level);
        ACTIVE_FIELDS.put(UUID.randomUUID(), field);
        PLAYER_COOLDOWNS.put(playerId, currentTick);

        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION,
                    center.x, center.y + 0.5, center.z,
                    1, 0, 0, 0, 0);
            server.sendParticles(ParticleTypes.SONIC_BOOM,
                    center.x, center.y + 0.5, center.z,
                    30, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<Map.Entry<UUID, WindField>> it = ACTIVE_FIELDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WindField> entry = it.next();
            WindField field = entry.getValue();

            if (field.isExpired()) {
                Player player = getPlayerById(field.playerId);
                if (player != null && player.level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.CLOUD,
                            field.center.x, field.center.y + 0.5, field.center.z,
                            20, 0.5, 0.5, 0.5, 0.1);
                }
                it.remove();
                continue;
            }

            field.tickCount++;

            if (field.tickCount % DAMAGE_INTERVAL_TICKS == 0) {
                applyWindEffect(field);
            }

            if (field.tickCount % 2 == 0) {
                spawnParticles(field);
            }
        }
    }

    private static void applyWindEffect(WindField field) {
        Player player = getPlayerById(field.playerId);
        if (player == null) return;

        ServerLevel level = (ServerLevel) player.level();
        if (level == null) return;

        Vec3 center = field.center;
        double range = field.range;

        AABB aabb = new AABB(center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, aabb)) {
            if (entity.getUUID().equals(field.playerId)) continue;

            Vec3 toCenter = center.subtract(entity.position());
            double distance = toCenter.length();
            if (distance < 0.1) continue;

            double strength = Math.max(0, 1.0 - distance / range) * 1.5;
            Vec3 pull = toCenter.normalize().scale(strength * 1.0);
            pull = pull.add(0, 0.05, 0);

            entity.setDeltaMovement(entity.getDeltaMovement().add(pull));
            entity.hurtMarked = true;

            entity.hurt(player.damageSources().magic(), field.damage);

            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    3, 0.1, 0.1, 0.1, 0);
        }
    }

    private static void spawnParticles(WindField field) {
        ServerLevel level = getLevelForPlayer(field.playerId);
        if (level == null) return;

        Vec3 center = field.center;
        double range = field.range;

        for (int i = 0; i < 8; i++) {
            double angle = level.random.nextDouble() * 2 * Math.PI;
            double radius = level.random.nextDouble() * range * 0.8;
            double dx = radius * Math.cos(angle);
            double dz = radius * Math.sin(angle);
            double dy = level.random.nextDouble() * 2.0 - 0.5;
            level.sendParticles(ParticleTypes.CLOUD,
                    center.x + dx, center.y + dy + 0.5, center.z + dz,
                    1, 0, 0.05, 0, 0.02);
            level.sendParticles(ParticleTypes.END_ROD,
                    center.x + dx * 0.3, center.y + dy * 0.5 + 0.5, center.z + dz * 0.3,
                    1, 0, 0.1, 0, 0);
        }

        int sparkCount = 6 + field.level;
        for (int i = 0; i < sparkCount; i++) {
            double angle = i * 2 * Math.PI / sparkCount;
            double radiusSpark = field.range * 0.6;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    center.x + radiusSpark * Math.cos(angle), center.y + 0.5, center.z + radiusSpark * Math.sin(angle),
                    2, 0, 0.2, 0, 0.02);
        }
    }

    // ==================== 辅助方法 ====================
    private static Player getPlayerById(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Player player = level.getPlayerByUUID(uuid);
            if (player != null) return player;
        }
        return null;
    }

    private static ServerLevel getLevelForPlayer(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Player player = level.getPlayerByUUID(uuid);
            if (player != null) return level;
        }
        return null;
    }
}