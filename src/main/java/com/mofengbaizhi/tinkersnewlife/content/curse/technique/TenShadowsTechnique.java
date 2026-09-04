package com.mofengbaizhi.tinkersnewlife.content.curse.technique;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaseTechnique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.network.PacketOpenShikigamiScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 术式「十影术式」：召唤十种式神（玉犬/鵺/大蛇/蛤蟆/满象/脱兔/圆鹿/贯牛/虎葬/魔虚罗）。
 * <p>
 * 按下释放键呼出式神选择界面（客户端 GUI）：点击已召唤的式神 → 收回（返还一半咒力），
 * 点击未召唤的式神 → 召唤。咒力只在真正召唤式神时扣除（无维持消耗）。
 * 初始仅玉犬可用，其余需先调伏：首次召唤未调伏式神会变成敌意式神（同时攻击主人与锁定目标），
 * 击败它即调伏成功。式神的大小、速度、基础数值受咒力亲和与咒力输出缩放。
 */
public final class TenShadowsTechnique extends BaseTechnique {

    public static final TenShadowsTechnique INSTANCE = new TenShadowsTechnique();

    private TenShadowsTechnique() {
        super(Modifiers.TEN_SHADOWS.getId());
    }

    /** 按下释放键：熔断检查 → 打开式神选择界面（召回/召唤均由界面点击处理） */
    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketOpenShikigamiScreen());
    }
}
