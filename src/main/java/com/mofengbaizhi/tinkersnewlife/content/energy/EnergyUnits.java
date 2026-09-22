package com.mofengbaizhi.tinkersnewlife.content.energy;

/**
 * <b>全模组唯一的能量换算表</b>（用户拍板口径 ✓ 2026-09-21）。
 *
 * <h2>基准单位：1 EE（晶能 / Elder Energy）</h2>
 * <pre>
 *   1 EE = 10 点 Iron's Spellbooks 魔力（法力）   ← §524 起 ×10
 *        = 40 点 Goety（诡厄巫法）灵魂能量
 *        = 20 点本模组咒力
 * </pre>
 * 也就是说：<b>法力是 1:1 的基准</b>，灵魂是它的 4 倍、咒力是它的 2 倍。
 *
 * <h2>为什么要有这一个类</h2>
 * 以前"魔力 / 灵魂 / 咒力"三系之间的汇率散落在
 * {@code AllPathsOneTrait.SOULS_PER_MANA / SOULS_PER_CURSE}、
 * {@code CursePowerHelper#payCurseWithSoulFallback} 里的字面量 {@code * 3.0}
 * 以及若干注释里 ✗ —— 改一处就会三处不一致 ✗。
 * 现在<b>所有</b>跨体系换算都必须走这里 ✓（新写的代码请勿再写魔法数字 ✗）。
 *
 * <h2>⚠ 语义边界（有意<b>不</b>统一的换算）</h2>
 * <ul>
 *   <li>{@code CurseBottleHelper.POWER_PER_MB} / {@code CurseVaultData.POWER_PER_MB}
 *       （1 mB 咒力残秽 = 10 咒力）：那是<b>我们自己的流体 ↔ 咒力</b>的定价 ✓
 *       不是"魔力 / 灵魂 / 咒力"之间的汇率 ✗ ⇒ <b>不</b>并入本表（并入了反而自相矛盾）。</li>
 *   <li>{@code 1 结界碎片 = 25 咒力}（{@code CursePowerHelper}）：同上，是物品 ↔ 咒力的定价。</li>
 *   <li>术式 / 领域 / 拟造的咒力价格（{@code CURSE_PER_TICK}、{@code ConstructTechnique} 的造价）：
 *       它们是"要花多少咒力"的设计数值，不是跨体系汇率 ✗。</li>
 * </ul>
 *
 * <h2>§557 新增：EE ⇄ 外部能量（FE / J / EU / AE / RPM）</h2>
 * 见 {@link #FE_PER_EE_FACTOR} 与 {@link Fe} 那一节 ✓ ——
 * <b>换算常量全部集中在本类</b> ✓，"万用能量转化器"与以后的任何使用者都从这里取 ✓
 * 绝不在方块代码里写魔法数字 ✗。
 */
public final class EnergyUnits {

    private EnergyUnits() {}

    // ============================================================
    //  基准常量（改这里 = 全局改口径 ✓）
    // ============================================================

    /**
     * 1 EE 折合多少 Iron's 魔力（法力）。
     * <p>§524 用户口径：「让 EE 的转化倍率变成现在的 10 倍」⇒ 三个基准常量**同时 ×10** ✓
     * （三者同乘 ⇒ **三系之间的相对汇率不变** ✓：灵魂:法力 仍 4:1、咒力:法力 仍 2:1 ✓
     *  只是"1 EE"变值钱了 10 倍 ⇒ 一颗 1000 EE 的水晶现在等于 **10,000 魔力** ✓）。
     */
    public static final double MANA_PER_EE = 10.0D;

    /** 1 EE 折合多少 Goety 灵魂能量 —— 用户口径：1:4 */
    public static final double SOULS_PER_EE = 40.0D;   // §524 ×10（相对汇率不变 ✓）

    /** 1 EE 折合多少本模组咒力 —— 用户口径：1:2 */
    public static final double CURSE_PER_EE = 20.0D;  // §524 ×10（相对汇率不变 ✓）

    // ---- 反方向（由上面三个基准推出 ✓ 不再手写数字 ✗）----

    /** 1 点法力 = 多少 EE */
    public static final double EE_PER_MANA = 1.0D / MANA_PER_EE;
    /** 1 点灵魂 = 0.25 EE */
    public static final double EE_PER_SOUL = 1.0D / SOULS_PER_EE;
    /** 1 点咒力 = 0.5 EE */
    public static final double EE_PER_CURSE = 1.0D / CURSE_PER_EE;

    // ---- 两两互换（三系之间也一律经由基准推出 ✓）----

