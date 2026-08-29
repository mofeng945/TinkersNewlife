package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静止（无量空处）处理（服务端）
 * <p>
 * - 每 tick 冻结携带者：玩家不可移动/跳跃/冲刺，打开的界面被立即关闭；
 *   生物强制 noAi（AI 停摆、不移动不攻击）
 * - 静止玩家无法攻击（AttackEntityEvent）、无法使用物品/打开容器（交互事件取消）
 * - 生物 noAi 在静止结束后恢复原值
 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StunHandler {

    /** 被静止过的生物：UUID → 静止前的 noAi 值（用于恢复） */
    private static final Map<UUID, Boolean> STUNNED_MOB_NOAI = new ConcurrentHashMap<>();

    public static boolean isStunned(LivingEntity entity) {
        return entity != null && entity.hasEffect(ModEffects.STUN.get());
    }

    /** 记录生物静止前的 noAi 状态（领域施加静止时调用） */
    public static void onStunApplied(Mob mob) {
        STUNNED_MOB_NOAI.putIfAbsent(mob.getUUID(), mob.isNoAi());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        // 玩家：定身 = 无法自主移动（输入清零），但仍受物理引擎/击退/碰撞挤压影响
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isStunned(player)) continue;
            player.xxa = 0;
            player.zza = 0;
            player.setJumping(false);
            player.setSprinting(false);
            // 无法停留在任何界面（背包/箱子/curios 等，重开也会被立即关闭）
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
        }

        // 生物：静止期间强制 noAi（不自主移动/攻击），物理/击退/碰撞仍生效；结束后恢复
        for (UUID id : STUNNED_MOB_NOAI.keySet()) {
            Entity entity = findEntity(server, id);
            if (!(entity instanceof Mob mob)) {
                STUNNED_MOB_NOAI.remove(id);
                continue;
            }
            if (mob.hasEffect(ModEffects.STUN.get())) {
                mob.setNoAi(true);
                mob.getNavigation().stop();
                // ⭐ 保证被定身也能正常下落（不会被固定在空中）：
                // 未落地且未在下落时施加向下加速度，其余情况交给物理引擎
                if (!mob.onGround() && !mob.isInWater() && !mob.isNoGravity()) {
                    Vec3 v = mob.getDeltaMovement();
                    if (v.y >= 0) {
                        mob.setDeltaMovement(v.add(0, -0.08, 0));
                    }
                }
            } else {
                mob.setNoAi(STUNNED_MOB_NOAI.get(id));
                STUNNED_MOB_NOAI.remove(id);
            }
        }
    }

    /** 静止玩家无法攻击 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 静止玩家无法使用物品（右键） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 静止玩家无法打开容器/交互方块（右键方块） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }
}
