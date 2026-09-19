package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.WizardArmorTextures;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ⭐ 资源包重载后**清掉"按材料生成图是否存在"的缓存** ✓。
 *
 * <p>踩过的坑 ✗：{@code WizardArmorTextures.clearCache()} 只写了定义、**从来没被调用** ✗
 * ⇒ 存在性缓存（{@code EXISTS}）一旦记下"这张生成图不存在"就永久生效 ✗
 * ⇒ 用户"生成纹理 + 启用资源包"之后，**先前看过的部位（法袍/护腿/靴子）毫无变化** ✗，
 * 而帽子因为那条路径是切包之后才第一次查询的，反而变了 ✓
 *（用户实测："帽子变了衣服什么的没变化" ✓）。
 *
 * <p>本类挂 **MOD 总线** ✓（{@code RegisterClientReloadListenersEvent} 属于 MOD 总线事件 ✓；
 * 游戏内事件才是 FORGE 总线 ✗ 见 §373 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WizardArmorCacheReloadHandler {

    private WizardArmorCacheReloadHandler() {}

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void unused, ResourceManager manager, ProfilerFiller profiler) {
                WizardArmorTextures.clearCache();
            }
        });
    }
}