    /** 1 法力 = 4 灵魂 */
    public static final double SOULS_PER_MANA = SOULS_PER_EE / MANA_PER_EE;
    /** 1 咒力 = 2 灵魂 */
    public static final double SOULS_PER_CURSE = SOULS_PER_EE / CURSE_PER_EE;
    /** 1 咒力 = 0.5 法力 */
    public static final double MANA_PER_CURSE = MANA_PER_EE / CURSE_PER_EE;
    /** 1 法力 = 2 咒力 */
    public static final double CURSE_PER_MANA = CURSE_PER_EE / MANA_PER_EE;
    /** 1 灵魂 = 0.25 法力 */
    public static final double MANA_PER_SOUL = MANA_PER_EE / SOULS_PER_EE;
    /** 1 灵魂 = 0.5 咒力 */
    public static final double CURSE_PER_SOUL = CURSE_PER_EE / SOULS_PER_EE;

    // ============================================================
    //  双向换算（EE 为中心 ✓ 全部走乘法/除法，不再出现裸数字 ✗）
    // ============================================================

    // ---- EE ↔ 法力 ----
    public static double eeToMana(double ee) { return ee * MANA_PER_EE; }
    public static double manaToEe(double mana) { return mana * EE_PER_MANA; }

    // ---- EE ↔ 灵魂 ----
    public static double eeToSouls(double ee) { return ee * SOULS_PER_EE; }
    public static double soulsToEe(double souls) { return souls * EE_PER_SOUL; }

    // ---- EE ↔ 咒力 ----
    public static double eeToCurse(double ee) { return ee * CURSE_PER_EE; }
    public static double curseToEe(double curse) { return curse * EE_PER_CURSE; }

    // ---- 直接两两互换（内部经 EE ⇒ 与上面的基准永远自洽 ✓）----

    public static double manaToSouls(double mana) { return eeToSouls(manaToEe(mana)); }
    public static double soulsToMana(double souls) { return eeToMana(soulsToEe(souls)); }

    public static double manaToCurse(double mana) { return eeToCurse(manaToEe(mana)); }
    public static double curseToMana(double curse) { return eeToMana(curseToEe(curse)); }

    public static double soulsToCurse(double souls) { return eeToCurse(soulsToEe(souls)); }
    public static double curseToSouls(double curse) { return eeToSouls(curseToEe(curse)); }

    // ============================================================
    //  取整助手（灵魂按"颗"、咒力按"点"扣，往往要向上取整 ✓）
    //  —— 与 AllPathsOneTrait / CursePowerHelper 原来的 Math.ceil 口径一致 ✓
    // ============================================================

    /** 要付 {@code ee} 点晶能，需要多少<b>整颗</b>灵魂（向上取整） */
    public static int ceilEeToSouls(double ee) { return (int) Math.ceil(eeToSouls(ee)); }

    /** 要付 {@code ee} 点晶能，需要多少<b>整点</b>咒力（向上取整） */
    public static int ceilEeToCurse(double ee) { return (int) Math.ceil(eeToCurse(ee)); }

    /** 要付 {@code ee} 点晶能，需要多少<b>整点</b>法力（向上取整） */
    public static int ceilEeToMana(double ee) { return (int) Math.ceil(eeToMana(ee)); }

    /** 要付 {@code ee} 点晶能，需要多少<b>整颗</b>灵魂（向下取整；用于"已付出多少"的反算） */
    public static int floorEeToSouls(double ee) { return (int) Math.floor(eeToSouls(ee)); }

    /** 要付 {@code ee} 点晶能，需要多少<b>整点</b>咒力（向下取整） */
    public static int floorEeToCurse(double ee) { return (int) Math.floor(eeToCurse(ee)); }

    // ============================================================
    //  人类可读（tooltip / 手册 / 日志用 ✓ 免得每处各写各的格式 ✗）
    // ============================================================

    /** 一句写清整张表的说明（手册与日志共用 ✓） */
    public static String describeRate() {
        return "1 EE = " + trim(MANA_PER_EE) + " 法力 = " + trim(SOULS_PER_EE) + " 灵魂 = "
                + trim(CURSE_PER_EE) + " 咒力";
    }

