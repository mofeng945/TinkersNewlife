package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class HivePrideModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("hive_pride"));
    private static final UUID SPEED = UUID.fromString("73a45e19-a843-4838-acde-058c88d01702");
    private static final int SUPPORT_TICKS = 200;
    private record Order(ServerPlayer player, LivingEntity target, long expires, int reputation) {}
    private static final Map<Mob, Order> ORDERS = new WeakHashMap<>();

    private static int rank(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return 0;
        int total = 0;
        for (var slot : EquipmentSlot.values()) {
            var stack = player.getItemBySlot(slot);
            boolean eligible = slot.getType() == EquipmentSlot.Type.HAND ? stack.is(TinkerTags.Items.SHIELDS)
                    : stack.is(TinkerTags.Items.WORN_ARMOR) && LivingEntity.getEquipmentSlotForItem(stack) == slot;
            if (eligible) total += ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(stack), ID);
        }
        return total;
    }

    public static int effectiveReputation(UUID playerId, int reputation) {
        if (reputation >= 25) return reputation;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return reputation;
        var player = server.getPlayerList().getPlayer(playerId);
        return player != null && rank(player) > 0 ? 25 : reputation;
    }

    private static List<Mob> nearby(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20),
                mob -> mob.isAlive() && mob.distanceToSqr(player) <= 400 && Bridge.isMyrmex(mob));
    }

    @SubscribeEvent public static void checkNearby(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 10 != 0 || !IntegrationLoader.isIceAndFire()) return;
        int rank = rank(player);
        int reputation = 0;
        if (rank >= 3) {
            for (var mob : nearby(player)) reputation = Math.max(reputation, Bridge.reputation(mob, player));
        }
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            double amount = reputation >= 25 ? Math.min(.5, rank * .05) : 0;
            var old = attribute.getModifier(SPEED);
            if (old != null && old.getAmount() != amount) { attribute.removeModifier(SPEED); old = null; }
            if (old == null && amount > 0) attribute.addTransientModifier(new AttributeModifier(SPEED,
                    "Hive Pride", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        // Short leases keep these effects continuous without removing unrelated potion effects.
        if (reputation >= 75) lease(player, MobEffects.DAMAGE_RESISTANCE, Math.min(3, rank) - 1, 20);
        if (reputation == 100) lease(player, MobEffects.REGENERATION, 1, 30);
    }

    private static void lease(LivingEntity entity, MobEffect effect, int amplifier, int duration) {
        var current = entity.getEffect(effect);
        if (current == null || current.getAmplifier() < amplifier ||
                (current.getAmplifier() == amplifier && current.endsWithin(duration - 10))) {
            entity.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
        }
    }

    @SubscribeEvent public static void rally(LivingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel) || event.getAmount() <= 0 || !IntegrationLoader.isIceAndFire()
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == event.getEntity()) return;
        if (event.getEntity() instanceof ServerPlayer defender) order(defender, attacker, 50);
        if (attacker instanceof ServerPlayer commander) order(commander, event.getEntity(), 75);
    }

    private static void order(ServerPlayer player, LivingEntity target, int reputation) {
        int rank = rank(player);
        if (rank < 3 || !target.isAlive() || target.isSpectator() || player.isAlliedTo(target)
                || (target instanceof ServerPlayer other && !player.canHarmPlayer(other))) return;
        long expires = player.level().getGameTime() + SUPPORT_TICKS;
        for (var mob : nearby(player)) {
            if (mob == target || mob.isNoAi() || !mob.canAttack(target) || Bridge.sameHive(mob, target)
                    || Bridge.reputation(mob, player) < reputation) continue;
            ORDERS.put(mob, new Order(player, target, expires, reputation));
            mob.setTarget(target);
            if (reputation == 50 && mob.getTarget() == target) lease(mob, MobEffects.DAMAGE_BOOST,
                    Math.min(5, rank) - 1, SUPPORT_TICKS);
        }
    }

    @SubscribeEvent public static void holdTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        var order = ORDERS.get(mob);
        if (order == null) return;
        if (valid(mob, order)) event.setNewTarget(order.target);
    }

    @SubscribeEvent public static void maintainOrder(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || mob.tickCount % 5 != 0) return;
        var order = ORDERS.get(mob);
        if (order == null) return;
        if (!valid(mob, order)) {
            ORDERS.remove(mob);
            if (mob.getTarget() == order.target) mob.setTarget(null);
        } else if (mob.getTarget() != order.target) {
            mob.setTarget(order.target);
        }
    }

    private static boolean valid(Mob mob, Order order) {
        return mob.isAlive() && order.player.isAlive() && order.target.isAlive() && !order.target.isSpectator()
                && !order.player.isRemoved() && !order.target.isRemoved() && order.player.level() == mob.level()
                && order.target.level() == mob.level() && mob.level().getGameTime() < order.expires
                && rank(order.player) >= 3 && Bridge.reputation(mob, order.player) >= order.reputation;
    }

    private static final class Bridge {
        private static final Class<?> MYRMEX;
        private static final Method HIVE;
        private static final Method REPUTATION;
        private static final Method SAME_HIVE;
        static {
            Class<?> type = null;
            Method hive = null, reputation = null, sameHive = null;
            try {
                type = Class.forName("com.github.alexthe666.iceandfire.entity.EntityMyrmexBase");
                hive = type.getMethod("getHive");
                reputation = hive.getReturnType().getMethod("getPlayerReputation", UUID.class);
                sameHive = type.getMethod("haveSameHive", type, net.minecraft.world.entity.Entity.class);
            } catch (ReflectiveOperationException | LinkageError exception) {
                TinkersNewlife.LOGGER.warn("Could not resolve Ice and Fire Myrmex interfaces", exception);
                type = null;
            }
            MYRMEX = type;
            HIVE = hive;
            REPUTATION = reputation;
            SAME_HIVE = sameHive;
        }
        static boolean isMyrmex(Mob mob) { return MYRMEX != null && MYRMEX.isInstance(mob); }
        static int reputation(Mob mob, ServerPlayer player) {
            try {
                Object hive = HIVE.invoke(mob);
                return hive == null ? 0 : (int) REPUTATION.invoke(hive, player.getUUID());
            } catch (ReflectiveOperationException exception) { return 0; }
        }
        static boolean sameHive(Mob mob, LivingEntity target) {
            try { return (boolean) SAME_HIVE.invoke(null, mob, target); }
            catch (ReflectiveOperationException exception) { return true; }
        }
    }
}
