package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.item.WhipItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>鞭子第一人称"举鞭格挡"姿势</b>（§1067）—— 用户口径（2026-10-05）：
 * <blockquote>
 * 「<b>格挡时第一人称也应当适当地把物品往眼前放一下，而不是只有第三人称有举盾动画</b>」
 * </blockquote>
 *
 * <h2>接入点与坑（照本仓长矛/弹弓那两套 ✓）</h2>
 * <ul>
 *   <li>挂 {@link RenderHandEvent}（渲染手部物品**之前** ✓）；</li>
 *   <li>⚠ 该事件**早于**原版的手位移 ✗ ⇒ 必须自己补上
 *       {@code H = translate(k*0.56, -0.52 + equipProgress*-0.6, -0.72)} ✓
 *       （不补就会重现本仓 §845 那种"手持物品飞出/看不见"✗ ✓ ——
 *       长矛 {@code SpearFirstPersonHandler} 与弹弓 {@code SlingshotFirstPersonHandler} 里都写着这一条 ✓）；</li>
 *   <li>补完 {@code H} 之后，姿势就作用在**手空间**：<b>X 右 ✓ Y 上 ✓ −Z 前（朝视线）✓</b> ⇒
 *       把鞭子<b>往视线中央、往上、往前</b>挪一点 ✓ 再抬一下角度 ✓ = "往眼前放一下" ✓。</li>
 * </ul>
 *
 * <h2>时序</h2>
 * <ul>
 *   <li><b>举着格挡时</b>：进度 {@code p} 在 {@link #RAISE_TICKS} tick 内 0→1 缓入 ✓（不生硬 ✓）；</li>
 *   <li><b>松手 / 完美格挡收势后</b>：用**物品冷却百分比**把 {@code p} 落回 0 ✓ ——
 *       只取冷却的前 {@link #UNWIND_FRACTION} 段（0.25 ✓）⇒ 0.25~0.5 秒内自然放下 ✓
 *       （不这么做会跟着 2 秒冷却慢慢落 ✗ 太拖 ✗）；</li>
 *   <li>其它物品 / 副手 / 不在用且不在冷却 ⇒ **一概不插手** ✓（零副作用 ✓）。</li>
 * </ul>
 *
 * <h2>想调手感就改这几个常量 ✓</h2>
 * {@link #RAISE_TICKS}（抬起来多快 ✓）、{@link #TOWARD_CENTER}（往视线中央挪多少 ✓）、
 * {@link #RAISE_UP}（往上抬多少 ✓）、{@link #RAISE_FORWARD}（往前送多少 ✓）、
 * {@link #RAISE_PITCH_DEG}（仰角 ✓）、{@link #RAISE_YAW_DEG}（偏航 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class WhipFirstPersonHandler {

    /** 举起来用多久（tick ✓ 5 tick ＝ 0.25 秒 ✓ 够快但不生硬 ✓） */
    private static final float RAISE_TICKS = 5.0F;
    /** 放下只用冷却的前百分之多少（✓ 0.25 ⇒ 冷却前 1/4 段放下 ✓） */
    private static final float UNWIND_FRACTION = 0.25F;
    /** 往视线中央挪多少（格 ✓ —— 手在 x ＝ ±0.56 ✓，往中间收一点才像"举到眼前" ✓） */
    private static final float TOWARD_CENTER = 0.26F;
    /** 往上抬多少（格 ✓） */
    private static final float RAISE_UP = 0.16F;
    /** 往前送多少（格 ✓，−Z 是前方 ✓） */
    private static final float RAISE_FORWARD = 0.10F;
    /** 仰角（度 ✓ 负值 ⇒ 鞭身前段往上抬 ✓） */
    private static final float RAISE_PITCH_DEG = -28.0F;
    /** 偏航（度 ✓ 让鞭子略微转向视线中央 ✓） */
    private static final float RAISE_YAW_DEG = -10.0F;

    private WhipFirstPersonHandler() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WhipItem)) {
            return;                                   // 不是鞭子 ⇒ 一概不插手 ✓
        }

        boolean using = player.isUsingItem()
                && player.getUseItem().is(ModItems.WHIP.get())
                && player.getUsedItemHand() == event.getHand();

        float progress;
        if (using) {
            float held = player.getTicksUsingItem() + event.getPartialTick();
            progress = smooth(Mth.clamp(held / RAISE_TICKS, 0.0F, 1.0F));
        } else {
            // 松手后：用物品冷却百分比倒着放下 ✓（只取冷却前 UNWIND_FRACTION 段 ✓）
            if (!player.getCooldowns().isOnCooldown(stack.getItem())) {
                return;                               // 既没用着也没在收势 ⇒ 不插手 ✓
            }
            float remaining = Mth.clamp(
                    player.getCooldowns().getCooldownPercent(stack.getItem(), event.getPartialTick()), 0.0F, 1.0F);
            progress = 1.0F - smooth(Mth.clamp(remaining / UNWIND_FRACTION, 0.0F, 1.0F));
        }
        if (progress <= 1.0E-4F) {
            return;
        }

        // ⚠ 事件在手位移**之前** ⇒ 先补上原版 H ✓（否则物品会飞/看不见 ✗）
        net.minecraft.world.entity.HumanoidArm arm =
                event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        applyArmOffset(event.getPoseStack(), arm, event.getEquipProgress());

        // 手空间里：X 右 ✓ Y 上 ✓ −Z 前 ✓ ⇒ 往中央、往上、往前 + 抬角 ✓
        float k = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PoseStack pose = event.getPoseStack();
        pose.translate(-k * TOWARD_CENTER * progress,
                RAISE_UP * progress,
                -RAISE_FORWARD * progress);
        pose.mulPose(Axis.XP.rotationDegrees(RAISE_PITCH_DEG * progress));
        pose.mulPose(Axis.YP.rotationDegrees(k * RAISE_YAW_DEG * progress));
    }

    /** 原版 {@code ItemInHandRenderer#applyItemArmTransform} ✓（就是上面说的 H ✓） */
    private static void applyArmOffset(PoseStack pose, HumanoidArm arm, float equipProgress) {
        int k = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate((double) ((float) k * 0.56F), (double) (-0.52F + equipProgress * -0.6F), -0.72D);
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
