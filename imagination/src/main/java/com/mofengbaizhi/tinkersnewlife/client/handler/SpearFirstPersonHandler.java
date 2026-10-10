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

        // §914 ★ **优先播动画文件** ✓：assets/tinkersnewlife/animations/spear_first_person.json ✓
        //   文件缺失/写坏 ⇒ 自动回退到下面 §913 的内置姿势 ✓（改文件改坏了不会崩 ✓ 最坏回到默认 ✓）。
        //   ⚠ 收回时时间**倒着走** ✓ ⇒ 同一条时间轴就能表达"刺出→收回" ✓ 不用写第二条 ✓。
        com.mofengbaizhi.tinkersnewlife.client.anim.AnimationClip clip =
                com.mofengbaizhi.tinkersnewlife.client.anim.AnimationClip.spearFirstPerson();
        if (clip != null) {
            // §918/§921：把动画**共轭到物品模型空间**去 ✓
            //   ★ M = H · D ✓（H = 原版手位移 ✓ D = 物品第一人称显示变换 ✓）
            //   —— 少了 H 就会像 §913/§845 那样"物品飞出去/看不见" ✗（事件在手位移**之前**触发 ✗）
            clip.applyItem(event.getPoseStack(), effective, arm,
                    baseTransform(stack, player, arm, event.getEquipProgress()));
            return;
        }

        // §921：**回退姿势也先补上原版手位移 H** ✓ —— 事件在手位移之前触发 ✗，
        //   不补的话就会重现 §845 那次"蓄力时手持看不到了"✗（物品被留在相机原点附近 ✗）。
        applyArmOffset(event.getPoseStack(), arm, event.getEquipProgress());
        SpearChargeAnimation.firstPersonSimple(event.getPoseStack(), arm, charge, attack);
    }

    /** 原版 {@code ItemInHandRenderer#applyItemArmTransform} ✓（= §921 里的 {@code H} ✓） */
    private static void applyArmOffset(com.mojang.blaze3d.vertex.PoseStack pose,
                                       net.minecraft.world.entity.HumanoidArm arm, float equipProgress) {
        int k = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
        pose.translate((double) ((float) k * 0.56F), (double) (-0.52F + equipProgress * -0.6F), -0.72D);
    }

    /**
     * 取"从 {@link RenderHandEvent} 那个空间 → 物品模型空间"的变换矩阵 ✓
     * ＝ <b>{@code H · D}</b> ✓（§921 修正：以前只乘了 {@code D} ✗ 少了手位移 ✗）。
     *
     * <p>两个来源（都在 Forge 补丁后的 {@code ItemInHandRenderer} 里 ✓ 我扒源码逐行对过 ✓）：
     * <ol>
     *   <li><b>H</b> = {@code applyItemArmTransform} ✓：
     *       {@code translate(k*0.56F, -0.52F + equipProgress*-0.6F, -0.72F)} ✓
     *       （§921 把 {@code UseAnim} 压成 {@code NONE} 后，原版只剩这一层 ✓ 三叉戟那套没了 ✓）；</li>
     *   <li><b>D</b> = 物品自己的 {@code firstperson_righthand} 显示变换 ✓
     *       （我们的长矛是 {@code rotation [0,-90,55]} / {@code scale 1.35} ✓ 见 §919 ✓）。</li>
     * </ol>
     * 有了它，动画按 {@code M·R·M⁻¹} 共轭后才真正作用在**物品模型自己的坐标系**里 ✓
     * ⇒ 和 Blockbench 预览一致 ✓（这才是"我做的动画说了算" ✓）。
     *
     * <p>⚠ 触发点在 {@code renderHandsWithItems} 第 316 行 ✓ 也就是**早于**
     * {@code renderArmWithItem}（317 行 ✓ 里面才做 H/S/D ✗）⇒ 这里必须自己把它们补进矩阵 ✓。
     */
    private static org.joml.Matrix4f baseTransform(ItemStack stack, LocalPlayer player,
                                                   net.minecraft.world.entity.HumanoidArm arm, float equipProgress) {
        com.mojang.blaze3d.vertex.PoseStack tmp = new com.mojang.blaze3d.vertex.PoseStack();
        int k = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
        tmp.translate((double) ((float) k * 0.56F), (double) (-0.52F + equipProgress * -0.6F), -0.72D);   // H ✓
        try {
            net.minecraft.client.resources.model.BakedModel model =
                    Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, 0);
            net.minecraft.world.item.ItemDisplayContext type =
                    arm == net.minecraft.world.entity.HumanoidArm.RIGHT
                            ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                            : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            net.minecraft.client.renderer.block.model.ItemTransform transform =
                    model.getTransforms().getTransform(type);
            transform.apply(arm == net.minecraft.world.entity.HumanoidArm.LEFT, tmp);                    // D ✓
        } catch (Throwable ignored) {
            // D 取不到也没关系 ✓ 至少 H 是对的 ✓（不会崩 ✓）
        }
        return new org.joml.Matrix4f(tmp.last().pose());
    }
}
