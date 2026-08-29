package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlameArrowEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「灶·开」：按住蓄力，松开发射
 * <p>
 * - 按下按键：火焰箭出现在玩家身前视野内（视线前方 2 格），悬浮蓄力
 * - 松开按键：消耗咒力并向当前朝向发射（笔直轨迹、速度较慢）
 * - 命中目标或扎在方块上引发火焰爆炸（不破坏方块）：
 *   中心伤害 = (1 + 咒力亲和/100) × (当前攻击伤害 + 咒力输出×10) × 200%，随距离衰减；
 *   并点燃 (1 + 咒力输出) 半径的区域
 * - 咒力消耗为「解」的 10 倍（松开发射时扣除）
 */
public final class ZaoKaiTechnique extends BaseTechnique {

    public static final ZaoKaiTechnique INSTANCE = new ZaoKaiTechnique();

    /** 火焰箭飞行速度（较慢，笔直） */
    private static final float ARROW_SPEED = 1.2F;
    /** 蓄力箭生成位置：玩家视线前方距离 */
    private static final double CHARGE_OFFSET = 2.0;

    /** 蓄力中的火焰箭：玩家 UUID → 蓄力箭 */
    private static final Map<UUID, FlameArrowEntity> CHARGING = new ConcurrentHashMap<>();

    private ZaoKaiTechnique() {
        super(Modifiers.ZAO_KAI.getId());
    }

    /** 咒力消耗为「解」的 10 倍 */
    @Override
    protected int getCost(ServerPlayer player) {
        return super.getCost(player) * 10;
    }

    /** 按下：在身前视野内生成蓄力箭（无需指向目标） */
    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CHARGING.containsKey(player.getUUID())) return; // 已在蓄力
        FlameArrowEntity arrow = new FlameArrowEntity(player.level(), player);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        arrow.setPos(eye.x + look.x * CHARGE_OFFSET, eye.y + look.y * CHARGE_OFFSET, eye.z + look.z * CHARGE_OFFSET);
        player.level().addFreshEntity(arrow);
        CHARGING.put(player.getUUID(), arrow);
    }

    /** 松开：扣除咒力并向当前朝向发射（不足则取消发射） */
    @Override
    public void onKeyRelease(ServerPlayer player) {
        FlameArrowEntity arrow = CHARGING.remove(player.getUUID());
        if (arrow == null || !arrow.isAlive()) return;

        // 松开发射时扣除（解 ×10），不足则取消
        if (!payCost(player)) {
            arrow.discard();
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        // 向松开瞬间的朝向发射：笔直、慢速
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, ARROW_SPEED, 0.0F);
    }

    /** 取消蓄力（登出/死亡时）：销毁蓄力箭并清记录 */
    public static void cancelCharge(ServerPlayer player) {
        FlameArrowEntity arrow = CHARGING.remove(player.getUUID());
        if (arrow != null && arrow.isAlive()) {
            arrow.discard();
        }
    }
}