    private static String trim(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    // ============================================================
    //  §557 EE ⇄ 外部能量（用户给定口径 2026-09-22 ✓ 一个字都不许改 ✗）
    //
    //    1 FE = 8 EE              ⇒ 1 EE = 0.125 FE
    //    1 EU = 4 FE              （工业时代 IC2 的 EU）
    //    1 AE = 2 FE              （应用能源 AE 的 AE）
    //    10 J = 4 FE              ⇒ 1 J = 0.4 FE（通用机械 Mekanism 的 J）
    //    FE/t = (45 × RPM) / 64   ⇒ 1 RPM = 0.703125 FE/t（Create 转速）
    //    RF = Tesla = μI = FF = FE（都是 FE 的别名，1:1 ✓ 不需要适配器 ✗）
    // ============================================================

    /**
     * <b>1 FE 折多少 EE</b> —— 用户口径：{@code 1 FE = 8 EE} ✓。
     * <p>反方向 {@link #EE_PER_FE} 由它除法推出 ✓（不手写 0.125 ✗ 免得两处漂移）。
     */
    public static final double FE_PER_EE_FACTOR = 8.0D;

    /** 1 EE = 多少 FE（= 1/8 = 0.125 ✓） */
    public static final double EE_PER_FE = 1.0D / FE_PER_EE_FACTOR;

    /** 把 EE 换成一整点 FE（<b>向下取整</b> ✓ 不够 8 EE 就换不出 1 FE ✓ 余数留在容器里不丢 ✗） */
    public static int eeToFe(double ee) {
        if (!(ee > 0.0D)) return 0;
        return (int) Math.floor(ee * EE_PER_FE);
    }

    /** 把 {@code fe} 点 FE 折成 EE（= ×8 ✓ 整数运算 ✓） */
    public static int feToEe(int fe) {
        return fe <= 0 ? 0 : fe * (int) FE_PER_EE_FACTOR;
    }

    /**
     * <b>外部能量 → FE 的换算常量与助手</b>（§557 用户给定，逐条对着上面那张表 ✓）。
     *
     * <p>⚠ <b>RF / Tesla / μI / FF 不在这里</b> ✗ —— 它们就是 FE 的别名（1:1 ✓ 用户已确认 ✓）
     * ⇒ 任何暴露 {@code ForgeCapabilities.ENERGY} 的方块（RF 系、Tesla 系…）<b>走的就是 FE 那一路</b> ✓
     * 不需要也不会做单独的适配器 ✓。
     */
    public static final class Fe {

        private Fe() {
        }

        /** 1 EU 折多少 FE（用户口径 4 ✓；未来若 IC2 真接上了，这里改一个数就够 ✓） */
        public static final double FE_PER_EU = 4.0D;

        /** 1 AE 折多少 FE（用户口径 2 ✓） */
        public static final double FE_PER_AE = 2.0D;

        /** 每 10 J 折多少 FE（用户口径 4 ⇒ {@link #FE_PER_J} = 0.4 ✓） */
        public static final double JOULES_PER_UNIT = 10.0D;
        /** 10 J 对应的 FE（用户口径 4 ✓） */
        public static final double FE_PER_10_J = 4.0D;

        /** 1 J 折多少 FE（= 4/10 = 0.4 ✓ 由上面两个常量推出 ✓ 不手写 ✗） */
        public static final double FE_PER_J = FE_PER_10_J / JOULES_PER_UNIT;

        /** Create：分子（用户口径 45 ✓） */
        public static final double RPM_TO_FE_NUMERATOR = 45.0D;
        /** Create：分母（用户口径 64 ✓） */
        public static final double RPM_TO_FE_DENOMINATOR = 64.0D;

        /**
         * {@code FE/t = (45 × RPM) / 64} ✓ 用户口径。
         * <p>⇒ 1 RPM = 45/64 = <b>0.703125 FE/t</b> ✓。
         */
        public static double rpmToFePerTick(double rpm) {
            if (!(rpm > 0.0D)) return 0.0D;
            return (RPM_TO_FE_NUMERATOR * rpm) / RPM_TO_FE_DENOMINATOR;
        }

        /** 1 RPM 折多少 FE/t（= 45/64 = 0.703125 ✓） */
        public static double fePerRpm() {
            return RPM_TO_FE_NUMERATOR / RPM_TO_FE_DENOMINATOR;
        }

        /** J → FE（按 1 J = 0.4 FE ✓） */
        public static double joulesToFe(double joules) {
            if (!(joules > 0.0D)) return 0.0D;
            return joules * FE_PER_J;
        }

        /** EU → FE（按 1 EU = 4 FE ✓） */
        public static double euToFe(double eu) {
            if (!(eu > 0.0D)) return 0.0D;
            return eu * FE_PER_EU;
        }

        /** AE → FE（按 1 AE = 2 FE ✓） */
        public static double aeToFe(double ae) {
            if (!(ae > 0.0D)) return 0.0D;
            return ae * FE_PER_AE;
        }

        /** 一句写清 §557 的汇率（tooltip / 手册 / 日志共用 ✓） */
        public static String describeRate() {
            return "1 FE = " + trim(FE_PER_EE_FACTOR) + " EE; 1 EU = " + trim(FE_PER_EU) + " FE; 1 AE = "
                    + trim(FE_PER_AE) + " FE; " + trim(JOULES_PER_UNIT) + " J = " + trim(FE_PER_10_J)
                    + " FE; FE/t = (" + trim(RPM_TO_FE_NUMERATOR) + " x RPM) / "
                    + trim(RPM_TO_FE_DENOMINATOR);
        }
    }
}
