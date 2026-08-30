package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.entity.BloodNovaEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 术式「赤血操术·超新星」：血之极致，聚为星爆。
 * <p>
 * 指向目标（视线索敌），在目标位置生成一颗微小血红色圆球（直径约 0.2 格），
 * 0.8 秒延迟后爆炸（不破坏方块）：
 * <ul>
 *   <li>中心伤害 = (1 + 咒力亲和/100) × 咒力输出 × 消耗血量 × 10，随距离线性衰减</li>
 *   <li>每次消耗 10% 最大生命值（创造模式不消耗，伤害仍按理论消耗计算）</li>
 *   <li>咒力消耗 = 「解」 × 5/2</li>
 *   <li>无视无敌帧，材料战斗特性每个受击目标照常触发</li>
 * </ul>
 */
public final class BloodManipulationSupernovaTechnique extends BaseTechnique {

    public static final BloodManipulationSupernovaTechnique INSTANCE = new BloodManipulationSupernovaTechnique();

    /** 每次发动消耗最大生命值的比例（10%） */
    public static final double BLOOD_COST_RATIO = 0.10;
    /** 咒力消耗 = 解 × 5/2 */
    public static final double COST_MULTIPLIER = 5.0 / 2.0;
    /** 爆炸半径（格） */
    public static final double EXPLOSION_RADIUS = 3.0;

    private BloodManipulationSupernovaTechnique() {
        super(Modifiers.BLOOD_MANIPULATION_SUPERNOVA.getId());
    }

    /** 咒力消耗 = 「解」 × 5/2（最低 1 点） */
    @Override
    protected int getCost(ServerPlayer player) {
        return Math.max(1, (int) Math.ceil(super.getCost(player) * COST_MULTIPLIER));
    }

    /**
     * 超新星流程：熔断 → 咒力 → 索敌 → 血量（10%） → 目标位置生成血球。
     * 未命中目标时不消耗血量（咒力已扣，与解/百敛一致）。
     */
    @Override
    public boolean tryUse(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return false;
        }
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return false;
        }
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return false;
        }
        // 血量检查：当前血量必须严格大于 10% 消耗，避免术式致死
        double bloodCost = player.getMaxHealth() * BLOOD_COST_RATIO;
        if (!player.isCreative() && player.getHealth() <= (float) bloodCost) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_blood"), true);
            return false;
        }
        if (!player.isCreative()) {
            player.setHealth(player.getHealth() - (float) bloodCost);
        }
        spawnNova(player, target, bloodCost);
        return true;
    }

    /** 在目标中心生成血球：中心伤害 = (1 + 亲和/100) × 咒力输出 × 消耗血量 × 10，含魔杖增幅 */
    private void spawnNova(ServerPlayer player, LivingEntity target, double bloodCost) {
        Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double centerDamage = (1.0 + affinity / 100.0) * output * bloodCost * 10.0;
        centerDamage = amplifyTechniqueDamage(player, centerDamage);
        player.serverLevel().addFreshEntity(new BloodNovaEntity(
                player.serverLevel(), pos, player.getUUID(),
                (float) centerDamage, (float) EXPLOSION_RADIUS));
    }
}
