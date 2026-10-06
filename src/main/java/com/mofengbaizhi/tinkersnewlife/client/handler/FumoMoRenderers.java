package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoBlockEntityRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoCurioRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoHeadLayer;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoDoll;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoSkins;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

/** 客户端渲染注册（§880）：把 fufu 的方块实体渲染器接上 ✓（只在客户端加载 ✓ 专服不会碰 ✗） */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FumoMoRenderers {

    private FumoMoRenderers() {}

    /**
     * §894：把 fufu 的**头部渲染器**注册进 Curios（Curios 5.x 的 CuriosRendererRegistry ✓）。
     * <p>§1079：**每一个皮肤物品都要注册** ✓（原来只注册 fumo_mo 一件 ✗）⇒ 挨个遍历
     * {@link FumoMoDoll#FUMO_ITEMS} ✓（单个失败只 warn 并跳过，绝不让启动崩 ✗）。
     */
    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (RegistryObject<net.minecraft.world.item.Item> item : FumoMoDoll.FUMO_ITEMS) {
                try {
                    top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
                            item.get(), FumoMoCurioRenderer::new);
                } catch (Throwable t) {
                    TinkersNewlife.LOGGER.warn("[fufu] Curios 头部渲染器注册失败（{}）：{}", item.getId(), t.toString());
                }
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FumoMoDoll.FUMO_MO_BE.get(), FumoMoBlockEntityRenderer::new);
    }

    /**
     * §1079 <b>让"自动注册出来的皮肤物品"也有模型</b> ✓ ——
     * 皮肤是运行时扫描出来的 ⇒ 不可能给每个皮肤预放一份
     * {@code assets/tinkersnewlife/models/item/fumo_&lt;名字&gt;.json} ✗。
     * <p>做法：模型烘焙完成时，把这些物品的 {@code #inventory} 模型**直接指向默认那只**
     * （{@code fumo_mo#inventory} ✓ 它的父模型是 {@code builtin/entity} ⇒
     * {@code isCustomRenderer() == true} ⇒ 客户端会自动调
     * {@link com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoItemRenderer} ✓，
     * 而"哪张皮肤"由物品栈自己在 {@code renderByItem} 里定 ✓）；展示变换也一并继承默认那只 ✓。
     * <p>⚠ 没做这一步的话，皮肤物品会落成"缺失模型"（紫黑方块 ✗）。
     * <p>⚠ 烘焙这个事件在**工作线程**上发 ⇒ 这里只碰 map ＋ 已经算好的皮肤名表 ✓，
     * 不碰任何注册表/世界 ✓。
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        try {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            ResourceLocation baseId = new ResourceLocation(TinkersNewlife.MOD_ID,
                    FumoMoSkins.itemPath(FumoMoSkins.DEFAULT_SKIN));
            BakedModel base = models.get(new ModelResourceLocation(baseId, "inventory"));
            if (base == null) {
                TinkersNewlife.LOGGER.warn("[fufu] 没找到默认皮肤的物品模型 {}#inventory ⇒ 皮肤物品可能显示成缺失模型 ✗", baseId);
                return;
            }
            int n = 0;
            for (String skin : FumoMoSkins.scanned()) {
                ResourceLocation id = new ResourceLocation(TinkersNewlife.MOD_ID, FumoMoSkins.itemPath(skin));
                models.put(new ModelResourceLocation(id, "inventory"), base);
                n++;
            }
            if (n > 0) {
                TinkersNewlife.LOGGER.info("[fufu] 已把 {} 个皮肤物品的模型指向 {}#inventory ✓", n, baseId);
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[fufu] 皮肤物品模型注入失败（皮肤物品可能显示异常，但不崩）：{}", t.toString());
        }
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
