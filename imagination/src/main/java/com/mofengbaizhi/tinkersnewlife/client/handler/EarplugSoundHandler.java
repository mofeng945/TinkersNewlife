package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.EarplugModifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「闭耳塞听」客户端表现：戴着装了该强化、且<b>耳塞已开启</b>的头盔时，
 * <b>屏蔽一切声音</b>（直接把 {@code PlaySoundEvent} 的声音置空）。
 * <p>
 * 与缄默手套那套"白名单"不同：耳塞是<b>全屏蔽</b>，不做任何例外——
 * 这正是该强化的代价（听不见一切，包括自己的脚步与怪叫）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EarplugSoundHandler {

    private EarplugSoundHandler() {}

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() == null) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (EarplugModifier.isMuffled(player)) {
            event.setSound(null);
        }
    }
}
