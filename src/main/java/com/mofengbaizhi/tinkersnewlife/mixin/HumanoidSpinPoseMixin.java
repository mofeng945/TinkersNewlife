package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ⭐ §1124 长短刃「杀戮光环」的**双手平举**姿势（用户口径 ✓
 * 「使玩家模型**双手平举**刀刃持平然后开始快速旋转」✓）。
 *
 * <h2>⚠⚠ 为什么必须走 mixin（这是我第一次没做到位的地方 ✗）</h2>
 * ⭐ 原版每帧都会调 {@code HumanoidModel#setupAnim} 把手臂姿态**重算一遍** ✗
 * ⇒ ⚠ 在 {@code RenderPlayerEvent.Pre} 里设置手臂会被**立刻覆盖** ✗（所以第一版只有"转"没有"举"✓）
 * ⇒ ⭐ 唯一稳的时机是 **{@code setupAnim} 返回之后** ✓ ⇒ ⭐ 就在这里注入 {@code at = RETURN} ✓。
 *
 * <h2>⭐ 关键名怎么来的（实测 ✓ 不是猜 ✗）</h2>
 * 反编译 `joined-1.20.1-…-srg.jar` 的 `HumanoidModel` ✓：
 * <pre>
 *   public void m_6973_(T entity, float limbSwing, float limbSwingAmount,
 *                       float ageInTicks, float netHeadYaw, float headPitch)   ← ＝ setupAnim ✓
 *   public final ModelPart f_102811_;   // right_arm ✓
 *   public final ModelPart f_102812_;   // left_arm  ✓
 * </pre>
 * ⚠ 本仓惯例：**用 SRG 名 ＋ {@code remap = false}** ✓（官方名走 refmap 在本仓匹配不上 ✗ 见 §1117h ✓）。
 *
 * <h2>⭐ 手势（照用户给的参考实现 ✓）</h2>
 * 手臂 **绕 Z 轴各转 90°**（右手 ＋90°、左手 −90° ✓）⇒ 两臂**向两侧平举** ✓；
 * **双腿锁 0** ✓（避免旋转时腿在甩 ✓）；
 * ⭐ 进出各 5 tick 的**渐入渐出**（{@code t} 0→1 ✓）—— ⚠ 用**客户端自己的计时** ✗
 * （物品 NBT 的信号同步会滞后 ✓ 不能当精确计时器 ✓）。
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidSpinPoseMixin {

    /** ⚠ SRG 名（见类注释 ✓）—— 只读不改引用 ✓ 所以 {@code @Final} 是安全的 ✓ */
    @Shadow @Final public ModelPart f_102811_;   // right_arm
    @Shadow @Final public ModelPart f_102812_;   // left_arm
    @Shadow @Final public ModelPart f_102813_;   // right_leg
    @Shadow @Final public ModelPart f_102814_;   // left_leg

    /** ⭐ 渐入渐出各 5 tick ✓（照用户参考实现 ✓） */
    private static final int RAMP = 5;

    /** ⭐ 总时长（tick ✓）—— 与 {@link LongShortBladeItem#ULTIMATE_DURATION_TICKS} 保持一致 ✓＝100 ✓ */
    private static final int TOTAL = LongShortBladeItem.ULTIMATE_DURATION_TICKS;

    /** ⭐ 每个玩家"这次光环从哪个 age 开始" ✓（⚠ 用 {@code ageInTicks} 记 ✗ 不数调用次数 ✓） */
    private static final java.util.Map<java.util.UUID, Float> START_AGE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** ⭐ 每个玩家"信号消失时的 age" ✓（用于渐出 ✓） */
    private static final java.util.Map<java.util.UUID, Float> END_AGE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** ⭐ 交替挥动：上次看到的 NBT 计数器 ✓（变了 ⇒ 又挥了一刀 ✓） */
    private static final java.util.Map<java.util.UUID, Integer> SWING_LAST =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** ⭐ 交替挥动：副手那一挥从哪个 age 开始 ✓（同样用 age 计时 ✗ 不数帧 ✓） */
    private static final java.util.Map<java.util.UUID, Float> SWING_START =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** ⭐ 副手一挥的时长（tick ✓ 约等于原版挥动时长 ✓） */
    private static final float SWING_TICKS = 6.0F;

    @Inject(method = "m_6973_", at = @At("RETURN"), require = 1, remap = false)
    private void tnl$spinPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        try {
            if (!(entity instanceof Player player)) {
                return;
            }
            // ⭐⭐ **交替挥动：副手那一挥**（用户实测四轮 ✓ 最后定案 ✓）
            //   ⚠⚠ 上一版用**物品 NBT** 当信号 ✗ ⇒ 探针实测：服务端每击都在写 ✓
            //     但客户端 counter **大约每秒才跳一次** ✗（41→42→46→50→57…✓）
            //     ⇒ 副手每秒才播一次 ＝ 用户说的「左手极少挥动」✗
            //   ⇒ ⭐ 现已改为**服务端每击直接给本人发 S2C 包** ✓（`PacketSwingOffhand` ✓）
            //     ⇒ 零延迟 ✓ 不依赖 NBT 推送时机 ✓
            //   ⚠ 计时仍用**绝对 tick**✗ 不数调用次数 ✓（§1130 的教训 ✓）。
            float offhandSwing = com.mofengbaizhi.tinkersnewlife.client.renderer
                    .LongShortBladeSpinHandler.offhandSwingProgress(entity.tickCount);
            if (offhandSwing > 0.0F) {
                this.f_102812_.xRot = -offhandSwing * 1.3F;   // ⭐ 左臂向前挥 ✓
                this.f_102812_.zRot = 0.0F;
                // ⚠ **临时探针三**（⭐ 铁证：证明这段**真的执行了** ✓ 定位完即删 ✗）——
                //   ⭐ 若这行有而你看不到 ⇒ ⭐ **是你的视角看不到自己的模型** ✗（第一人称 ✗）
                //   ⭐ 若这行没有 ⇒ 注入/mixin 没生效 ✗。
                if (entity.tickCount % 20 == 0) {
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                            "[长短刃·探针] 左臂挥动已应用 swing={} tick={}",
                            String.format(java.util.Locale.ROOT, "%.2f", offhandSwing), entity.tickCount);
                }
            }

            float t = spinFactor(player, ageInTicks);
            if (t <= 0.0F) {
                // ⭐⭐ **副手挥动**（⚠ 用户实测：「还是没左右挥动」✗ 的第二层原因 ✓）
                //   ⭐ 原版 `HumanoidModel#setupAnim` 挥动时**只按 `getMainArm()` 挑手臂** ✗
                //   ⇒ ⚠ 即使服务端把"副手挥动"的动画包（id 3 ✓）发出来了 ✗
                //     原版**还是挥同一只手** ✗ ⇒ ⭐ 这里自己把**左臂**转起来 ✓
                //   （⭐ 服务端在 `LongShortBladeHandler` 里已绕过 `swing()` 的闸门直接发包 ✓
                //     所以客户端拿得到 `swingingArm == OFF_HAND` ✓）。
                if (entity.swinging && entity.swingingArm == net.minecraft.world.InteractionHand.OFF_HAND) {
                    float f = entity.getAttackAnim(0.0F);   // ⚠ 注入参数里没有 partialTick ✗ 用 0 略顿 ✓ 可接受 ✓
                    float swing = net.minecraft.util.Mth.sin(net.minecraft.util.Mth.sqrt(f) * (float) Math.PI);
                    this.f_102812_.xRot = -swing * 1.2F;    // ⭐ 左臂向前挥 ✓
                    this.f_102812_.zRot = 0.0F;
                }
                return;
            }
            // ⭐ 双手平举：绕 Z 轴各 90° ✓ 右手 ＋、左手 − ✓（照用户参考实现 ✓）
            this.f_102811_.xRot = 0.0F;
            this.f_102811_.yRot = 0.0F;
            this.f_102811_.zRot = (float) (Math.PI * 0.5F * t);
            this.f_102812_.xRot = 0.0F;
            this.f_102812_.yRot = 0.0F;
            this.f_102812_.zRot = (float) (-Math.PI * 0.5F * t);
            // ⭐ 双腿锁 0 ✓（不然旋转时腿在甩 ✓）
            this.f_102813_.xRot = 0.0F;
            this.f_102813_.yRot = 0.0F;
            this.f_102813_.zRot = 0.0F;
            this.f_102814_.xRot = 0.0F;
            this.f_102814_.yRot = 0.0F;
            this.f_102814_.zRot = 0.0F;
        } catch (Throwable ignored) {
            // ⭐ 改姿态出错绝不能崩客户端 ✗
        }
    }

    /**
     * ⭐ 本帧该举多少（0 ＝ 不举 ✓ 1 ＝ 完全平举 ✓）。
     *
     * <h2>⚠⚠ 这里**绝对不能"数调用次数"**（我第一版就是那么错的 ✗）</h2>
     * {@code setupAnim} 是**每帧、每个实体**都调一次 ✗ ⇒ 若"调一次 ＋1" ✓
     * 计数器**每帧涨好几次** ⇒ ⭐ 一秒内就冲过 {@link #TOTAL} ⇒ 之后一直走渐出分支 ⇒ 返回 ~0 ✗
     * ⇒ ⭐ **表现就是"胳膊一直没动"** ✗（用户实测报告 ✓ 那就是这个 ✓）。
     * <p>⇒ ⭐ 改用 {@code ageInTicks}（**已含 partialTick** ✓）记"起点 age" ✓ ⇒ 每 tick 只涨 1 ✓。
     */
    private static float spinFactor(Player player, float ageInTicks) {
        java.util.UUID id = player.getUUID();
        boolean on = spinning(player);

        if (on) {
            Float start = START_AGE.get(id);
            if (start == null) {
                // ⭐ 信号刚出现 ⇒ 记起点 ✓ 并清掉上一次的渐出记录 ✓
                START_AGE.put(id, ageInTicks);
                END_AGE.remove(id);
                start = ageInTicks;
            }
            float e = ageInTicks - start;
            if (e <= RAMP) {
                return e / (float) RAMP;                                   // ⭐ 渐入 ✓
            }
            if (e >= TOTAL - RAMP) {
                return Math.max(0.0F, (TOTAL - e) / (float) RAMP);          // ⭐ 渐出 ✓
            }
            return 1.0F;
        }

        Float start = START_AGE.get(id);
        if (start == null) {
            return 0.0F;                                                   // ⭐ 从没开过 ✓
        }
        Float end = END_AGE.get(id);
        if (end == null) {
            end = ageInTicks;
            END_AGE.put(id, end);
        }
        float e = ageInTicks - end;
        if (e >= RAMP) {
            START_AGE.remove(id);
            END_AGE.remove(id);
            return 0.0F;                                                   // ⭐ 渐出走完 ⇒ 彻底复位 ✓
        }
        return 1.0F - e / (float) RAMP;
    }

    /** ⭐ 手里（主手或副手 ✓）有没有"光环进行中"的信号 ✓ —— 与渲染器同一判据 ✓ */
    private static boolean spinning(Player player) {
        ItemStack main = player.getMainHandItem();
        if (LongShortBladeItem.isUltimateVisual(main)) {
            return true;
        }
        return LongShortBladeItem.isUltimateVisual(player.getOffhandItem());
    }
}
