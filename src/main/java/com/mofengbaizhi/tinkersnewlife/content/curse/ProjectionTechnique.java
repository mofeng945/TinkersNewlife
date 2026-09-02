package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.entity.ProjectionPhantomEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * 术式「投射咒法」：
 * 发动时计算玩家 1s 内最大直线移动距离（移动速度 × 20），
 * 在玩家视线方向同水平面生成虚影，距离 = 最大距离的 2/3~1 倍（随机）。
 * 1s 内玩家触碰虚影 → 10s 增益：伤害/跳跃高度/速度 ×2。
 * 每次发动消耗咒力上限的 1/12。
 */
public final class ProjectionTechnique extends BaseTechnique {

    public static final ProjectionTechnique INSTANCE = new ProjectionTechnique();

    /** 增益每层持续 tick（10 秒） */
    public static final int BUFF_TICKS = 200;
    /** 罚站持续 tick（3 秒） */
    public static final int STUN_TICKS = 60;
    /** 增益层数上限：最多 2^10 = 1024 倍，防止伤害指数爆炸 */
    public static final int MAX_BUFF_LEVEL = 10;
    /** 玩家 persistent data 键：增益截止 gameTime */
    public static final String KEY_BUFF_UNTIL = "tinkersnewlife.projection_buff_until";
    /** 增益层数（每次触碰 +1，倍率 ×2^层数） */
    public static final String KEY_BUFF_LEVEL = "tinkersnewlife.projection_buff_level";
    /** 罚站截止 gameTime */
    public static final String KEY_STUN_UNTIL = "tinkersnewlife.projection_stun_until";
    /** 罚站起始位置/视角（锁定用） */
    public static final String KEY_STUN_X = "tinkersnewlife.projection_stun_x";
    public static final String KEY_STUN_Y = "tinkersnewlife.projection_stun_y";
    public static final String KEY_STUN_Z = "tinkersnewlife.projection_stun_z";
    public static final String KEY_STUN_YAW = "tinkersnewlife.projection_stun_yaw";
    public static final String KEY_STUN_PITCH = "tinkersnewlife.projection_stun_pitch";

    private static final Random RANDOM = new Random();

    private ProjectionTechnique() {
        super(Modifiers.PROJECTION.getId());
    }

    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        // 消耗咒力上限的 1/12
        double max = CursePowerHelper.getMaxCurse(player);
        int cost = Math.max(1, (int) Math.ceil(max / 12.0));
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        // 1s 最大直线移动距离 = 移动速度 × 20
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double maxDist = speed * 20.0;
        // 虚影距离：最大距离的 2/3 ~ 1 倍
        double dist = maxDist * (2.0 / 3.0 + RANDOM.nextDouble() / 3.0);
        // 视线水平方向（同水平面，忽略俯仰）
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 pos = player.position().add(flat.scale(dist));
        // 生成虚影（与玩家同 Y）
        ServerLevel level = player.serverLevel();
        ProjectionPhantomEntity phantom = new ProjectionPhantomEntity(ModEntities.PROJECTION_PHANTOM.get(), level);
        phantom.moveTo(pos.x, player.getY(), pos.z, 0, 0);
        phantom.setOwner(player);
        level.addFreshEntity(phantom);
    }

    /** 触碰虚影 → 层数 +1，刷新 10s 增益，解除罚站 */
    public static void applyBuff(ServerPlayer player) {
        var data = player.getPersistentData();
        // 以"当前有效层数"叠加（过期即 0），避免历史残留层数导致一口叠满
        int level = Math.min(MAX_BUFF_LEVEL, getBuffLevel(player) + 1);
        data.putInt(KEY_BUFF_LEVEL, level);
        data.putLong(KEY_BUFF_UNTIL, player.serverLevel().getGameTime() + BUFF_TICKS);
        boolean wasStunned = data.contains(KEY_STUN_UNTIL);
        data.remove(KEY_STUN_UNTIL);
        if (wasStunned) {
            sendStun(player, false);
        }
    }

    /** 虚影超时未触碰 → 罚站 3 秒（锁定位置/视角，无法移动/转视角/攻击/交互） */
    public static void startStun(ServerPlayer player) {
        var data = player.getPersistentData();
        data.putLong(KEY_STUN_UNTIL, player.serverLevel().getGameTime() + STUN_TICKS);
        data.putDouble(KEY_STUN_X, player.getX());
        data.putDouble(KEY_STUN_Y, player.getY());
        data.putDouble(KEY_STUN_Z, player.getZ());
        data.putFloat(KEY_STUN_YAW, player.getYRot());
        data.putFloat(KEY_STUN_PITCH, player.getXRot());
        data.remove(KEY_BUFF_LEVEL);
        data.remove(KEY_BUFF_UNTIL);
        sendStun(player, true);
    }

    /** 罚站结束（到期）时服务端调用，通知客户端解除 */
    public static void endStun(ServerPlayer player) {
        var data = player.getPersistentData();
        if (data.contains(KEY_STUN_UNTIL)) {
            data.remove(KEY_STUN_UNTIL);
            sendStun(player, false);
        }
    }

    /** 向客户端同步罚站状态 */
    private static void sendStun(ServerPlayer player, boolean stunned) {
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.mofengbaizhi.tinkersnewlife.network.PacketProjectionStun(stunned,
                        player.getYRot(), player.getXRot()));
    }

    /** 当前增益层数（无增益为 0）；过期时顺手清掉残留层数，防止下次一口叠满 */
    public static int getBuffLevel(ServerPlayer player) {
        var data = player.getPersistentData();
        if (data.getLong(KEY_BUFF_UNTIL) <= player.serverLevel().getGameTime()) {
            if (data.contains(KEY_BUFF_LEVEL)) data.remove(KEY_BUFF_LEVEL);
            return 0;
        }
        return data.getInt(KEY_BUFF_LEVEL);
    }

    /** 当前倍率 = 2^层数 */
    public static double getBuffMultiplier(ServerPlayer player) {
        return Math.pow(2.0, getBuffLevel(player));
    }

    /** 是否处于增益 */
    public static boolean hasBuff(ServerPlayer player) {
        return getBuffLevel(player) > 0;
    }

    /** 是否处于罚站 */
    public static boolean isStunned(ServerPlayer player) {
        return player.getPersistentData().getLong(KEY_STUN_UNTIL) > player.serverLevel().getGameTime();
    }
}
