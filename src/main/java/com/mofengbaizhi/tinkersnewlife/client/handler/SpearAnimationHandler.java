package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.SpearChargeAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>长矛蓄力动画的接线</b>（§844，客户端 ✓）。
 *
 * <p>为什么挂在 {@link RenderHandEvent}：这是 Forge 给的"正要渲染第一人称手/物品"的钩子 ✓
 * 拿到 {@code PoseStack} 就能照原版 {@code SpearAnimations.firstPersonUse} 那套数学施加位移与旋转 ✓
 * （数学在 {@link SpearChargeAnimation} ✓ 官方未混淆客户端反编译所得 ✓）。
 *
 * <p>配套：{@code SpearItem#getUseAnimation} 现在返回 {@code UseAnim.NONE} ✓
 * —— 1.20.1 对 {@code SPEAR} 的默认处理是"三叉戟那套端举"✗（用户：「太丑了」✗），
 * 关掉它、改由我们自己画 ✓（「正在使用」状态不变 ✓ 冲锋状态机也不受影响 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SpearAnimationHandler {

    private SpearAnimationHandler() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!SpearChargeAnimation.usingSpear(player)) return;

        // 按住多久了（tick ✓）—— 动画的四个阶段都由它推出来 ✓
        float timeHeld = player.getTicksUsingItem();
        SpearChargeAnimation.firstPersonUse(0.0F, event.getPoseStack(), timeHeld, player.getMainArm());
    }
}
