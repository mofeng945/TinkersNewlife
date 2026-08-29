package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlameArrowEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 术式「灶·开」：向看向目标方向发射一道笔直轨迹、速度较慢的火焰箭
 * <p>
 * 命中目标或扎在方块上引发火焰爆炸（不破坏方块）：
 * 中心伤害 = (1 + 咒力亲和/100) × (当前攻击伤害 + 咒力输出×10) × 200%，随距离衰减，命中者被点燃。
 * 咒力消耗为「解」的 10 倍。
 */
public final class ZaoKaiTechnique extends BaseTechnique {

    public static final ZaoKaiTechnique INSTANCE = new ZaoKaiTechnique();

    /** 火焰箭飞行速度（较慢，笔直） */
    private static final float ARROW_SPEED = 1.2F;

    private ZaoKaiTechnique() {
        super(Modifiers.ZAO_KAI.getId());
    }

    /** 咒力消耗为「解」的 10 倍 */
    @Override
    protected int getCost(ServerPlayer player) {
        return super.getCost(player) * 10;
    }

    @Override
    protected void onCast(ServerPlayer player, LivingEntity target) {
        // 沿视线方向发射：无重力 → 笔直轨迹，慢速 + 零误差
        FlameArrowEntity arrow = new FlameArrowEntity(player.level(), player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, ARROW_SPEED, 0.0F);
        player.level().addFreshEntity(arrow);
    }
}
