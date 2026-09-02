package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.network.PacketOpenWuWeiScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 术式「无为转变」：
 * <p>
 * - 按术式键（C）：变形中 → 恢复人形；未选择形态 → 打开形态选择界面（击杀记录）；已选择 → 顺转（自己变成所选生物）
 * - 按术式反转键（F）：将视线目标（生物/玩家）变形成所选生物，60 秒后自动恢复
 * <p>
 * 变身后继承该生物的全部基础属性（血量/移速等），但不继承其能力（AI 清空、由玩家视角操控，类似黑鸟）。
 * 形态需要先击杀对应生物才会出现在选择界面中。
 */
public final class WuWeiTechnique extends BaseTechnique {

    public static final WuWeiTechnique INSTANCE = new WuWeiTechnique();

    private WuWeiTechnique() {
        super(Modifiers.WU_WEI.getId());
    }

    /** 按下术式键（C）：变形中→恢复；未选形态→打开选择界面；已选→顺转变自己 */
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
        // 未选形态：打开选择界面（击杀记录中的生物）
        if (!WuWeiHandler.hasSelection(player)) {
            TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketOpenWuWeiScreen(WuWeiHandler.getRecordedForms(player)));
            return;
        }
        // 已选形态：顺转（自己变）
        WuWeiHandler.onSelfKey(player);
    }

    /** 按下术式反转键（F）：将视线目标变形成所选生物（限时） */
    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        WuWeiHandler.onReverseKey(player);
    }
}
