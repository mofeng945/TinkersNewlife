package com.mofengbaizhi.tinkersnewlife.content.handler;

/** Ranking of stored dragons, using the same two-decimal score shown to the player. */
public final class DragonPitPower {
    private DragonPitPower() {}
    private static final double[] STAGE = {1, 1.25, 1.55, 1.90, 2.35};

    public static double score(int stage, double maxHp, double attack, double armor) {
        if (stage < 1 || stage > 5) throw new IllegalArgumentException("Dragon stage must be 1–5");
        double effectiveHp = positive(maxHp) * (100 + positive(armor)) / 100;
        return Math.round(STAGE[stage - 1] * Math.sqrt(positive(attack) * effectiveHp) * 100) / 100.0;
    }

    private static double positive(double v) { return Double.isFinite(v) ? Math.max(0, v) : 0; }
}
