package com.mofengbaizhi.tinkersnewlife.content.skill;

/**
 * TNL1216 墨默的技能姿势类别：技能自己声明"我在摆哪种姿势"，客户端只照它画。
 *
 * <p>以前是三个布尔（CHARGING / CHORD / BLINK）分别由渲染器拼出来，容易漏一处就串姿势
 * （和弦抬手被吞、音波摆成念咒都出过）。现在改成唯一的枚举来源：技能用 {@link MomoSpellGoal#poseKind()}
 * 报出自己的类别，渲染器读实体同步的法术编号映射成枚举，模型只按枚举分支。
 */
public enum MomoPose {
    /** 没在施法，交给原版姿势 */
    NONE,
    /** 瞬身：站定垂手 */
    BLINK,
    /** 幻音共鸣：护脸蓄力 */
    RESONANCE,
    /** 混沌和弦：双手举起吟唱 */
    CHORD,
    /** TNL1251 受击硬直：身体前倾、手臂惯性前摆、双腿一前一后。
     *  只有 {@code MomoHurtGoal} 会用它 —— 技能施法的 stun **不播**这个。 */
    HURT,
    /** TNL1253 生息绝响：展开死生领域时的姿势（双手上举展开）。 */
    REQUIEM,
    /** TNL1287 飞踢：斜身、右腿前踢、左腿后收。 */
    KICK,
    /** TNL1294 和弦·挥动段：抬手完成之后，持续挥手发波的那一段。 */
    CHORD_WAVE;

    /** 由同步的法术编号得到姿势类别：1 瞬身、2 共鸣、3 和弦、4 绝响、6 飞踢 */
    public static MomoPose of(int spellId) {
        return switch (spellId) {
            case 1 -> BLINK;
            case 2 -> RESONANCE;
            case 3 -> CHORD;
            case 6 -> KICK;
            default -> NONE;
        };
    }
}
