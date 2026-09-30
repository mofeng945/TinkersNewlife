package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.SpearChargeAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * <b>长矛蓄力动画接线（§846：矩阵补偿版 ✓）</b>。
 *
 * <h2>为什么需要"补偿"</h2>
 * 1.20.1 的 Forge {@link RenderHandEvent} 在 {@code GameRenderer#renderItemInHand} 里触发 ✓
 * ⇒ 它在**所有手部/物品变换之前** ✗；而原版 1.21.11 的动画数学
 * （{@code SpearAnimations.firstPersonUse} ✓）是**接在它自己的持矛基准之上**的 ✓
 * ⇒ 直接施加会落在错误坐标系里 ⇒ §844 那次实测「**手持看不见了**」✗。
 *
 * <h2>做法：把"原版基准"换成"高版本基准"</h2>
 * 设（同一局部坐标系、矩阵右乘）：
 * <ul>
 *   <li>{@code S} ＝ 1.20.1 自己 {@code case SPEAR} 的那套 ✗（＝用户嫌丑的"三叉戟端举"✗）；</li>
 *   <li>{@code D} ＝ 1.21.11 的 {@code case SPEAR}：{@code translate(k*0.56, -0.52, -0.72)}
 *       ＋ {@code SpearAnimations.firstPersonUse(...)} ✓；</li>
 *   <li>最终姿势 ＝ {@code S ∘ U}（S 由原版施加、U 由我们施加 ✓）⇒ 要它等于 {@code D}
 *       ⇒ <b>{@code U = S⁻¹ ∘ D}</b> ✓。</li>
 * </ul>
 * 常量**全部来自反编译源码** ✓（1.20.1 与 1.21.11 的 {@code ItemInHandRenderer} ✓）
 * ⇒ 观感与高版本一致 ✓ 且**不动匠魂占用的 {@code IClientItemExtensions}** ✗（风险最低 ✓）。
 *
 * <p>⚠ 已知近似（如实说明 ✓）：① 1.20.1 那段 {@code case SPEAR} 里的小抖动
 * （幅度 0.004 ✓）**忽略** ✗；其 {@code p = timeHeld/10} 对我们的 72000 时长已夹到 1 ✓
 * 故 {@code translate(0,0,0.2)}／{@code scale(1,1,1.2)} 按 p=1 算 ✓；
 * ② {@code equipProgress} 视为 1 ⇒ 1.20.1 的 {@code applyItemArmTransform}
 * 与 1.21.11 的基准位移**数值相同**（都是 {@code (k*0.56, -0.52, -0.72)} ✓）⇒ 无需额外补偿 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SpearFirstPersonAnimationHandler {

    private SpearFirstPersonAnimationHandler() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !SpearChargeAnimation.usingSpear(player)) return;

        int k = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float timeHeld = player.getTicksUsingItem();

        // ── D：高版本 1.21.11 的姿势（基准位移 ＋ SpearAnimations）──────────────
        PoseStack dStack = new PoseStack();
        dStack.translate(k * 0.56F, -0.52F, -0.72F);
        SpearChargeAnimation.firstPersonUse(0.0F, dStack, timeHeld, player.getMainArm());
        Matrix4f d = new Matrix4f(dStack.last().pose());

        // ── S：1.20.1 自己的 case SPEAR（p 已夹到 1，抖动忽略）──────────────────
        Matrix4f s = new Matrix4f();
        s.translate(k * -0.5F, 0.7F, 0.1F);
        s.rotate(Axis.XP.rotationDegrees(-55.0F));
        s.rotate(Axis.YP.rotationDegrees(k * 35.3F));
        s.rotate(Axis.ZP.rotationDegrees(k * -9.785F));
        s.translate(0.0F, 0.0F, 0.2F);
        s.scale(1.0F, 1.0F, 1.2F);
        s.rotate(Axis.YN.rotationDegrees(k * 45.0F));

        // ── 补偿：U = S⁻¹ ∘ D（右乘到当前姿势 ⇒ 原版随后乘 S ⇒ 结果正好是 D）──
        Matrix4f u = new Matrix4f(s).invert().mul(d);
        event.getPoseStack().last().pose().mul(u);
    }
}
