package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.anim.AnimationClip;
import com.mofengbaizhi.tinkersnewlife.content.item.SlingshotItem;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * <b>弹弓第一人称动画的接入点</b>（§988）：<b>拉弓颤抖</b>。
 *
 * <p>用户口径：「**只保留拉弓蓄力骨骼动画**」＋「**动画就按拉弓颤抖骨骼动画做**」✓
 * ⇒ 蓄力阶段那套"换模型"（``pulling_1/2/3``）**已删除** ✗，改成**一条骨骼动画**：
 * 拉弓期间 {@code item} 骨骼高频小幅抖动 ✓，并且**越拉越抖**（播放速度随蓄力进度提高 ✓）。
 *
 * <p>动画文件：{@code assets/tinkersnewlife/tnl_anim/slingshot_firstperson.animation.json} ✓
 * （在 Blockbench 里改 ✓ 改完 **F3+T** 即时生效 ✓ 不用重编译 ✓ 文件名随便 ✓ 见 {@code AnimationClip} ✓）；
 * 文件缺失/写坏 ⇒ **本类直接不插手** ✓（保持原样 ✓ 不崩 ✓）。
 *
 * <p>⚠ 与长矛同一套数学：{@code RenderHandEvent} 在**手位移之前**触发 ⇒ 必须自己把
 * {@code H（手位移）· D（物品显示变换）} 补进矩阵 ✓ 否则动画会像 §845 那样"物品飞出屏幕" ✗。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class SlingshotFirstPersonHandler {

    private SlingshotFirstPersonHandler() {}

    /** 拉满参考时长（tick ✓ 只用于"颤抖强度曲线" ✓ 与工具面板的 Draw Speed 无关 ✓） */
    private static final float FULL_DRAW_TICKS = 20.0F;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SlingshotItem)) return;

        // 只在"正在拉弓"时抖 ✓（松手 / 其它物品 / 副手 ⇒ 完全不插手 ✓）
        boolean using = player.isUsingItem()
                && player.getUseItem().getItem() instanceof SlingshotItem
                && player.getUsedItemHand() == event.getHand();
        if (!using) return;

        AnimationClip clip = AnimationClip.slingshotFirstPerson();
        if (clip == null) return;   // 动画文件没读到 ⇒ 不插手（保持原版手持 ✓）

        float held = player.getTicksUsingItem() + event.getPartialTick();
        float progress = Mth.clamp(held / FULL_DRAW_TICKS, 0.0F, 1.0F);
        // ⭐ 越拉越抖：播放速度随蓄力提高 ✓ 时间在动画长度内回绕 ✓
        float speed = 0.8F + progress * 1.8F;
        float length = Math.max(0.05F, clip.length());
        float time = (held * speed) % length;

        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        clip.applyItem(event.getPoseStack(), time, arm,
                baseTransform(stack, player, arm, event.getEquipProgress()));
    }

    /**
     * 从 {@link RenderHandEvent} 的空间 → 物品模型空间的变换矩阵 ＝ <b>{@code H · D}</b> ✓
     * （与 {@code SpearFirstPersonHandler} 里那份完全一致 ✓ 逐行相同 ✓）。
     */
    private static Matrix4f baseTransform(ItemStack stack, LocalPlayer player, HumanoidArm arm, float equipProgress) {
        PoseStack tmp = new PoseStack();
        int k = arm == HumanoidArm.RIGHT ? 1 : -1;
        tmp.translate((double) ((float) k * 0.56F), (double) (-0.52F + equipProgress * -0.6F), -0.72D);   // H ✓
        try {
            net.minecraft.client.resources.model.BakedModel model =
                    Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, 0);
            net.minecraft.world.item.ItemDisplayContext type =
                    arm == HumanoidArm.RIGHT
                            ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                            : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            net.minecraft.client.renderer.block.model.ItemTransform transform =
                    model.getTransforms().getTransform(type);
            transform.apply(arm == HumanoidArm.LEFT, tmp);                                                // D ✓
        } catch (Throwable ignored) {
            // D 取不到也没关系 ✓ 至少 H 是对的 ✓ 不会崩 ✓
        }
        return new Matrix4f(tmp.last().pose());
    }
}
