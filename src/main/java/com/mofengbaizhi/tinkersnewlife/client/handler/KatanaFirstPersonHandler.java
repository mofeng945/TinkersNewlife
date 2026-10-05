package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.anim.AnimationClip;
import com.mofengbaizhi.tinkersnewlife.content.item.KatanaItem;
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
import org.joml.Matrix4f;

/**
 * <b>拔刀剑第一人称动画的接入点</b>（§1002）：<b>居合蓄力姿势</b>。
 *
 * <p>用户口径与计划见 §996 的 P3-3 ✓：复用长矛/弹弓那套（{@code AnimationClip} 动画文件 ＋
 * {@link RenderHandEvent} ＋ {@code H·D} 矩阵共轭 ✓）。
 *
 * <h2>两个阶段</h2>
 * <ol>
 *   <li><b>蓄力</b>（右键长按 ✓）：按 {@code getTicksUsingItem()} 采样动画 ⇒ 刀**抬起并后引**
 *       （动画文件 {@code tnl_anim/katana_firstperson.animation.json} ✓ 在 0.5 秒内完成、之后保持 ✓），
 *       并按蓄力进度**叠一个手空间小抖动**（越蓄越明显 ✓）；</li>
 *   <li><b>收招</b>（松开后进入冷却 ✓）：用**冷却百分比**把蓄力姿势**倒着放回去** ✓
 *       ⇒ 同一条时间轴就表达了"抬起 → 斩出 → 回位" ✓ 不用写第二条动画 ✓。</li>
 * </ol>
 *
 * <p>⚠ 与长矛/弹弓同一套数学：事件在**手位移之前**触发 ⇒ 必须自己补 {@code H（手位移）· D（物品显示变换）} ✓
 * 否则会重现 §845 那种"蓄力时手持看不见/飞出去" ✗。动画文件缺失 ⇒ 本类**不插手** ✓（保持原版 ✓ 不崩 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class KatanaFirstPersonHandler {

    private KatanaFirstPersonHandler() {}

    /** 蓄满参考时长（tick ✓ 只用于抖动强度曲线 ✓） */
    private static final float FULL_CHARGE_TICKS = 20.0F;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof KatanaItem)) return;

        boolean using = player.isUsingItem()
                && player.getUseItem().getItem() instanceof KatanaItem
                && player.getUsedItemHand() == event.getHand();

        float charge;
        if (using) {
            charge = player.getTicksUsingItem() + event.getPartialTick();
        } else if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            // 收招：冷却越走完 ⇒ 姿势越回到初始 ✓（同一条时间轴倒放 ✓）
            float left = Mth.clamp(player.getCooldowns()
                    .getCooldownPercent(stack.getItem(), event.getPartialTick()), 0.0F, 1.0F);
            charge = FULL_CHARGE_TICKS * (1.0F - left);
        } else {
            return;   // 既没蓄力也没在收招 ⇒ 完全不插手 ✓
        }

        AnimationClip clip = AnimationClip.katanaFirstPerson();
        if (clip == null) return;   // 动画文件没读到 ⇒ 保持原版手持 ✓

        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        PoseStack pose = event.getPoseStack();
        float time = Math.min(Math.max(0.0F, charge), Math.max(0.1F, clip.length()));
        clip.applyItem(pose, time, arm, baseTransform(stack, player, arm, event.getEquipProgress()));

        // ⭐ 蓄力抖动：手空间一个小角度（越蓄越大 ✓ 斩出后随收招自然消失 ✓）
        float progress = Mth.clamp(charge / FULL_CHARGE_TICKS, 0.0F, 1.0F);
        float amplitude = progress * 1.5F;
        if (amplitude > 0.01F && using) {
            pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(charge * 2.2D) * amplitude));
        }
    }

    /**
     * 从 {@link RenderHandEvent} 的空间 → 物品模型空间的变换矩阵 ＝ {@code H · D} ✓
     * （与 {@code SpearFirstPersonHandler} / {@code SlingshotFirstPersonHandler} 里那两份逐行相同 ✓）。
     */
    private static Matrix4f baseTransform(ItemStack stack, LocalPlayer player, HumanoidArm arm, float equipProgress) {
        PoseStack tmp = new PoseStack();
        int k = arm == HumanoidArm.RIGHT ? 1 : -1;
        tmp.translate((double) ((float) k * 0.56F), (double) (-0.52F + equipProgress * -0.6F), -0.72D);   // H
        try {
            net.minecraft.client.resources.model.BakedModel model =
                    Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, 0);
            net.minecraft.world.item.ItemDisplayContext type =
                    arm == HumanoidArm.RIGHT
                            ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                            : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            net.minecraft.client.renderer.block.model.ItemTransform transform =
                    model.getTransforms().getTransform(type);
            transform.apply(arm == HumanoidArm.LEFT, tmp);                                                // D
        } catch (Throwable ignored) {
            // D 取不到也没关系，至少 H 是对的
        }
        return new Matrix4f(tmp.last().pose());
    }
}
