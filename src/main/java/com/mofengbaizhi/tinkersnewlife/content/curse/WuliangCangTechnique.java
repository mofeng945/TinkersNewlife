package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.entity.CursedOrbEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「无下限·苍」（含术式反转）：
 * <p>
 * - 按 C（术式键）：开始苍蓄力；再次按 C 朝前方发射天蓝色苍球——
 *   苍球把 10 格内实体拉向球心（不含施术者），实体接触球心则爆炸
 * - 按 F（术式反转键）：开始赫蓄力；再次按 F 发射亮红赫球——
 *   赫球把实体推开，被推开实体撞到方块时按速度受伤，实体接触球心爆炸（苍的 2 倍）
 * - 苍蓄力期间按 F：两者结合为「虚式·茈」直接向前发射——
 *   茈球破坏所有撞到的方块（基岩除外，不掉落），击中实体造成苍 10 倍爆炸伤害
 * <p>
 * 三者最大飞行距离 40 格；苍/赫撞到方块消失。发射消耗咒力（茈消耗更高）。
 */
public final class WuliangCangTechnique extends BaseTechnique {

    public static final WuliangCangTechnique INSTANCE = new WuliangCangTechnique();

    /** 蓄力状态：0=无 1=苍蓄力中 2=赫蓄力中 */
    private static final Map<UUID, Integer> CHARGING = new ConcurrentHashMap<>();

    private WuliangCangTechnique() {
        super(Modifiers.WULIANG_CANG.getId());
    }

    /** 当前是否在蓄力 */
    public static int getChargeState(ServerPlayer player) {
        return CHARGING.getOrDefault(player.getUUID(), 0);
    }

    /** 取消蓄力（登出/死亡时） */
    public static void cancelCharge(ServerPlayer player) {
        CHARGING.remove(player.getUUID());
    }

    // ============================================================
    //  苍（术式键 C）
    // ============================================================

    @Override
    public void onKeyPress(ServerPlayer player) {
        int state = getChargeState(player);
        if (state == 0) {
            // 开始苍蓄力
            CHARGING.put(player.getUUID(), 1);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.cang.charge"), true);
        } else if (state == 1) {
            // 再次按下 → 发射苍球
            CHARGING.remove(player.getUUID());
            fire(player, CursedOrbEntity.TYPE_CANG, 1.0);
        } else {
            // 赫蓄力中按 C → 发射赫球
            CHARGING.remove(player.getUUID());
            fire(player, CursedOrbEntity.TYPE_HE, 1.0);
        }
    }

    // ============================================================
    //  赫（术式反转键 F）
    // ============================================================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        int state = getChargeState(player);
        if (state == 1) {
            // 苍蓄力中按 F → 结合为茈，直接向前发射
            CHARGING.remove(player.getUUID());
            fire(player, CursedOrbEntity.TYPE_ZI, 3.0);
        } else if (state == 0) {
            // 开始赫蓄力
            CHARGING.put(player.getUUID(), 2);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.he.charge"), true);
        } else {
            // 赫蓄力中再次按 F → 发射赫球
            CHARGING.remove(player.getUUID());
            fire(player, CursedOrbEntity.TYPE_HE, 1.0);
        }
    }

    // ============================================================
    //  发射
    // ============================================================

    /**
     * 发射球体：位置 = 施术者眼前，朝当前视线方向。
     * costMultiplier：咒力消耗倍率（苍/赫 1 倍，茈 3 倍）。
     */
    private void fire(ServerPlayer player, int type, double costMultiplier) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        int cost = (int) Math.ceil(getCost(player) * costMultiplier);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        // 苍基准伤害 = (1 + 亲和/100) × (10 + 输出×5)，含模块化魔杖增幅
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        double base = (1.0 + affinity / 100.0) * (10.0 + output * 5.0);
        base = amplifyTechniqueDamage(player, base);

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 pos = eye.add(look.scale(0.8));

        CursedOrbEntity orb = new CursedOrbEntity(player.serverLevel(), pos, player.getUUID(), type, (float) base, look);
        player.serverLevel().addFreshEntity(orb);
        player.displayClientMessage(Component.translatable(
                type == CursedOrbEntity.TYPE_CANG ? "message.tinkersnewlife.cang.fire"
                        : type == CursedOrbEntity.TYPE_HE ? "message.tinkersnewlife.he.fire"
                        : "message.tinkersnewlife.zi.fire"), true);
    }

    /** 蓄力粒子（服务端每 tick 调用）：苍=蓝、赫=红 环绕施术者 */
    public static void tickChargeParticles(ServerLevel level, ServerPlayer player) {
        int state = getChargeState(player);
        if (state == 0) return;
        Vector3f col = state == 1 ? new Vector3f(0.4F, 0.7F, 1.0F) : new Vector3f(1.0F, 0.2F, 0.15F);
        for (int i = 0; i < 3; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = 0.5;
            level.sendParticles(new DustParticleOptions(col, 1.0F),
                    player.getX() + Math.cos(angle) * r, player.getY() + 1.2, player.getZ() + Math.sin(angle) * r,
                    1, 0.02, 0.02, 0.02, 0);
        }
    }
}
