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

    /** 增益持续 tick（10 秒） */
    public static final int BUFF_TICKS = 200;
    /** 玩家 persistent data 键：增益截止 gameTime */
    public static final String KEY_BUFF_UNTIL = "tinkersnewlife.projection_buff_until";

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
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.projection.summon",
                String.format("%.1f", dist)), true);
    }

    /** 触碰虚影 → 触发 10s 增益 */
    public static void applyBuff(ServerPlayer player) {
        player.getPersistentData().putLong(KEY_BUFF_UNTIL,
                player.serverLevel().getGameTime() + BUFF_TICKS);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.projection.buff"), true);
    }

    /** 玩家当前是否处于投射增益中 */
    public static boolean hasBuff(ServerPlayer player) {
        long until = player.getPersistentData().getLong(KEY_BUFF_UNTIL);
        return until > player.serverLevel().getGameTime();
    }
}
