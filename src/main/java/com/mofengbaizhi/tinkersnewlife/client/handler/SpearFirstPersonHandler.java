package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.SpearChargeAnimation;
import com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>长矛第一人称动画的接入点</b>（§911）。
 *
 * <p>⚠ 之前 {@link SpearChargeAnimation#firstPersonUse} 这套数学**移植过来了但一个调用点都没有** ✗
 * （仓库里 grep 只有定义 ✓ 第三人称那边有 mixin ✓）—— 所以第一人称**从来没动过** ✗。
 * 这轮补上：挂 {@link RenderHandEvent}（渲染手部物品之前 ✓）把官方那套姿势套到手的 PoseStack 上 ✓。
 *
 * <p>触发条件（两个都覆盖 ✓）：
 * <ul>
 *   <li><b>正在使用</b>（右键按住 ✓）⇒ {@code timeHeld = getTicksUsingItem()} ✓ 蓄力→刺出 ✓；</li>
 *   <li><b>刚松手</b>（主手仍是长矛且冷却在跑 ✓）⇒ 用冷却百分比当收回进度 ✓ ⇒ 姿势 unwind ✓。</li>
 * </ul>
 * 其它物品/其它手一概不动 ✓；不是我们的长矛就直接返回 ✓（零副作用 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class SpearFirstPersonHandler {

    private SpearFirstPersonHandler() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SpearItem)) return;

        boolean using = player.isUsingItem()
                && player.getUseItem().getItem() instanceof SpearItem
                && player.getUsedItemHand() == event.getHand();
        float retract = 0.0F;
        if (!using) {
            if (!player.getCooldowns().isOnCooldown(stack.getItem())) return;   // 既没在用也没在收回 ⇒ 不插手
            retract = player.getCooldowns().getCooldownPercent(stack.getItem(), event.getPartialTick());
        }

        // ⚠ RenderHandEvent 给的是 InteractionHand ✗ 不是 HumanoidArm ✓ ⇒ 按主手转一下 ✓
        net.minecraft.world.entity.HumanoidArm arm = event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();

        // §913 **保守限幅**姿势（先保证看得见 ✓）
        //   蓄力进度：0 → 1 用 RAISE_END tick ✓；刺出进度：RAISE_END → ATTACK_END ✓
        float held = using ? player.getTicksUsingItem() : 0.0F;
        // 收回（松手后 ✓）：有效时间从刺出末端倒着走 ✓ ⇒ 两个进度一起回落 ✓ 姿势自然 unwind ✓
        float effective = using ? held
                : SpearChargeAnimation.ATTACK_END * (1.0F - net.minecraft.util.Mth.clamp(retract, 0.0F, 1.0F));
        float charge = net.minecraft.util.Mth.clamp(effective / SpearChargeAnimation.RAISE_END, 0.0F, 1.0F);
        float attack = net.minecraft.util.Mth.clamp(
                (effective - SpearChargeAnimation.RAISE_END)
                        / (SpearChargeAnimation.ATTACK_END - SpearChargeAnimation.RAISE_END), 0.0F, 1.0F);

        SpearChargeAnimation.firstPersonSimple(event.getPoseStack(), arm, charge, attack);
    }
}
