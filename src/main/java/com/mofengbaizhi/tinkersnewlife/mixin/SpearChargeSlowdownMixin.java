package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <b>长矛蓄力不减速</b>（§837）—— 客户端侧，直接把 1.20.1 写死的"使用物品 ×0.2"对长矛跳过。
 *
 * <h2>为什么必须客户端自己修（§836 试过"用终焉图书馆的组件"，用户实测还是减速 ✗）</h2>
 * 原版 1.21.11 长矛靠数据组件 {@code use_effects(speedMultiplier = 1.0, canSprint = true)}
 * ⇒ 不减速、可疾跑 ✓；终焉图书馆在 1.20.1 移植了同一套组件 ✓，
 * 但他们的组件管理器是<b>"缓存 + 只在解析过 NBT 之后才有值"</b> ✗（反编译实读 ✓）：
 * {@code endingLibrary$getComponentManagerIfPresent()} 直接返回缓存字段 ✗，
 * 而他们的客户端混入用的正是 {@code ifPresent(...)} ⇒ <b>只有那份 ItemStack 的 NBT 真被解析过才吃到</b> ✓；
 * 而 TConstruct 的工具每 tick 都在写自己的 {@code tic_*} NBT ✓ ⇒ 我们塞进根键 {@code Component} 的组件
 * 很容易在客户端那份栈上缺席（或被 {@code setTag} 重建冲掉 ✗）⇒ 用户实测<b>还是减速</b> ✗。
 * ⇒ 结论：<b>减速是纯客户端表现，就得在客户端解决</b> ✓ 不依赖任何数据同步 ✓。
 *
 * <h2>做法</h2>
 * {@code LocalPlayer#aiStep} 里只有两处 {@code isUsingItem()}（1.20.1 源码实读 ✓ L647 / L682）：
 * <ol>
 *   <li><b>L647 减速块</b>：{@code if (isUsingItem() && !isPassenger()) { leftImpulse *= 0.2F;
 *       forwardImpulse *= 0.2F; sprintTriggerTime = 0; }} ⇒ 长矛时让它不成立 ✓（不减速 ＋ 冲刺计时不清零 ✓）；</li>
 *   <li><b>L682 起跑判定</b>：{@code … && !this.isUsingItem() && …} ⇒ 长矛时也跳过 ✓
 *       （＝原版 {@code canSprint = true} ✓）。</li>
 * </ol>
 * 「正在使用」状态本身<b>完全不动</b> ✗ ⇒ 原版持握姿势（{@code UseAnim.SPEAR}）与冲锋状态机照旧 ✓。
 *
 * <h2>⚠ 每条注入写两份（本仓既有写法 ✓ 因为没有 Mixin 注解处理器 ✗ 不生成 refmap）</h2>
 * 开发环境用 official 名 ✓、生产环境用 SRG 字面量 ＋ {@code remap = false} ✓
 * （先例：{@code ItemRendererMixin} 就是 {@code "getModel"} ＋ {@code "m_174264_"} 各一条 ✓）。
 * SRG 名来源：{@code aiStep → m_8107_()V} ✓（多份 mod refmap 交叉验证 ✓）、
 * {@code isUsingItem → m_6117_()Z} ✓（Mojang official → obf → obf_to_srg 两步查得；
 * 同一套流程查 {@code aiStep} 得到的 {@code m_8107_} 与 refmap 完全一致 ⇒ 流程本身已验证 ✓）。
 *
 * <h2>🔎 诊断（"到底有没有生效"能查 ✓）</h2>
 * 第一次真正对长矛生效时打一条 INFO 日志：
 * {@code [长矛] 蓄力减速已跳过（客户端注入生效 §837）} ✓
 * ⇒ 用户再反馈"还是减速"时，看日志里有没有这句，就能立刻区分
 * "注入没生效 ✗" 与 "注入生效了但另有原因 ✗" ✓。
 */
@Mixin(LocalPlayer.class)
public abstract class SpearChargeSlowdownMixin {

    /** 只打一次日志 ✓ */
    @Unique
    private static boolean tinkersnewlife$loggedApplied = false;

    // ============================================================
    //  ① L647 减速块：长矛时跳过
    // ============================================================

    @Redirect(method = "aiStep",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean tinkersnewlife$noSlowdown$named(LocalPlayer self) {
        return tinkersnewlife$gate(self);
    }

    @Redirect(method = "m_8107_", remap = false,
            at = @At(value = "INVOKE", ordinal = 0, remap = false,
                    target = "Lnet/minecraft/client/player/LocalPlayer;m_6117_()Z"))
    private boolean tinkersnewlife$noSlowdown$srg(LocalPlayer self) {
        return tinkersnewlife$gate(self);
    }

    // ============================================================
    //  ② L682 起跑判定：长矛时跳过（＝原版 canSprint = true）
    // ============================================================

    @Redirect(method = "aiStep",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean tinkersnewlife$canSprint$named(LocalPlayer self) {
        return tinkersnewlife$gate(self);
    }

    @Redirect(method = "m_8107_", remap = false,
            at = @At(value = "INVOKE", ordinal = 1, remap = false,
                    target = "Lnet/minecraft/client/player/LocalPlayer;m_6117_()Z"))
    private boolean tinkersnewlife$canSprint$srg(LocalPlayer self) {
        return tinkersnewlife$gate(self);
    }

    // ============================================================
    //  共用闸门：手上"正在使用"的是长矛 ⇒ 只在这两处假装没在使用
    // ============================================================

    @Unique
    private static boolean tinkersnewlife$gate(LocalPlayer self) {
        boolean using = self.isUsingItem();
        if (using && self.getUseItem().getItem() instanceof SpearItem) {
            if (!tinkersnewlife$loggedApplied) {
                tinkersnewlife$loggedApplied = true;
                TinkersNewlife.LOGGER.info("[长矛] 蓄力减速已跳过（客户端注入生效 §837）");
            }
            return false;
        }
        return using;
    }
}
