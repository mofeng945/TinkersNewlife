package com.mofengbaizhi.tinkersnewlife.util;

/** The two relationship scores share a budget; neither can contribute over 30 percentage points. */
public final class MomoMercyFormula {
    private MomoMercyFormula() {}
    public static float multiplier(int alignment, int favor) {
        int points = Math.max(0, Math.min(50, alignment)) + Math.max(0, Math.min(50, favor));
        return (1000 - 6 * points) / 1000f;
    }
}
