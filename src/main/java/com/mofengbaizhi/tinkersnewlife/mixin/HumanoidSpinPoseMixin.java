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

    /** ⭐ 每个玩家"这次光环已经走了多少 tick" ✓（客户端自己数 ✗ 不信 NBT 的滞后值 ✓） */
    private static final java.util.Map<java.util.UUID, Integer> ELAPSED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** ⭐ 上一帧信号还在的玩家 ✓（用来判断"信号刚消失 ⇒ 进渐出" ✓） */
    private static final java.util.Map<java.util.UUID, Boolean> WAS_ON =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Inject(method = "m_6973_", at = @At("RETURN"), require = 1, remap = false)
    private void tnl$spinPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        try {
            if (!(entity instanceof Player player)) {
                return;
            }
            float t = spinFactor(player);
            if (t <= 0.0F) {
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
     * ⭐ 本帧该举多少（0 ＝ 不举 ✓ 1 ＝ 完全平举 ✓）——
     * ⚠ 用**客户端自己数**的经过 tick ✗（物品 NBT 同步会滞后 ✓ 拿它当计时器会抖 ✓），
     * 但"**该不该举**"仍看物品 NBT 的信号 ✓（那是唯一能跨端拿到的东西 ✓）。
     */
    private static float spinFactor(Player player) {
        java.util.UUID id = player.getUUID();
        boolean on = spinning(player);
        if (on) {
            ELAPSED.merge(id, 1, Integer::sum);
            WAS_ON.put(id, Boolean.TRUE);
        } else if (Boolean.TRUE.equals(WAS_ON.remove(id))) {
            // ⭐ 信号刚消失 ⇒ 继续走渐出 ✓ 走完就彻底停 ✓
            int e = ELAPSED.merge(id, 1, Integer::sum);
            if (e > RAMP) {
                ELAPSED.remove(id);
                return 0.0F;
            }
            return 1.0F - (float) e / (float) RAMP;
        } else {
            return 0.0F;
        }
        int e = ELAPSED.getOrDefault(id, 0);
        if (e <= RAMP) {
            return (float) e / (float) RAMP;                 // ⭐ 渐入 ✓
        }
        if (e >= TOTAL - RAMP) {
            return Math.max(0.0F, (float) (TOTAL - e) / (float) RAMP);   // ⭐ 渐出 ✓
        }
        return 1.0F;
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
