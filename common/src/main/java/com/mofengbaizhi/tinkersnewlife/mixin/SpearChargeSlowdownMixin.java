package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <b>长矛蓄力不减速</b>（§837）—— 客户端侧，让 1.20.1 写死的"使用物品 ×0.2"对长矛跳过。
 *
 * <h2>⚠⚠ TNL1303 与 Iron 法术书库的 <b>@Redirect 硬冲突</b>（玩家反馈：启动崩溃）</h2>
 * 玩家日志原文：
 * <pre>
 * @Redirect conflict. Skipping irons_lib.mixins.json:LocalPlayerMixin-&gt;
 *   @Redirect::irons_lib$chargeIsNotASlowUse(LocalPlayer;)Z with priority 1000,
 *   already redirected by tinkersnewlife.mixins.json:SpearChargeSlowdownMixin-&gt;
 *   @Redirect::tinkersnewlife$noSlowdown$srg(LocalPlayer;)Z with priority 1000
 * Caused by: InjectionError: Critical injection failure: Redirector irons_lib$chargeIsNotASlowUse
 *   ... failed injection check, (0/1) succeeded
 * </pre>
 * 两个模组抢 `LocalPlayer#aiStep` 里**同一个 `isUsingItem()` 调用点**：
 * 后到的一方被框架跳过，而 `@Redirect` 默认 `require = 1`
 * ⇒ 被跳过的一方直接 `InjectionError` ⇒ 启动阶段崩（连崩溃报告都写不完整）。
 *
 * <h3>修法：本 Mod 主动让路</h3>
 * <ol>
 *   <li>优先级降到 **900**（低于 irons_lib 的 1000）⇒ 同一点上它先占位；</li>
 *   <li>两个 {@code @Redirect} 全部 **{@code require = 0}** ⇒ 抢不到时**静默跳过**，
 *       <b>绝不会再把别的模组拖崩</b> ✓（这正是本次崩溃的根因）；</li>
 *   <li>⚠ 代价（如实记）：**装了 Iron 法术书库的整合包里，"长矛蓄力不减速"会失效**
 *       （那个调用点被 irons_lib 占用）。宁可少一个手感特性，也不能让游戏起不来 ✓。
 *       两边都要活只能等 irons_lib 改成 `@WrapOperation` 或降优先级。</li>
 * </ol>
 *
 * <h2>⚠ 每条注入写两份（本仓既有写法 ✓ 没有 Mixin 注解处理器 ✗ 不生成 refmap）</h2>
 * 开发环境用 official 名、生产环境用 SRG 字面量 ＋ {@code remap = false}。
 */
@Mixin(value = LocalPlayer.class, priority = 900)
public abstract class SpearChargeSlowdownMixin {

    /** 只打一次日志 ✓ */
    @Unique
    private static boolean tinkersnewlife$loggedApplied = false;

    // ============================================================
    //  ① L647 减速块：长矛时跳过
    // ============================================================

    @Redirect(method = "aiStep", require = 0,
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean tinkersnewlife$noSlowdown$named(LocalPlayer self) {
        return tinkersnewlife$gate(self);
    }

    @Redirect(method = "m_8107_", remap = false, require = 0,
            at = @At(value = "INVOKE", ordinal = 0, remap = false,
                    target = "Lnet/minecraft/client/player/LocalPlayer;m_6117_()Z"))
    private boolean tinkersnewlife$noSlowdown$srg(LocalPlayer self) {
        return tinkersnewlife$gate(self);
    }

    // ============================================================
    //  ② L682 起跑判定：长矛时跳过（＝原版 canSprint = true）
    // ============================================================

    @Redirect(method = "aiStep", require = 0,
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean tinkersnewlife$canSprint$named(LocalPlayer self) {
        return tinkersnewlife$gate(self);
    }

    @Redirect(method = "m_8107_", remap = false, require = 0,
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
