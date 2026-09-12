package com.mofengbaizhi.tinkersnewlife.content.curse.technique;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaseTechnique;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 术式「无下限·无限」：
 * <p>
 * 按下术式释放键（C）开启，再次按下关闭（切换型，开启状态存持久数据，切换术式后仍保持）。
 * 开启期间：
 * - 受到的伤害若不高于阈值则完全无效（<b>不消耗任何咒力</b>）；
 * - 若高于阈值，每点溢出伤害消耗咒力；咒力耗尽自动关闭，并对施术者造成一次破盾伤害（溢出 × 5）；
 * - <b>咒力无限时不破盾</b>：视作咒力充足，溢出部分直接抵消。
 * <p>
 * 阈值 = 10 + 输出等级 × 2 + 亲和 × 0.1
 * （例：输出 5 / 亲和 0 → <b>20</b>，即该配置可以无消耗完全免疫 20 点及以下的伤害）
 * 每点溢出伤害咒力消耗 = max(1, (1 - 输出等级/10) × 10)
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

    /**
     * 伤害阈值 = 10 + 输出等级 × 2 + 亲和 × 0.1（保留 1 位小数）
     * <pre>
     * 输出 1  / 亲和 0   → 12
     * 输出 5  / 亲和 0   → 20     ← 需求点：该配置可无消耗完全免疫 20 点伤害
     * 输出 10 / 亲和 0   → 30
     * 输出 10 / 亲和 200 → 50
     * </pre>
     */
    public static double getThreshold(ServerPlayer player) {
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        double t = 10.0 + output * 2.0 + affinity * 0.1;
        return Math.round(t * 10.0) / 10.0;
    }

    /** 每点溢出伤害消耗的咒力 = (1 - 输出/10) × 10，最低 1 */
    public static double getCursePerOverflow(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        double per = (1.0 - output / 10.0) * 10.0;
        return Math.max(1.0, per);
    }

    /**
     * 开启受理的伤害处理：返回应实际承受的伤害。
     * - 伤害 ≤ 阈值 → 完全抵挡，返回 0（不消耗咒力）；
     * - 伤害 &gt; 阈值 → 溢出伤害，扣咒力；咒力无限 → 视作充足，直接抵消（<b>不破盾</b>）；
     *   咒力不足 → 自动关闭 + 破盾（溢出 × 5，不可抵挡）。
     * 返回 0 表示抵挡，返回原值或其他值表示承受。
     */
    public static float onPlayerDamaged(ServerPlayer player, float amount) {
        if (!isActive(player)) return amount;
        double threshold = getThreshold(player);
        if (amount <= threshold) {
            return 0.0F; // 不高于阈值：完全无效，零消耗
        }
        // 溢出伤害
        double overflow = amount - threshold;
        double cursePerOverflow = getCursePerOverflow(player);
        double curseCost = overflow * cursePerOverflow;

        // ⭐ 咒力无限：视作咒力充足 → 直接抵消溢出，**不再破盾**（原先无限状态必定破盾）
        if (CursePowerHelper.isCurseInfinite(player)) {
            return 0.0F;
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
