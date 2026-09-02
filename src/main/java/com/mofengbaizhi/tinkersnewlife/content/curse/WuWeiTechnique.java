package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 术式「无为转变」：
 * <p>
 * - 按 P 键：打开形态选择界面（击杀记录中的生物；在客户端键位处理中发送请求包）
 * - 按术式键（C）：变形中 → 恢复人形；已选形态 → 顺转（自己变成所选生物）
 * - 按术式反转键（F）：开启/关闭「转变外放」——开启后下一次攻击会把目标变形成所选生物
 *   （不再需要视线锁定；命中即变形；不影响自身已有的变形状态）
 * <p>
 * 变身后继承该生物的全部基础属性（血量/移速等），但不继承其能力（AI 清空、由玩家视角操控）。
 * 形态需要先击杀对应生物才会出现在选择界面中。
 */
public final class WuWeiTechnique extends BaseTechnique {

    public static final WuWeiTechnique INSTANCE = new WuWeiTechnique();

    private WuWeiTechnique() {
        super(Modifiers.WU_WEI.getId());
    }

    /** 按下术式键（C）：变形中→恢复；未选形态→提示按 P 选择；已选→顺转变自己 */
    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        // 变形中：恢复人形
        if (WuWeiHandler.isTransformed(player)) {
            WuWeiHandler.endTransformPublic(player);
            return;
        }
        // 未选形态：提示按 P 打开选择界面
        if (!WuWeiHandler.hasSelection(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.need_select_p"), true);
            return;
        }
        // 已选形态：顺转（自己变）
        WuWeiHandler.onSelfKey(player);
    }

    /** 按下术式反转键（F）：开启/关闭「转变外放」（不影响自身已有的变形状态） */
    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        // 未选形态：提示按 P 选择
        if (!WuWeiHandler.hasSelection(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.need_select_p"), true);
            return;
        }
        // 开关转变外放（自身变形中亦可开启：下一次攻击把目标变形成所选生物）
        boolean now = WuWeiHandler.toggleReversal(player);
        player.displayClientMessage(Component.translatable(now
                ? "message.tinkersnewlife.wu_wei.reversal_on"
                : "message.tinkersnewlife.wu_wei.reversal_off"), true);
    }
}
