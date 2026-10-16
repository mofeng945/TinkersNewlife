package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * TNL1091 幻音共鸣蓄力：学监守者的起手，分三个看得见的阶段。
 *
 * 进度 0.00~0.45：身体微前倾、低头、双手抬到脸前
 * 进度 0.45~0.75：保持护脸，轻微发抖
 * 进度 0.75~1.00：双臂向后张开，然后尖啸放出
 *
 * 进度来自隐藏标记的冷却 —— 那是唯一验证过能到客户端的通道
 * （24 行 charging=true 探针），所以动画由真实数值驱动，不是猜的。
 */
public class KtuluMomoModel extends HumanoidModel<KtuluMomo> {

    /** 渲染器在 super.render 之前写入，setupAnim 就读它 */
    public static boolean CHARGING = false;

    /** TNL1156 只有正在施放混沌和弦的帧才为 true */
    public static boolean CHORD = false;

    /** TNL1189 只有正在施放瞬身的帧才为 true */
    public static boolean BLINK = false;
    /** 0 = 刚开始，1 = 马上要尖啸 */
    public static float PROGRESS = 0.0F;
    /** TNL1108 同步标志掉了之后和弦还要保持举手一会儿，
     *  免得环还在往外飞的时候姿势突然放下。*/
    public static long CHORD_HOLD_UNTIL = 0L;

