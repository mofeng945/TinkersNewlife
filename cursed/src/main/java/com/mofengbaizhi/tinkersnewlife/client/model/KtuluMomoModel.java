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

    /**
     * TNL1224 姿势的唯一输入：渲染器每帧写一次，模型只照它分支。
     * 以前这里是四个布尔（CHARGING/CHORD/BLINK/RESONANCE），其中 CHARGING 名字还停在
     * "只有共鸣会蓄力"那会儿，实际却是"有技能在施法"的总闸，很容易看错。
     */
    public static com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose POSE =
            com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.NONE;

    /** 0 = 刚开始，1 = 马上要尖啸 */
    public static float PROGRESS = 0.0F;

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
        // TNL1188 每帧重置手臂偏移；和弦姿势会挪它们，残留的偏移会渗透到其它姿势。
        // TNL1222 用户："正常情况下手臂还是在身体里"。根因就是这里：原来把 x 归 0，
        // 等于把骨架自带的肩部偏移（±5）也清了 ⇒ 两条手臂被吸进躯干、看着像苦力怕。
        // x 必须回到网格的默认肩宽，只有旋转/缩放由姿势负责。
        // TNL1225 用户："两个手臂是反的"。原版 HumanoidModel 里右臂在 x=-5、左臂在 x=+5，
        // 之前照诡厄抄成了 +5/-5 ⇒ 左右镜像了。这里两处都改回原版符号。
        this.rightArm.x = -5.0F;
        this.leftArm.x = 5.0F;
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
        // TNL1224 没在施法 ⇒ 原版姿势
        if (POSE == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.NONE) {
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
        // TNL1223 用户："幻音共鸣没同级别统一写法？"。原来共鸣是"没被上面拦住的都算我"，
        // 新增技能会**默默借用它的姿势**。现在三分支同级，兜底留给原版姿势。
        if (POSE == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.RESONANCE) {
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
        }

        // TNL1105 混沌和弦：唤魔者式施法，双手高举并摆动。
        long nowMs = System.currentTimeMillis();

        // TNL1252 受击硬直（用户："身体前倾，手臂惯性向前摆动，双腿一前一后，这样颤动一下，硬直为 1s"）。
        // 只有 MomoHurtGoal 会上报 HURT ⇒ 技能施法的定身**不会**走到这里。
        if (POSE == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.HURT) {
            float tnl$hurt = Math.max(0.0F, Math.min(1.0F, PROGRESS));
            float tnl$shake = (float) Math.sin(ageInTicks * 3.5F) * (1.0F - tnl$hurt);   // 开头最抖，1 秒内平息
            this.rightArm.x = -5.0F;
            this.leftArm.x = 5.0F;
            this.body.xRot = 0.35F - 0.25F * tnl$hurt + tnl$shake * 0.06F;   // 前倾，随后收回
            this.head.xRot = 0.30F + tnl$shake * 0.05F;
            this.head.yRot = netHeadYaw * 0.3F;
            this.rightArm.xRot = -0.90F + tnl$shake * 0.10F;                 // 惯性向前甩
            this.rightArm.yRot = 0.10F;
            this.rightArm.zRot = -0.10F;
            this.leftArm.xRot = -0.90F - tnl$shake * 0.10F;
            this.leftArm.yRot = -0.10F;
            this.leftArm.zRot = 0.10F;
            this.rightLeg.xRot = 0.35F;                                      // 双腿一前一后
            this.leftLeg.xRot = -0.35F;
            this.hat.xRot = this.head.xRot;
            this.hat.yRot = this.head.yRot;
            this.hat.zRot = this.head.zRot;
            return;
        }

        // TNL1254 生息绝响：双手合十举到脸前，修女祈祷状（用户口径）。
        // 只在 RequiemGoal 施法时上报 ⇒ 其它技能的 stun 不会走到这里。
        if (POSE == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.REQUIEM) {
            float tnl$pray = Math.max(0.0F, Math.min(1.0F, PROGRESS));
            float tnl$breath = (float) Math.sin(ageInTicks * 0.9F) * 0.03F;
            this.body.xRot = 0.10F + tnl$breath * 0.5F;                 // 微微前倾，随呼吸起伏
            this.head.xRot = 0.28F;                                     // 低头祈祷
            this.head.yRot = netHeadYaw * 0.25F;
            // 双手合十：两臂抬到脸前并在中线并拢（xRot 抬起，yRot/zRot 收向中心）
            this.rightArm.x = -5.0F;
            this.leftArm.x = 5.0F;
            this.rightArm.xRot = -1.45F + tnl$breath;
            this.rightArm.yRot = -0.55F;
            this.rightArm.zRot = -0.30F;
            this.leftArm.xRot = -1.45F + tnl$breath;
            this.leftArm.yRot = 0.55F;
            this.leftArm.zRot = 0.30F;
            this.rightLeg.xRot = 0.0F;
            this.leftLeg.xRot = 0.0F;
            this.hat.xRot = this.head.xRot;
            this.hat.yRot = this.head.yRot;
            this.hat.zRot = this.head.zRot;
            return;
        }

        // TNL1156 姿势由渲染器逐帧决定：只有当正在施法的是混沌和弦时，
        // 才用和弦姿势。老的 CHORD_HOLD_UNTIL 判定会让
        // **任何**技能蓄力后和弦姿势挂 20 秒，于是音波起手
        // 看起来和弦一样，而和弦自己反而丢了姿势。
        if (POSE == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.CHORD) {
            // TNL1188 直接照搬诡厄 CultistModel 的 SPELLCASTING 分支（已验证观感）：
            // zRot ±135 度把手抬起来，0.6662 的 cos 波给吟唱带来摆动，
            // 肩部 x 偏 ±5 把两臂分开，免得双手在中线重叠。
            float wave = (float) Math.cos(ageInTicks * 0.6662F) * 0.25F;
            float wave2 = (float) Math.cos(ageInTicks * 0.6662F + 0.6F) * 0.25F;
            this.rightArm.x = -5.0F;
            this.leftArm.x = 5.0F;
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

        // TNL1288 飞踢：照用户给的三格参考图
        //   图122 起手护脸 → 图123 抬膝（大腿抬到水平，身体微前倾、双手护脸）
        //   → 图124 蹬出（身体强烈前倾、踢腿向后上方伸到水平，手臂一前一后展开）
        //   进度由 MomoKickGoal#animProgress() 驱动：
        //   冲刺段 0.05→0.35（抬膝），踢出瞬间 0.35→0.55（蹬出），之后收回到 0.30。
        if (POSE == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.KICK) {
            float t = Math.max(0.0F, Math.min(1.0F, PROGRESS));
            float chamber;      // 0→1 抬膝
            float extend;       // 0→1 蹬出
            if (t < 0.30F) {
                chamber = t / 0.30F;
                extend = 0.0F;
            } else if (t < 0.50F) {
                chamber = 1.0F;
                extend = (t - 0.30F) / 0.20F;
            } else if (t < 0.62F) {
                chamber = 1.0F;
                extend = 1.0F;
            } else {
                float r = Math.min(1.0F, (t - 0.62F) / 0.38F);
                chamber = 1.0F - r;
                extend = 1.0F - r;
            }
            // 抬膝：右腿前抬到水平（-1.35）；蹬出：向后上方摆到水平（+1.55）
            this.rightLeg.xRot = -1.35F * chamber + 1.55F * extend;
            this.rightLeg.yRot = 0.12F * extend;
            this.rightLeg.zRot = 0.10F * extend;
            // 支撑腿：抬膝时站稳，蹬出时略后蹬
            this.leftLeg.xRot = 0.18F * chamber - 0.18F * extend;
            this.leftLeg.yRot = 0.0F;
            this.leftLeg.zRot = 0.0F;
            // 躯干：抬膝时几乎直立，蹬出时像图124那样强烈前倾
            this.body.xRot = 0.06F * chamber + 0.92F * extend;
            this.head.xRot = 0.06F * chamber + 0.30F * extend;
            this.head.yRot = netHeadYaw * 0.2F;
            // 手臂：抬膝时护脸（借共鸣的护脸数值），蹬出时一前一后展开（图124）
            float guardX = -1.05F;
            float guardY = -0.30F;
            float guardZ = 0.70F;
            this.rightArm.xRot = guardX * chamber - 0.45F * extend;
            this.rightArm.yRot = guardY * chamber + 0.15F * extend;
            this.rightArm.zRot = guardZ * chamber - 0.35F * extend;
            this.leftArm.xRot = guardX * chamber + 0.55F * extend;
            this.leftArm.yRot = -guardY * chamber - 0.15F * extend;
            this.leftArm.zRot = -guardZ * chamber + 0.35F * extend;
            this.hat.xRot = this.head.xRot;
            this.hat.yRot = this.head.yRot;
            this.hat.zRot = this.head.zRot;
            return;
        }

        // TNL1189 瞬身：站定，双臂垂下略内收（学诡厄 CROSSED 的意图），
        // 头锁正前方。插在共鸣分支**之前**，这样瞬身不会
        // 借用监守者的护脸姿势。
        if (POSE == com.mofengbaizhi.tinkersnewlife.content.skill.MomoPose.BLINK) {
            return;
        }

        // TNL1102 用户："帽子绑到身体上了，我要它绑在头上"。在这套烘焙网格里 hat 是
        // 根级部件，所以显式把头部旋转复制给它。如果发现
        // 被算了两遍（因为它也从头部继承），把这三行减半。
        this.hat.xRot = this.head.xRot;
        this.hat.yRot = this.head.yRot;
        this.hat.zRot = this.head.zRot;


    }
}
