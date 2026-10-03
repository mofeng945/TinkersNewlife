package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoBlockEntityRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoCurioRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoHeadLayer;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoDoll;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端渲染注册（§880）：把 fufu 的方块实体渲染器接上 ✓（只在客户端加载 ✓ 专服不会碰 ✗） */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FumoMoRenderers {

    private FumoMoRenderers() {}

    /** §894：把 fufu 的**头部渲染器**注册进 Curios（Curios 5.x 的 CuriosRendererRegistry ✓） */
    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
                FumoMoDoll.FUMO_MO_ITEM.get(), FumoMoCurioRenderer::new));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FumoMoDoll.FUMO_MO_BE.get(), FumoMoBlockEntityRenderer::new);
    }

    /**
     * §899：<b>原版头盔槽</b>那条路 —— 原版护甲层只认 {@code ArmorItem} ✗（已核对 1.20.1 源码 ✓），
     * 所以我们自己往**玩家渲染器**上挂一层 ✓（这样物品仍然是 {@code BlockItem} ⇒ 方块照常能放 ✓）。
     * <p>⚠ {@code getSkin(名字)} 的返回类型带通配符（{@code ? extends Player}）⇒ 没法直接 {@code addLayer}
     * ⇒ 先转成 {@link PlayerRenderer} 再加 ✓（皮肤渲染器本来就是它 ✓）。
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            // ⚠ 用 getPlayerSkin（非过时 ✓）；getSkin 已被标记 deprecated ✗
            PlayerRenderer renderer = (PlayerRenderer) event.getPlayerSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new FumoMoHeadLayer<>(renderer));
            }
        }
    }
}