    public KtuluMomoModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(KtuluMomo entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // TNL1101 头发**就是**这个 hat 部件（蓄力时隐藏它会让头发整个消失），老代码
        // 那句 `visible = !CHARGING` 写在早退**之后**，所以帽子再也回不来。
        // 现在无条件先恢复，放在任何早退之前。
        // TNL1188 每帧重置手臂偏移；和弦姿势会挪它们，残留的偏移
        // 会渗透到其它所有姿势里。
        this.rightArm.x = 0.0F;
        this.leftArm.x = 0.0F;
        // TNL1203 用户："手臂藏进身体里了，常态下像个苦力怕"。
        // TNL1187 那个 0.62 横截面缩放，在这套烘焙网格上把袖子缩进了躯干，
        // 所以"细手臂"方案撤销，手臂缩放恢复原版。
        this.rightArm.xScale = 1.0F;
        this.rightArm.zScale = 1.0F;
        this.leftArm.xScale = 1.0F;
        this.leftArm.zScale = 1.0F;
        this.hat.visible = true;
        this.hat.skipDraw = false;
        this.hat.yScale = 1.0F;
        if (!CHARGING) {
            return;
        }
        // TNL1096 用户："momo_common 皮肤里不跟着转的就是帽子那层"。
        // 在原版 HumanoidModel 里 hat 是头的**子部件**，本来应该自动跟随。
        // 这个强制扭转是决定性测试：如果看得见的帽子不转，那我们看到的皮肤
        // 就根本不是这个模型实例画的。
        // TNL1097 用户确认是实机那只（B）且身体发抖**看得见**，
        // 所以确实是这个模型在画。那帽子扭转看不见就只能说明 hat 部件
        // 在这套皮肤下压根没被画出来。强制可见、做标记、并打一次状态日志。
        this.hat.visible = true;
        this.hat.skipDraw = false;
        // TNL1098 不会认错的身份测试：如果我们摆的这个模型就是被画的那个，那蓄力时
        // 墨默必须肉眼可见地膨胀。可帽子探针显示 visible=true、yRot=1.0 却毫无变化，
        // 所以要嘛这个模型不是被渲染的那个，要嘛外层头发不是这个部件。
        // TNL1205 用户："手臂埋进身体里了，你做的什么缩放？"。两处：
        // 0.62 的手臂横截面（TNL1203 已撤）＋ 这个 1.45 的身体膨胀 —— 后者是
        // TNL1098 那个"身份测试"的遗留。两处都已删除，
        // 身体不再缩胀，手臂就在网格原本的位置上。
        this.body.xScale = 1.0F;
        this.body.zScale = 1.0F;
        float t = Math.max(0.0F, Math.min(1.0F, PROGRESS));
        // TNL1092 用户："只有发抖，骨骼动画没了"。发抖是挂在
        // body.xRot 上的，说明钩子确实在跑。打数值看 t 是不是卡在端点上。
        float tremble = (float) Math.sin(ageInTicks * 2.2F) * 0.04F * Math.min(1.0F, t * 2.0F);

        // 阶段 1：抬手护脸。阶段 3：向后张开。中间保持护脸。
        float up;
        if (t < 0.45F) {
            up = t / 0.45F;                       // 0 -> 1 raising the hands
        } else if (t < 0.75F) {
            up = 1.0F;                            // holding
        } else {
            up = 1.0F - (t - 0.75F) / 0.25F;      // 1 -> 0 swinging the hands back down and behind
        }
        float backSwing = t < 0.75F ? 0.0F : (t - 0.75F) / 0.25F;

        // 轻微前倾，蓄力越深越明显，释放时向后仰
        this.body.xRot = 0.22F * Math.min(1.0F, t * 1.6F) - 0.40F * backSwing + tremble * 0.4F;   // TNL1101
        // 护脸时低头，尖啸时抬头
        this.head.xRot = 0.52F * up - 0.85F * backSwing + tremble;   // TNL1101 stronger, so the hair visibly follows
        this.head.yRot = netHeadYaw * 0.4F;

        // TNL1094 用户："护脸反了，手臂往前张开而不是挡住脸，
        // 而且从来没向后甩"。yRot 与 zRot 反过来，让双手向内收拢
        // 贴到脸上，释放则插值到一个明确的向后张开姿势。
        float guardX = -1.35F * up;                       // raise the hands forward and up
        float guardY = -0.45F * up;                       // bring the right hand toward the centre
        float guardZ = 0.90F * up;                        // adduct it in front of the face
        float openX = 1.40F;                              // release: arms swung behind the body
        float openY = 0.50F;
        float openZ = -0.35F;

        this.rightArm.xRot = guardX * (1.0F - backSwing) + openX * backSwing + tremble;
        this.rightArm.yRot = guardY * (1.0F - backSwing) + openY * backSwing;
        this.rightArm.zRot = guardZ * (1.0F - backSwing) + openZ * backSwing;
        this.leftArm.xRot = guardX * (1.0F - backSwing) + openX * backSwing - tremble;
        this.leftArm.yRot = -guardY * (1.0F - backSwing) - openY * backSwing;
        this.leftArm.zRot = -guardZ * (1.0F - backSwing) - openZ * backSwing;

        // 蓄力时双腿微微撑住
        this.rightLeg.xRot = 0.12F * up;
        this.leftLeg.xRot = -0.10F * up;
        // TNL1105 混沌和弦：唤魔者式施法，双手高举并摆动。
        long nowMs = System.currentTimeMillis();

        // TNL1156 姿势由渲染器逐帧决定：只有当正在施法的是混沌和弦时，
        // 才用和弦姿势。老的 CHORD_HOLD_UNTIL 判定会让
        // **任何**技能蓄力后和弦姿势挂 20 秒，于是音波起手
        // 看起来和弦一样，而和弦自己反而丢了姿势。
        if (CHORD) {
            // TNL1188 直接照搬诡厄 CultistModel 的 SPELLCASTING 分支（已验证观感）：
            // zRot ±135 度把手抬起来，0.6662 的 cos 波给吟唱带来摆动，
            // 肩部 x 偏 ±5 把两臂分开，免得双手在中线重叠。
            float wave = (float) Math.cos(ageInTicks * 0.6662F) * 0.25F;
            float wave2 = (float) Math.cos(ageInTicks * 0.6662F + 0.6F) * 0.25F;
            this.rightArm.x = 5.0F;
            this.leftArm.x = -5.0F;
            this.body.xRot = 0.06F;
            this.head.xRot = -0.18F + wave * 0.05F;      // chin up, chanting
            // TNL1109 用户："手臂又指反了"。xRot 是前后轴，
            // 所以之前往前往后都错。把手臂举到头上是 zRot（正面）轴。
            this.rightArm.xRot = 0.0F;
            this.rightArm.yRot = wave;
            this.rightArm.zRot = 2.3561945F + wave;      // 135 degrees, Goety SPELLCASTING
            this.leftArm.xRot = 0.0F;
            this.leftArm.yRot = wave2;
            this.leftArm.zRot = -2.3561945F - wave2;
            this.rightLeg.xRot = 0.0F;
            this.leftLeg.xRot = 0.0F;
            this.hat.xRot = this.head.xRot;
            this.hat.yRot = this.head.yRot;
            this.hat.zRot = this.head.zRot;
            return;
        }

        // TNL1189 瞬身：站定，双臂垂下略内收（学诡厄 CROSSED 的意图），
        // 头锁正前方。插在共鸣分支**之前**，这样瞬身不会
        // 借用监守者的护脸姿势。
        if (BLINK) {
            this.body.xRot = 0.0F;
            this.head.xRot = 0.0F;
            this.head.yRot = netHeadYaw * 0.25F;
            this.rightArm.xRot = 0.0F;
            this.rightArm.yRot = 0.0F;
            this.rightArm.zRot = -0.35F;
            this.leftArm.xRot = 0.0F;
            this.leftArm.yRot = 0.0F;
            this.leftArm.zRot = 0.35F;
            this.rightLeg.xRot = 0.0F;
            this.leftLeg.xRot = 0.0F;
            this.hat.xRot = this.head.xRot;
            this.hat.yRot = this.head.yRot;
            this.hat.zRot = this.head.zRot;
            return;
        }

        // TNL1102 用户："帽子绑到身体上了，我要它绑在头上"。在这套烘焙网格里 hat 是
        // 根级部件，所以显式把头部旋转复制给它。如果发现
        // 被算了两遍（因为它也从头部继承），把这三行减半。
        this.hat.xRot = this.head.xRot;
        this.hat.yRot = this.head.yRot;
        this.hat.zRot = this.head.zRot;

        // TNL1099 探针改到"姿势应用之后"再跑，以前打的是静止值
        if (entity.tickCount % 10 == 0) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL1099 applied t={} bodyX={} headX={} hatY={} rightArmX={}",
                    t, this.body.xRot, this.head.xRot, this.hat.yRot, this.rightArm.xRot);
        }

    }
}
