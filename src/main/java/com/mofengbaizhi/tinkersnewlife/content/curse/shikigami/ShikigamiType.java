package com.mofengbaizhi.tinkersnewlife.content.curse.shikigami;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

/**
 * 十种影法术 式神类型
 * <p>
 * 每种式神的基础属性（生命/伤害/速度/体型）与咒力消耗倍率。
 * 实际数值还会受施术者的「咒力亲和」与「咒力输出」缩放：
 * <ul>
 *   <li>属性缩放 = (1 + 亲和/100) × 输出（生命与伤害）</li>
 *   <li>体型缩放 = (1 + 亲和/100) × 输出</li>
 *   <li>速度缩放 = (1 + 亲和/100) × 输出</li>
 * </ul>
 * 咒力消耗 = 「解」的消耗 × 消耗倍率（只在召唤时扣除，维持不耗咒力）。
 */
public enum ShikigamiType {

    DOG        ("dog",          "玉犬",       0.9,  30,  6, 0.45, 1.0F),
    NUE        ("nue",          "鵺",         0.8,  25,  8, 0.60, 2.5F),
    SERPENT    ("serpent",      "蚀蠹",       1.4,  55,  9, 0.38, 3.0F),
    TOAD       ("toad",         "蛤蟆",       1.3,  60,  7, 0.32, 2.0F),
    ELEPHANT   ("elephant",     "川豚",       2.2,  90, 12, 0.30, 6.0F),
    RABBIT     ("rabbit_escape","脱兔",       0.5,  10,  1, 0.55, 1.5F),
    DEER       ("deer",         "愈羊",       1.1,  40,  4, 0.45, 2.0F),
    OX         ("ox",           "贯牛",       1.6,  70, 10, 0.55, 3.0F),
    TIGER      ("tiger",        "怒角",       1.8,  80, 16, 0.42, 4.0F),
    MAHORAGA   ("mahoraga",     "魔虚罗",     2.0, 150, 20, 0.45, 10.0F);

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

    /** 本式神对应的实体注册类型（用于选择界面 3D 预览/客户端展示） */
    public EntityType<?> entityType() {
        return switch (this) {
            case DOG -> ModEntities.SHIKIGAMI_WOLF.get();
            case NUE -> ModEntities.SHIKIGAMI_PHANTOM.get();
            case SERPENT -> ModEntities.SHIKIGAMI_SILVERFISH.get();
            case TOAD -> ModEntities.SHIKIGAMI_FROG.get();
            case ELEPHANT -> ModEntities.SHIKIGAMI_PIG.get();
            case RABBIT -> ModEntities.SHIKIGAMI_RABBIT.get();
            case DEER -> ModEntities.SHIKIGAMI_GOAT.get();
            case OX -> ModEntities.SHIKIGAMI_COW.get();
            case TIGER -> ModEntities.SHIKIGAMI_SHEEP.get();
            case MAHORAGA -> ModEntities.SHIKIGAMI_IRON_GOLEM.get();
        };
    }

    // ============================================================
    //  缩放与数值
    // ============================================================

    /** 属性（生命/伤害）缩放 = (1 + 亲和/100) × 输出 */
    public static double statScale(ServerPlayer player) {
        return statScale(CursePowerHelper.getCurseAffinity(player), CursePowerHelper.getCurseOutputLevel(player));
    }

    /** 体型缩放 = (1 + 亲和/100) × 输出 */
    public static double sizeScale(ServerPlayer player) {
        return sizeScale(CursePowerHelper.getCurseAffinity(player), CursePowerHelper.getCurseOutputLevel(player));
    }

    /** 速度缩放 = (1 + 亲和/100) × 输出（上限 4 倍） */
    public static double speedScale(ServerPlayer player) {
        return speedScale(CursePowerHelper.getCurseAffinity(player), CursePowerHelper.getCurseOutputLevel(player));
    }

    /** 属性（生命/伤害）缩放 = (1 + 亲和/100) × 输出（客户端可用，无需玩家实体） */
    public static double statScale(int affinity, int output) {
        return (1.0 + affinity / 100.0) * Math.max(1, output);
    }

    /** 体型缩放 = (1 + 亲和/100) × 输出 */
    public static double sizeScale(int affinity, int output) {
        return (1.0 + affinity / 100.0) * Math.max(1, output);
    }

    /** 速度缩放上限（增幅不超过 4 倍，避免式神跑太快跑丢） */
    public static final double MAX_SPEED_SCALE = 4.0;

    /** 速度缩放 = (1 + 亲和/100) × 输出，上限 4 倍（客户端可用，无需玩家实体） */
    public static double speedScale(int affinity, int output) {
        return Math.min(MAX_SPEED_SCALE,
                (1.0 + affinity / 100.0) * Math.max(1, output));
    }

    /** 本类型缩放后的最大生命 */
    public double scaledHp(ServerPlayer player) {
        return Math.max(1, baseHp * statScale(player));
    }

    /** 本类型缩放后的攻击伤害 */
    public double scaledDamage(ServerPlayer player) {
        return Math.max(1, baseDamage * statScale(player));
    }

    /** 本类型缩放后的移速。
     * <p>实际移动 ∝ st.speed²（MoveControl: setSpeed = 导航速度参数 × MOVEMENT_SPEED 属性，
     * 式神把 st.speed 同时用作两者），因此把 st.speed 限制在 √(2×玩家疾跑速度当量) 以内，
     * 使式神实际速度不超过玩家疾跑速度的 2 倍，避免跑太快跑丢/玩家追不上。 */
    public double scaledSpeed(ServerPlayer player) {
        double raw = baseSpeed * speedScale(player);
        double playerSpeed = player.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        double cap = Math.sqrt(2.0 * playerSpeed * 1.3);
        return Math.max(0.05, Math.min(raw, cap));
    }

    /** 本类型缩放后的渲染体型 */
    public double scaledSize(ServerPlayer player) {
        return Math.max(0.2, baseScale * sizeScale(player));
    }

    /** 咒力消耗 = 「解」的消耗 × 倍率（最低 1 点） */
    public int summonCost(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return summonCost(affinity, output);
    }

    /** 咒力消耗（客户端可用）：消耗 = ceil((1 - 亲和/100) × (10 + 输出×5) × 倍率)，最低 1 */
    public int summonCost(int affinity, int output) {
        double base = (1.0 - affinity / 100.0) * (10 + output * 5);
        return Math.max(1, (int) Math.ceil(base * costMultiplier));
    }
}
