package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.entity.JacobLadderEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 术式「雅各布天梯」：
 * <p>
 * 对看向的目标，在其头顶上方 50 格召唤带有七芒星与十字架的大型魔法阵，
 * 2 秒蓄力后降下巨大光柱。被光柱照射的目标：
 * - 60 秒内禁用术式与领域（无下限·无限等持续性咒术瞬间终止）
 * - 持续受到帧伤（2 tick 一次，无视无敌帧），亡灵生物伤害 ×8
 * <p>
 * 法阵半径 = (1+(亲和/10+输出)/10) × 输出 × 5 格
 * 光柱基础帧伤 = (1+(亲和/10+输出)/10) × (输出×8 + 玩家伤害) × 0.1
 * 咒力消耗为「解」的 8 倍。
 */
public final class JacobsLadderTechnique extends BaseTechnique {

    public static final JacobsLadderTechnique INSTANCE = new JacobsLadderTechnique();

    private JacobsLadderTechnique() {
        super(Modifiers.JACOBS_LADDER.getId());
    }

    /** 咒力消耗为「解」的 8 倍 */
    @Override
    protected int getCost(ServerPlayer player) {
        return super.getCost(player) * 8;
    }

    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        // 视线索敌
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        if (!isTargetInRange(player, target)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.too_far"), true);
            return;
        }
        // 消耗咒力（解的 8 倍）
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, getCost(player)) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        // 法阵半径 = (1+(亲和/10+输出)/10) × 输出 × 5
        double radius = (1.0 + (affinity / 10.0 + output) / 10.0) * output * 5.0;
        radius = Math.max(3.0, radius);
        // 光柱基础帧伤 = (1+(亲和/10+输出)/10) × (输出×8 + 玩家伤害) × 0.1
        double playerDmg = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double frame = (1.0 + (affinity / 10.0 + output) / 10.0) * (output * 8.0 + playerDmg) * 0.1;
        frame = amplifyTechniqueDamage(player, frame);

        // 法阵中心 = 目标头顶上方 10 格
        Vec3 pos = new Vec3(target.getX(), target.getY() + 10.0, target.getZ());
        JacobLadderEntity ladder = new JacobLadderEntity(player.serverLevel(), pos,
                player.getUUID(), radius, (float) Math.max(0.5, frame));
        player.serverLevel().addFreshEntity(ladder);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.jacobs_ladder.summon"), true);
    }
}
