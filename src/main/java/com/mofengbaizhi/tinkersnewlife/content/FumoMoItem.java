package com.mofengbaizhi.tinkersnewlife.content;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/**
 * fufu 的方块物品（§881）：只为了挂**自定义物品渲染器**——
 * Forge 1.20.1（47.4.22）没有 {@code RegisterClientExtensionsEvent} ✗，
 * 官方入口是 {@link Item#initializeClient} ✓：它**只在客户端被调用** ✓
 * ⇒ 服务端永远不会执行到 body 里的客户端类型 ✓（本仓 §801 那条"公共代码别碰客户端类"的规矩不冲突 ✓）。
 */
public class FumoMoItem extends BlockItem {

    public FumoMoItem(net.minecraft.world.level.block.Block block, Item.Properties props) {
        super(block, props);
    }

    @Override
    public void initializeClient(java.util.function.Consumer<
            net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoItemRenderer.INSTANCE;
            }
        });
    }
}
