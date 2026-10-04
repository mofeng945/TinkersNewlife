package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.anim.AnimationClip;
import com.mofengbaizhi.tinkersnewlife.client.renderer.SpearChargeAnimation;
import com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>第三人称：让动画文件驱动"手里的长矛"</b>（§934）。
 *
 * <h2>为什么需要它</h2>
 * 用户口径：「**未蓄力时的手持状态维持正常就好**」✓ —— 但物品的
 * {@code display}（{@code thirdperson_*} 那套）是**静态**的 ✗（只按显示场景固化 ✗
 * 跟"在不在蓄力"无关 ✗）⇒ §931/§932 直接改它，待机手持也跟着变 ✗。
 * 正解：**空闲走原版** ✓（我们一行都不动 ✓），**蓄力/收回**才在渲染入口叠一个动画姿态 ✓
 * ⇒ 天然分状态 ✓。
 *
 * <h2>注入点</h2>
 * {@code ItemInHandRenderer#renderItem(LivingEntity; ItemStack; ItemDisplayContext; Z; PoseStack; …)}
 * 的 <b>HEAD</b> ✓ —— 这个方法第一人称/第三人称都走 ✓ ⇒ 用 {@code ctx == THIRD_PERSON_*} 过滤 ✓。
 * 此处 PoseStack = **手把物品挂上去之后**的空间 ✓，紧接着 {@code ItemRenderer}
 * 会把**显示变换 D** 乘上去 ✓ ⇒ 我在这里按 {@code M·R·M⁻¹}（{@code M = D}）共轭 ✓
 * ⇒ 动画里 {@code item_third} 骨骼的旋转就等于"在**物品模型自己的坐标系**里转" ✓
 * （和第一人称同一套手法 ✓ 见 §921）。
 *
 * <h2>骨骼名</h2>
 * {@code item_third} ✓ —— 和第一人称的 {@code item} **分开** ✓（两边互不干扰 ✓
 * 也符合用户"第一/第三人称分开做动画"的做法 ✓）。
 *
 * <h2>⚠ 双份注解（本仓惯例 ✓ 没有 Mixin 注解处理器 ✗ 不生成 refmap）</h2>
 * named（开发用 ✓）＋ SRG 字面量 {@code remap = false}（生产用 ✓）。
 * SRG 名来源：官方映射表
 * {@code srg_to_official_1.20.1.tsrg} ✓ 查到
 * {@code renderItem (LivingEntity;ItemStack;ItemDisplayContext;ZPoseStack;MultiBufferSource;I)V → m_269530_} ✓。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SpearThirdPersonItemMixin {

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void tinkersnewlife$thirdPersonSpearItem(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx,
                                                     boolean leftHand, PoseStack pose, MultiBufferSource buffer,
                                                     int light, CallbackInfo ci) {
        tinkersnewlife$apply(entity, stack, ctx, pose);
    }

    @Inject(method = "m_269530_(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false, at = @At("HEAD"))
    private void tinkersnewlife$thirdPersonSpearItemSrg(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx,
                                                        boolean leftHand, PoseStack pose, MultiBufferSource buffer,
                                                        int light, CallbackInfo ci) {
        tinkersnewlife$apply(entity, stack, ctx, pose);
    }

    /** 共用：只有"第三人称 ＋ 我们的长矛 ＋ 正在蓄力/收回"才叠动画 ✓ 其它一概不动 ✓（待机就是原版 ✓） */
    private static void tinkersnewlife$apply(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx,
                                             PoseStack pose) {
        if (!(stack.getItem() instanceof SpearItem)) return;
        boolean thirdPerson = ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!thirdPerson) return;

        boolean using = SpearChargeAnimation.usingSpear(entity);
        float retract = 0.0F;
        if (!using) {
            // 收回阶段（主手仍是长矛 ＋ 冷却在跑 ✓ 和第一/第三人称手臂同一口径 ✓）
            if (!SpearChargeAnimation.holdingSpear(entity)) return;
            if (!(entity instanceof Player player) || !player.getCooldowns().isOnCooldown(stack.getItem())) return;
            retract = player.getCooldowns().getCooldownPercent(stack.getItem(), 0.0F);
        }

        AnimationClip clip = AnimationClip.spearAnimation();
        if (clip == null || !clip.hasItemThird()) return;

        float effective = using
                ? entity.getTicksUsingItem()
                : SpearChargeAnimation.ATTACK_END * (1.0F - Mth.clamp(retract, 0.0F, 1.0F));
        HumanoidArm arm = ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        // ★ §935：先让"蓄力时的显示变换"**精确等于** §931/§932 那套 ✓（替换 ✓ 不是叠加 ✗）
        tinkersnewlife$applyChargeTransform(pose, stack, entity, ctx);
        // 再允许动画文件额外叠一点（`item_third` ✓ 没写/全 0 就等于没叠 ✓）
        clip.applyItemThird(pose, effective, arm, tinkersnewlife$displayTransform(stack, entity, ctx));
    }

    // ── §935 蓄力时"想要"的第三人称显示变换 ────────────────────────────────────
    //   来源：§931/§932（用户认可过的朝向/位置 ✓）——
    //   `rotation [-90,-90,55]`（X 从 0 改成 −90 ⇒ 平面贴图的矛尖转出平面指向前方 ✓）
    //   `translation [0,-2.0,-3.5]`（原来继承的是 [0,4.0,0.5] ✓ 下移 0.375 格 ＋ 前推 0.25 格 ✓）
    //   `scale 0.85`（= `forge:item/default-tool` 第三人的值 ✓ 别丢 ✗）
    private static final float[] CHARGE_ROTATION = {-90.0F, -90.0F, 55.0F};
    /** ⚠ 单位与 json 里的 `translation` 一致（1/16 格 ✓）—— 这里手动 /16 换成方块 ✓ */
    private static final float[] CHARGE_TRANSLATION = {0.0F, -2.0F, -3.5F};
    private static final float CHARGE_SCALE = 0.85F;

    /**
     * 把 {@code pose} 乘上 {@code E = M_new · M_old⁻¹} ✓
     * ⇒ 之后 {@code ItemRenderer} 再乘原版显示变换 {@code M_old} 时，净效果就是 {@code M_new} ✓
     * ⇒ **精确替换**成蓄力时想要的那份 ✓（用户口径：不要"又叠一层" ✗）。
     *
     * <p>⚠ `M_old` 用 `ItemTransform#apply` 取 ✓（与游戏同一套数学 ✓ 含 Forge 的 `rightRotation` ✓
     * 以及左手镜像 ✓）；`M_new` 用手写常量构造 ✓（左手镜像同样交给 `apply(leftHand,…)` ✓
     * ⇒ 传**右手**的值即可 ✓ 镜像后正好等于 json 里左手那份 `[-90,90,-55]` ✓）。
     */
    private static void tinkersnewlife$applyChargeTransform(PoseStack pose, ItemStack stack, LivingEntity entity,
                                                            ItemDisplayContext ctx) {
        try {
            boolean leftHand = ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            BakedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, entity.level(), entity, 0);
            ItemTransform oldTransform = model.getTransforms().getTransform(ctx);

            PoseStack oldStack = new PoseStack();
            oldTransform.apply(leftHand, oldStack);

            PoseStack newStack = new PoseStack();
            new ItemTransform(
                    new org.joml.Vector3f(CHARGE_ROTATION[0], CHARGE_ROTATION[1], CHARGE_ROTATION[2]),
                    new org.joml.Vector3f(CHARGE_TRANSLATION[0] / 16.0F,
                            CHARGE_TRANSLATION[1] / 16.0F, CHARGE_TRANSLATION[2] / 16.0F),
                    new org.joml.Vector3f(CHARGE_SCALE, CHARGE_SCALE, CHARGE_SCALE),
                    new org.joml.Vector3f(0.0F, 0.0F, 0.0F)).apply(leftHand, newStack);

            Matrix4f delta = new Matrix4f(newStack.last().pose())
                    .mul(new Matrix4f(oldStack.last().pose()).invert());
            pose.last().pose().mul(delta);
        } catch (Throwable ignored) {
            // 取不到就什么都不做 ✓（退化成原版 + item_third ✓ 不崩 ✓）
        }
    }

    /** 取该物品第三人称的显示变换矩阵 ✓（{@code M} ✓；取不到返回 null ⇒ 退化成"直接加法" ✓ 不崩 ✓） */
    private static Matrix4f tinkersnewlife$displayTransform(ItemStack stack, LivingEntity entity,
                                                            ItemDisplayContext ctx) {
        try {
            BakedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, entity.level(), entity, 0);
            ItemTransform transform = model.getTransforms().getTransform(ctx);
            PoseStack tmp = new PoseStack();
            transform.apply(ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND, tmp);
            return new Matrix4f(tmp.last().pose());
        } catch (Throwable t) {
            return null;
        }
    }
}
