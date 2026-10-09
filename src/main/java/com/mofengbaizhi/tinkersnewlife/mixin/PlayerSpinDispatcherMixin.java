package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ⭐⭐ §1180 <b>杀戮光环的"转起来"改在 {@code EntityRenderDispatcher} 这一层</b>
 * （⭐ 用户实测 ✓ 2026-10-09：「**ysm 好像没兼容我的杀戮光环动画**」✓）。
 *
 * <h2>⚠⚠ 为什么要搬到这里（⭐ 根本原因 ✓）</h2>
 * ⭐ 原来画在 ⭐ `HumanoidModel#setupAnim` 的 mixin（{@code HumanoidSpinPoseMixin} ✓）✗
 * ⭐ 而 ⭐ **是，史蒂夫模型（YSM）会整条接管玩家渲染** ✗ —— ⭐ 它自己 mixin 了
 * ⭐ **{@code EntityRenderDispatcher#render}** ✓ ⭐ 于是 ⭐ **原版 `HumanoidModel` 根本不会被调用** ✗
 * ⇒ ⭐ 手臂与旋转**一起失效** ✓。
 * <p>⚠ 这条经验 ⭐ **本仓库早就写过** ✗（⭐ 见 {@code EntityRenderDispatcherMixin} 与
 * {@code FlyingSwordFootRenderHandler} 的注释 ✓：「挂在更下层的 `RenderPlayerEvent.Post`
 * 会被 YSM 整条绕开 ✗」✓）—— ⭐ 我这次是把同一条经验**用到了杀戮光环上** ✓。
 *
 * <h2>⭐⭐ 做法：在最外层推一个"绕 Y 轴旋转"的姿势栈</h2>
 * ⭐ YSM 也在这个方法里画画 ✗ ⇒ ⭐ **我在 {@code HEAD} 先 `pushPose` ＋ 旋转** ✓
 * ⇒ ⭐ 之后**无论谁来画**（⭐ 原版 `PlayerRenderer` ✓ ⭐ 还是 YSM ✓）⭐ 都在这个旋转里 ✓ ✓
 * ⇒ ⭐ **两边都兼容** ✓（⚠ 而 ⚠ 原来那个 {@code RenderPlayerEvent} 里的旋转**必须删掉** ✗
 * ⭐ 否则原版路径会**转两次** ✓ —— ⭐ 已同步删除 ✓）。
 *
 * <h2>⚠ 注入两遍（⭐ 照 {@code EntityRenderDispatcherMixin} 的写法 ✓）</h2>
 * ⭐ 一次用**方法名** `render` ✗ ⭐ 一次**直连 SRG** `m_114384_` ✗（⭐ `remap = false` ✓）
 * —— ⭐ 因为 ⭐ refmap 在某些环境下**不生效** ✗ ⭐ 直连 SRG 是**兜底** ✓（⭐ 仓库里已有的实例 ✓）。
 * ⚠ 但 ⚠ 两次注入是 ⭐ **同一个方法** ✗ ⇒ ⭐ **绝不能让两边都转** ✗
 * ⇒ ⭐ 用一个 ⭐ `ThreadLocal` 深度计数器 ✗ ⭐ **一次调用只转一次** ✓。
 */
@Mixin(value = EntityRenderDispatcher.class, priority = 1400)
public class PlayerSpinDispatcherMixin {

    /** ⭐ 每 tick 转多少度 ✓（⭐ 90°/tick ＝ 每秒 5 圈 ✓ 与原来一致 ✓） */
    private static final float DEG_PER_TICK = 90.0F;

    /** ⚠ 旋转轴点在脚底会让模型"绕脚甩一圈" ✗ ⇒ ⭐ 抬到腰部附近 ✓（⭐ 与原来一致 ✓） */
    private static final double PIVOT_Y = 0.9D;

    /**
     * ⚠ 这一次 `render` 调用里"我推过姿势栈了吗" ✓ —— ⭐ 用 `ThreadLocal` 而不是普通字段 ✗
     * （⭐ 渲染虽然基本单线程 ✓ ⚠ 但 `ThreadLocal` 更安全 ✓ 且能防重入 ✓）。
     */
    private static final ThreadLocal<Boolean> TNL_PUSHED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "render", at = @At("HEAD"))
    private void tinkersnewlife$pushSpin(Entity entity, double x, double y, double z,
                                         float rotationYaw, float partialTicks,
                                         PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                         CallbackInfo ci) {
        tinkersnewlife$push(entity, partialTicks, poseStack);
    }

    /** ⭐ 兜底注入：直连 SRG 方法名（⭐ 绕开 refmap ✓ 照仓库既有写法 ✓） */
    @Inject(method = "m_114384_", at = @At("HEAD"), remap = false)
    private void tinkersnewlife$pushSpinSrg(Entity entity, double x, double y, double z,
                                            float rotationYaw, float partialTicks,
                                            PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                            CallbackInfo ci) {
        tinkersnewlife$push(entity, partialTicks, poseStack);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void tinkersnewlife$popSpin(Entity entity, double x, double y, double z,
                                        float rotationYaw, float partialTicks,
                                        PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                        CallbackInfo ci) {
        tinkersnewlife$pop(poseStack);
    }

    @Inject(method = "m_114384_", at = @At("RETURN"), remap = false)
    private void tinkersnewlife$popSpinSrg(Entity entity, double x, double y, double z,
                                           float rotationYaw, float partialTicks,
                                           PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                           CallbackInfo ci) {
        tinkersnewlife$pop(poseStack);
    }

    /**
     * ⭐ 该转就推一个旋转 ✓（⭐ 不取消任何东西 ✗ —— ⭐ 我们只是"在别人画之前先转一下" ✓）。
     */
    private void tinkersnewlife$push(Entity entity, float partialTicks, PoseStack poseStack) {
        // ⚠ 已经推过就不重复推 ✓（⭐ 一个方法同时被"名字"和"SRG"两条注入命中时会各调一次 ✓）
        if (Boolean.TRUE.equals(TNL_PUSHED.get())) {
            return;
        }
        try {
            if (!(entity instanceof Player player) || poseStack == null) {
                return;
            }
            if (!tinkersnewlife$spinning(player)) {
                return;
            }
            // ⭐ 用 partialTick 插值 ✓ —— 只按 tickCount 的话每 tick 才跳 90° ⇒ 看着一顿一顿 ✗
            float angle = ((player.tickCount + partialTicks) * DEG_PER_TICK) % 360.0F;
            poseStack.pushPose();
            poseStack.translate(0.0D, PIVOT_Y, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            poseStack.translate(0.0D, -PIVOT_Y, 0.0D);
            TNL_PUSHED.set(Boolean.TRUE);
        } catch (Throwable ignored) {
            // ⭐ 姿态出错绝不能崩客户端 ✗
        }
    }

    /** ⭐ 推过就必须还原 ✓ */
    private void tinkersnewlife$pop(PoseStack poseStack) {
        try {
            if (Boolean.TRUE.equals(TNL_PUSHED.get())) {
                TNL_PUSHED.set(Boolean.FALSE);
                if (poseStack != null) {
                    poseStack.popPose();
                }
            }
        } catch (Throwable ignored) {
            // ⭐ 同理 ✗
        }
    }

    /** ⭐ 这个玩家此刻该转吗 ✓ —— 看**手持物品的 NBT 信号** ✓（⭐ 主手优先 ✓ 再看副手 ✓） */
    private static boolean tinkersnewlife$spinning(Player player) {
        try {
            ItemStack main = player.getMainHandItem();
            if (LongShortBladeItem.isUltimateVisual(main)) {
                return true;
            }
            return LongShortBladeItem.isUltimateVisual(player.getOffhandItem());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
