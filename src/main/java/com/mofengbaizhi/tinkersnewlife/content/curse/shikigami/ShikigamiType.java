package com.mofengbaizhi.tinkersnewlife.content.curse.shikigami;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * 十种影法术 式神类型
 * <p>
 * 每种式神的基础属性（生命/伤害/速度/体型）与咒力消耗倍率。
 * 实际数值还会受施术者的「咒力亲和」与「咒力输出」缩放：
 * <ul>
 *   <li>属性缩放 = 1 + 亲和/100×0.5 + 输出×0.08（生命与伤害）</li>
 *   <li>体型缩放 = 1 + 亲和/100×0.4 + 输出×0.04</li>
 *   <li>速度缩放 = 1 + 亲和/100×0.3 + 输出×0.03</li>
 * </ul>
 * 咒力消耗 = 「解」的消耗 × 消耗倍率（只在召唤时扣除，维持不耗咒力）。
 */
public enum ShikigamiType {

    DOG        ("dog",          "玉犬",       0.9,  30,  6, 0.30, 1.0F),
    NUE        ("nue",          "鵺",         0.8,  25,  8, 0.35, 2.5F),
    SERPENT    ("serpent",      "大蛇",       1.4,  55,  9, 0.25, 3.0F),
    TOAD       ("toad",         "蛤蟆",       1.3,  60,  7, 0.20, 2.0F),
    ELEPHANT   ("elephant",     "满象",       2.2,  90, 12, 0.20, 6.0F),
    RABBIT     ("rabbit_escape","脱兔",       0.5,  10,  1, 0.40, 1.5F),
    DEER       ("deer",         "圆鹿",       1.1,  40,  4, 0.30, 2.0F),
    OX         ("ox",           "贯牛",       1.6,  70, 10, 0.35, 3.0F),
    TIGER      ("tiger",        "虎葬",       1.8,  80, 16, 0.28, 4.0F),
    MAHORAGA   ("mahoraga",     "魔虚罗",     2.0, 150, 20, 0.30, 10.0F);

    /** 注册 id（实体/修饰符路径用） */
    public final String id;
    /** 显示名（本地化键 modifier.tinkersnewlife.ten_shadows.<id>） */
    public final String name;
    /** 基础体型（乘体型缩放后作为渲染缩放） */
    public final double baseScale;
    /** 基础生命 */
    public final double baseHp;
    /** 基础攻击伤害 */
    public final double baseDamage;
    /** 基础移速 */
    public final double baseSpeed;
    /** 咒力消耗倍率（相对「解」） */
    public final float costMultiplier;

    ShikigamiType(String id, String name, double baseScale, double baseHp, double baseDamage,
                  double baseSpeed, float costMultiplier) {
        this.id = id;
        this.name = name;
        this.baseScale = baseScale;
        this.baseHp = baseHp;
        this.baseDamage = baseDamage;
        this.baseSpeed = baseSpeed;
        this.costMultiplier = costMultiplier;
    }

    public String getLangKey() {
        return "modifier.tinkersnewlife.ten_shadows." + id;
    }

    // ============================================================
    //  缩放与数值
    // ============================================================

    /** 属性（生命/伤害）缩放 = 1 + 亲和/100×0.5 + 输出×0.08 */
    public static double statScale(ServerPlayer player) {
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        return 1.0 + affinity / 100.0 * 0.5 + output * 0.08;
    }

    /** 体型缩放 = 1 + 亲和/100×0.4 + 输出×0.04 */
    public static double sizeScale(ServerPlayer player) {
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        return 1.0 + affinity / 100.0 * 0.4 + output * 0.04;
    }

    /** 速度缩放 = 1 + 亲和/100×0.3 + 输出×0.03 */
    public static double speedScale(ServerPlayer player) {
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        return 1.0 + affinity / 100.0 * 0.3 + output * 0.03;
    }

    /** 本类型缩放后的最大生命 */
    public double scaledHp(ServerPlayer player) {
        return Math.max(1, baseHp * statScale(player));
    }

    /** 本类型缩放后的攻击伤害 */
    public double scaledDamage(ServerPlayer player) {
        return Math.max(1, baseDamage * statScale(player));
    }

    /** 本类型缩放后的移速 */
    public double scaledSpeed(ServerPlayer player) {
        return Math.max(0.05, baseSpeed * speedScale(player));
    }

    /** 本类型缩放后的渲染体型 */
    public double scaledSize(ServerPlayer player) {
        return Math.max(0.2, baseScale * sizeScale(player));
    }

    /** 咒力消耗 = 「解」的消耗 × 倍率（最低 1 点） */
    public int summonCost(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double base = (1.0 - affinity / 100.0) * (10 + output * 5);
        return Math.max(1, (int) Math.ceil(base * costMultiplier));
    }
}
