package com.mofengbaizhi.tinkersnewlife.content.modifier;

/** Slot levels are summed within each pool. Dragon breath then passes through both pools. */
public final class DragonSlayerProtection {
    private DragonSlayerProtection() {}
    public static boolean fearEligible(long equippedLevels, float health, double distanceSquared, boolean boss) {
        return equippedLevels >= 4 && health > 0 && health <= 20 && distanceSquared <= 100 && !boss;
    }
    public static float multiplier(int head,int chest,int legs,int feet,boolean breath) {
        double ends = (double)Math.max(0,head) + Math.max(0,feet);
        double general = Math.min(.50, ends*.0125 + .025*((double)Math.max(0,chest)+Math.max(0,legs)));
        double dragon = breath ? Math.min(.70, ends*.05 + Math.max(0,chest)*.15 + Math.max(0,legs)*.10) : 0;
        return (float)((1-general)*(1-dragon));
    }
}
