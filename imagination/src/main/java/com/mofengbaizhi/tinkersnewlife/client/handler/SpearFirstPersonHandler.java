package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.anim.AnimationClip;
import com.mofengbaizhi.tinkersnewlife.client.renderer.SpearChargeAnimation;
import com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** First-person right-click pose. Vanilla still renders the hand and the tool. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class SpearFirstPersonHandler {
    private SpearFirstPersonHandler() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND) return;
        float time = SpearChargeAnimation.visualTime(player, event.getPartialTick());
        ItemStack stack = event.getItemStack();
        if (time <= 0.0F || !(stack.getItem() instanceof SpearItem)) return;

        HumanoidArm arm = player.getMainArm();
        AnimationClip clip = AnimationClip.spearFirstPerson();
        if (clip != null) {
            PoseStack delta = new PoseStack();
            SpearChargeAnimation.firstPersonStroke(delta, time);
            clip.applyItem(delta, time, arm, baseTransform(stack, player, arm, event.getEquipProgress()));
            Matrix4f blended = SpearChargeAnimation.blendTransform(new Matrix4f(), delta.last().pose(),
                    SpearChargeAnimation.visualWeight(time));
            SpearChargeAnimation.applyTransform(event.getPoseStack(), blended);
            return;
        }

        float charge = Mth.clamp(time / SpearChargeAnimation.RAISE_END, 0.0F, 1.0F);
        float attack = Mth.clamp((time - SpearChargeAnimation.RAISE_END)
                / (SpearChargeAnimation.ATTACK_END - SpearChargeAnimation.RAISE_END), 0.0F, 1.0F);
        // Vanilla applies the grip offset after the event; fallback must become identity at time zero.
        SpearChargeAnimation.firstPersonSimple(event.getPoseStack(), arm,
                SpearChargeAnimation.visualWeight(time) * charge, attack);
    }

    /** H · D: vanilla's hand offset times the model's first-person display transform. */
    private static Matrix4f baseTransform(ItemStack stack, LocalPlayer player, HumanoidArm arm, float equipProgress) {
        PoseStack tmp = new PoseStack();
        boolean left = arm == HumanoidArm.LEFT;
        tmp.translate(left ? -0.56F : 0.56F, -0.52F - 0.6F * equipProgress, -0.72D);
        try {
            var model = Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, 0);
            var context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            model.getTransforms().getTransform(context).apply(left, tmp);
        } catch (RuntimeException ignored) {
            // Unknown model transform: keep the vanilla hand offset.
        }
        Matrix4f matrix = tmp.last().pose();
        return Math.abs(matrix.determinant()) > 1.0E-6F ? matrix : null;
    }
}
