package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-only execution. The packet handler must still authorize the roster/session and exact reach. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DragonWarOrders {
    private static final int MOVE = 1, ATTACK = 2, BREATH = 3, FOLLOW = 4, CANCEL = 5;
    private static final int STAND = 0, SIT = 1, ESCORT = 2;
    private static final double OWNER_LIMIT_SQ = 128 * 128, ORDER_LIMIT_SQ = 64 * 64;
    private static final Map<ServerLevel, Map<UUID, Order>> ACTIVE = new HashMap<>();
    private static final String DRAGON_BASE = "com.github.alexthe666.iceandfire.entity.EntityDragonBase";

    private DragonWarOrders() {}

    private record Order(UUID owner, int action, @Nullable BlockPos pos, @Nullable UUID target,
                         long expires, int previousMode) {}

    public static boolean apply(ServerPlayer owner, TamableAnimal dragon, int action,
                                @Nullable BlockPos pos, @Nullable LivingEntity target) {
        if (owner == null || dragon == null || action < MOVE || action > CANCEL) return false;
        ServerLevel level = owner.serverLevel();
        if (!level.getServer().isSameThread() || !validDragon(level, owner, dragon)) return false;
        Native api = nativeApi(dragon);
        if (api == null) return false;
        if ((action == MOVE || action == BREATH && target == null) && pos == null) return false;
        if ((action == ATTACK || action == BREATH && target != null) && !validTarget(level, owner, dragon, target)) {
            return false;
        }
        BlockPos aim = action == MOVE || action == BREATH && target == null ? pos.immutable() : null;
        Map<UUID, Order> orders = ACTIVE.get(level);
        try {
            if ((boolean) api.dead.invoke(dragon)) return false;
            if (action == MOVE) {
                if (!usableAim(level, owner, dragon, aim, 2)) return false;
                // The map may point at the solid surface rather than at the block above it.
                var state = level.getBlockState(aim);
                if (!state.getCollisionShape(level, aim).isEmpty()) aim = aim.above();
            }
            if (action <= BREATH && !usableAim(level, owner, dragon,
                    aim != null ? aim : targetAim(target, action), action == BREATH ? 16 : 2)) return false;
            if (orders != null) {
                Order old = orders.remove(dragon.getUUID());
                if (old != null) finish(level, dragon, api, old, false);
            }
            int previousMode = (int) api.getCommand.invoke(dragon);
            reset(dragon, api);
            if (action >= FOLLOW) {
                api.setCommand.invoke(dragon, action == FOLLOW ? ESCORT : STAND);
                return true;
            }
            api.setCommand.invoke(dragon, STAND);
            int duration = action == MOVE ? 240 : action == ATTACK ? 200 : 45;
            Order order = new Order(owner.getUUID(), action, aim,
                    action != MOVE && target != null ? target.getUUID() : null,
                    level.getGameTime() + duration, previousMode);
            ACTIVE.computeIfAbsent(level, ignored -> new HashMap<>()).put(dragon.getUUID(), order);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            TinkersNewlife.LOGGER.warn("Failed to apply Ice and Fire dragon order", e);
            return false;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        Map<UUID, Order> orders = ACTIVE.get(level);
        if (orders == null) return;
        // Work on a snapshot: Forge callbacks from native AI/breath can remove an entity synchronously.
        for (var entry : Map.copyOf(orders).entrySet()) {
            UUID dragonId = entry.getKey();
            Order order = entry.getValue();
            if (orders.get(dragonId) != order) continue;
            if (!(level.getEntity(dragonId) instanceof TamableAnimal dragon)) {
                orders.remove(dragonId);
                continue;
            }
            Native api = nativeApi(dragon);
            if (api == null) {
                orders.remove(dragonId);
                continue;
            }
            try {
                boolean expired = level.getGameTime() >= order.expires;
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(order.owner);
                LivingEntity target = order.target != null && level.getEntity(order.target) instanceof LivingEntity found
                        ? found : null;
                BlockPos aim = order.pos != null ? order.pos : targetAim(target, order.action);
                boolean valid = !expired && validDragon(level, owner, dragon) && !(boolean) api.dead.invoke(dragon)
                        && (int) api.getCommand.invoke(dragon) == STAND
                        && (order.target == null || validTarget(level, owner, dragon, target))
                        && usableAim(level, owner, dragon, aim, order.action == BREATH ? 16 : 2);
                boolean arrived = valid && order.action == MOVE && Vec3.atBottomCenterOf(aim)
                        .distanceToSqr(dragon.position()) <= 4;
                if (!valid || arrived) {
                    if (orders.remove(dragonId, order)) finish(level, dragon, api, order, arrived);
                    continue;
                }
                if (!(boolean) api.canMove.invoke(dragon)) continue; // native sleeping, riding and attack animations
                if (order.action == ATTACK) {
                    if (dragon.getTarget() != target) dragon.setTarget(target);
                } else if (order.action == MOVE) {
                    if (dragon.getTarget() != null) dragon.setTarget(null);
                    Vec3 destination = Vec3.atBottomCenterOf(aim);
                    if ((boolean) api.flying.invoke(dragon) || (boolean) api.hovering.invoke(dragon)) {
                        api.flightTarget.invoke(api.flightManager.get(dragon), destination);
                    } else if (level.getGameTime() % 10 == 0) {
                        dragon.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
                    }
                } else {
                    if (dragon.getTarget() != null) dragon.setTarget(null);
                    Vec3 center = Vec3.atCenterOf(aim);
                    dragon.getLookControl().setLookAt(center.x, center.y, center.z, 30, 30);
                    api.breathAt.invoke(dragon, aim);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (orders.remove(dragonId, order)) finishSafely(level, dragon, api, order);
                TinkersNewlife.LOGGER.warn("Failed to tick Ice and Fire dragon order", e);
            }
        }
        if (orders.isEmpty()) ACTIVE.remove(level, orders);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof TamableAnimal dragon)) return;
        Map<UUID, Order> orders = ACTIVE.get(level);
        if (orders == null) return;
        Order order = orders.remove(dragon.getUUID());
        if (order != null) {
            Native api = nativeApi(dragon);
            if (api != null) finishSafely(level, dragon, api, order);
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) ACTIVE.remove(level);
    }

    private static void finishSafely(ServerLevel level, TamableAnimal dragon, Native api, Order order) {
        try {
            finish(level, dragon, api, order, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            TinkersNewlife.LOGGER.warn("Failed to clear Ice and Fire dragon order", e);
        }
    }

    private static void finish(ServerLevel level, TamableAnimal dragon, Native api, Order order, boolean arrived)
            throws ReflectiveOperationException {
        if (dragon.level() != level || !dragon.isAlive() || !dragon.isTame()
                || !order.owner.equals(dragon.getOwnerUUID()) || (boolean) api.dead.invoke(dragon)) return;
        // Don't override a native staff interaction or take control from a rider.
        if (dragon.isVehicle() || dragon.isPassenger() || (int) api.getCommand.invoke(dragon) != STAND) return;
        LivingEntity current = dragon.getTarget();
        if (order.action == ATTACK && current != null && current.getUUID().equals(order.target)) {
            dragon.setTarget(null);
            api.setBreathing.invoke(dragon, false);
        } else if (order.action == BREATH) {
            api.setBreathing.invoke(dragon, false);
        }
        dragon.getNavigation().stop();
        if (order.action == MOVE) api.flightTarget.invoke(api.flightManager.get(dragon), (Object) null);
        api.setCommand.invoke(dragon, arrived ? SIT : order.previousMode);
    }

    private static void reset(TamableAnimal dragon, Native api) throws ReflectiveOperationException {
        if (dragon.getTarget() != null) dragon.setTarget(null);
        dragon.getNavigation().stop();
        api.flightTarget.invoke(api.flightManager.get(dragon), (Object) null);
        api.setBreathing.invoke(dragon, false);
    }

    private static boolean validDragon(ServerLevel level, @Nullable ServerPlayer owner, TamableAnimal dragon) {
        return owner != null && owner.serverLevel() == level && owner.isAlive() && !owner.isSpectator()
                && level.getServer().getPlayerList().getPlayer(owner.getUUID()) == owner
                && dragon.level() == level && level.getEntity(dragon.getUUID()) == dragon && dragon.isAlive()
                && dragon.isTame() && owner.getUUID().equals(dragon.getOwnerUUID())
                && !dragon.isVehicle() && !dragon.isPassenger()
                && owner.distanceToSqr(dragon) <= OWNER_LIMIT_SQ && level.hasChunkAt(dragon.blockPosition());
    }

    private static boolean validTarget(ServerLevel level, ServerPlayer owner, TamableAnimal dragon,
                                       @Nullable LivingEntity target) {
        return target != null && target != owner && target != dragon && target.level() == level && target.isAlive()
                && level.getEntity(target.getUUID()) == target && !target.isSpectator() && !target.isInvulnerable()
                && dragon.canAttack(target) && !dragon.isAlliedTo(target) && !target.isAlliedTo(dragon)
                && !owner.isAlliedTo(target) && !target.isAlliedTo(owner)
                && (!(target instanceof OwnableEntity pet) || !owner.getUUID().equals(pet.getOwnerUUID()))
                && (!(target instanceof ServerPlayer other) || owner.canHarmPlayer(other));
    }

    @Nullable
    private static BlockPos targetAim(@Nullable LivingEntity target, int action) {
        return target == null ? null : action == BREATH ? BlockPos.containing(target.getEyePosition()) : target.blockPosition();
    }

    private static boolean usableAim(ServerLevel level, ServerPlayer owner, TamableAnimal dragon,
                                     @Nullable BlockPos pos, int padding) {
        if (pos == null || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        Vec3 center = Vec3.atCenterOf(pos);
        if (owner.distanceToSqr(center) > OWNER_LIMIT_SQ || dragon.distanceToSqr(center) > ORDER_LIMIT_SQ) return false;
        BlockPos from = dragon.blockPosition();
        // Probe only existing full chunks. Native breath clips the entire segment and can affect adjacent blocks.
        for (int x = (Math.min(from.getX(), pos.getX()) - padding) >> 4;
             x <= (Math.max(from.getX(), pos.getX()) + padding) >> 4; x++) {
            for (int z = (Math.min(from.getZ(), pos.getZ()) - padding) >> 4;
                 z <= (Math.max(from.getZ(), pos.getZ()) + padding) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    @Nullable
    private static Native nativeApi(TamableAnimal dragon) {
        var id = ForgeRegistries.ENTITY_TYPES.getKey(dragon.getType());
        if (id == null || !id.getNamespace().equals("iceandfire")) return null;
        return NATIVE.get(dragon.getClass());
    }

    private static final ClassValue<Native> NATIVE = new ClassValue<>() {
        @Override protected Native computeValue(Class<?> type) {
            for (Class<?> base = type; base != null; base = base.getSuperclass()) {
                if (!base.getName().equals(DRAGON_BASE)) continue;
                try {
                    Method breath = base.getDeclaredMethod("breathFireAtPos", BlockPos.class);
                    if (!breath.trySetAccessible()) return null;
                    Field manager = base.getField("flightManager");
                    return new Native(base.getMethod("getCommand"), base.getMethod("setCommand", int.class),
                            base.getMethod("canMove"), base.getMethod("isModelDead"), base.getMethod("isFlying"),
                            base.getMethod("isHovering"), base.getMethod("setBreathingFire", boolean.class), breath,
                            manager, manager.getType().getMethod("setFlightTarget", Vec3.class));
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    TinkersNewlife.LOGGER.warn("Unsupported Ice and Fire dragon order API", e);
                    return null;
                }
            }
            return null;
        }
    };

    private record Native(Method getCommand, Method setCommand, Method canMove, Method dead, Method flying,
                          Method hovering, Method setBreathing, Method breathAt, Field flightManager,
                          Method flightTarget) {}
}
