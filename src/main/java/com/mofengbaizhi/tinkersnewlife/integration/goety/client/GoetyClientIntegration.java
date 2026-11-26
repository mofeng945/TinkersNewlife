package com.mofengbaizhi.tinkersnewlife.integration.goety.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 诡厄联动的<b>客户端</b>入口：只负责把客户端渲染类（{@link GoetySlimeskullClient}）
 * 在正确的阶段（{@code FMLClientSetupEvent}）拉起来。
 *
 * <p>本类自身<b>零诡厄类型引用</b>，只做 {@code ModList} 判定 + 分派，
 * 因此即使未安装诡厄也不会出现类加载问题；{@code Dist.CLIENT} 注解保证专用服务器上不注入。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GoetyClientIntegration {

    private GoetyClientIntegration() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!IntegrationLoader.isGoety()) return;
        // 高头骨（goety:tall_skull）黏液头颅外观：失败静默（保留默认黏液头颅外观）
        GoetySlimeskullClient.registerTallSkullHead();
    }
}
