package com.mofengbaizhi.tinkersnewlife.content.skill;

/**
 * TNL1104 user: "only write the skill set for now, AI last, and only load ONE skill per test".
 *
 * Flip exactly one of these to true to test that skill alone. A disabled skill's handler returns
 * immediately, so nothing else runs.
 */
public final class MomoSkills {

    /** 星界共鸣：贴近 20 秒后蓄力推飞（已验收 ✓） */
    public static final boolean ASTRAL_RESONANCE = false;

    /** 混沌和弦：蓄力 2 秒后发出 5 道声波圈（本轮测试 ✓） */
    public static final boolean DISCORDANT_CHORD = true;

    /** 瞬身：锁定目标 1.5 秒后无声瞬移到其身后 3 格（本轮测试 ✓） */
    public static final boolean BLINK = false;

    private MomoSkills() {
    }
}
