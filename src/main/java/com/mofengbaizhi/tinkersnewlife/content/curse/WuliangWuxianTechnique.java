package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 术式「无下限·无限」：
 * <p>
 * 按下术式释放键（C）开启，再次按下关闭（切换型，开启状态存持久数据，切换术式后仍保持）。
 * 开启期间：
 * - 受到的伤害若低于阈值则完全无效；
 * - 若高于阈值，每点溢出伤害消耗咒力；咒力耗尽自动关闭，并对施术者造成一次破盾伤害（溢出 × 5）。
 * <p>
 * 阈值 = (1 + (咒力亲和/10 + 咒力输出等级)/10) × 10 点
 * 每点溢出伤害咒力消耗 = (1 - 咒力输出等级/10) × 10 点
 */
public final class WuliangWuxianTechnique extends BaseTechnique {

    public static final WuliangWuxianTechnique INSTANCE = new WuliangWuxianTechnique();

    /** 玩家持久数据：无限是否开启 */
    public static final String KEY_ACTIVE = "tinkersnewlife.wuxian_active";

    private WuliangWuxianTechnique() {
        super(Modifiers.WULIANG_WUXIAN.getId());
    }

    /** 按下术式键：切换开/关 */
    @Override
    public void onKeyPress(ServerPlayer player) {
        if (isActive(player)) {
            deactivate(player);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wuxian.off"), true);
        } else {
            if (CursePowerHelper.isBurnout(player)) {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                        CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
                return;
            }
            activate(player);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wuxian.on"), true);
        }
    }

    /** 是否开启 */
    public static boolean isActive(ServerPlayer player) {
        return player.getPersistentData().getBoolean(KEY_ACTIVE);
    }

    /** 开启 */
    public static void activate(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_ACTIVE, true);
    }

    /** 关闭 */
    public static void deactivate(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_ACTIVE, false);
    }

    /** 伤害阈值 = (1 + (亲和/10 + 输出)/10) × 10 */
    public static double getThreshold(ServerPlayer player) {
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        return (1.0 + (affinity / 10.0 + output) / 10.0) * 10.0;
    }

    /** 每点溢出伤害消耗的咒力 = (1 - 输出/10) × 10，最低 1 */
    public static double getCursePerOverflow(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        double per = (1.0 - output / 10.0) * 10.0;
        return Math.max(1.0, per);
    }

    /**
     * 开启受理的伤害处理：返回应实际承受的伤害。
     * - 伤害 < 阈值 → 完全抵挡，返回 0；
     * - 伤害 ≥ 阈值 → 溢出伤害，扣咒力；咒力不足则自动关闭 + 破盾（溢出 × 5，不可抵挡）。
     * 返回 0 表示抵挡，返回原值或其他值表示承受。
     */
    public static float onPlayerDamaged(ServerPlayer player, float amount) {
        if (!isActive(player)) return amount;
        double threshold = getThreshold(player);
        if (amount < threshold) {
            return 0.0F; // 低于阈值完全无效
        }
        // 溢出伤害
        double overflow = amount - threshold;
        double cursePerOverflow = getCursePerOverflow(player);
        double curseCost = overflow * cursePerOverflow;
        // 支付：无限模式下自动关闭 + 破盾伤害（此伤害不可被本次无限抵挡，直接返回给玩家）
        if (CursePowerHelper.isCurseInfinite(player)) {
            deactivate(player);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wuxian.shield_break"), true);
            // 破盾伤害 = 溢出 × 5，作为本次实际承受的伤害（一次性高额）
            player.hurt(player.damageSources().magic(), (float) Math.max(1.0, overflow * 5.0));
            return 0.0F; // 原伤害已被无限处理，破盾伤害单独结算
        }
        double curse = CursePowerHelper.getCurse(player);
        if (curse >= curseCost) {
            CursePowerHelper.spendCurse(player, curseCost);
            return 0.0F; // 完全用咒力抵消溢出
        }
        // 咒力不足 → 自动关闭 + 破盾伤害
        deactivate(player);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wuxian.shield_break"), true);
        player.hurt(player.damageSources().magic(), (float) Math.max(1.0, overflow * 5.0));
        return 0.0F;
    }
}
