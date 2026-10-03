package com.mofengbaizhi.tinkersnewlife.content;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * fufu 的方块物品（§881／§889）：
 * <ul>
 *   <li>挂**自定义物品渲染器**（Forge 1.20.1 没有 {@code RegisterClientExtensionsEvent} ✗ ⇒ 用官方
 *       {@link Item#initializeClient} ✓ 它只在客户端被调用 ✓）；</li>
 *   <li>实现 {@link ICurioItem} ⇒ **能戴在头上** ✓（Curios 的 {@code head} 槽 ✓ 与本仓「双向认知阻碍面具」
 *       同一套做法 ✓）。</li>
 * </ul>
 * <p>📌 待办：头部**渲染**（Curios 的 {@code ICurioRenderer}）—— 功能上"能戴"这一步已经通了 ✓，
 * 画面里挂在头上的那一半下一步接 ✓（要照 Curios 5.x 的渲染器签名来 ✓）。
 */
public class FumoMoItem extends BlockItem implements ICurioItem {

    public FumoMoItem(net.minecraft.world.level.block.Block block, Item.Properties props) {
        super(block, props);
    }

    /**
     * §890 用户口径：「应该能够戴在头上**和**头部饰品栏上」——
     * Forge 的 {@code IForgeItem#getEquipmentSlot} 返回 {@code HEAD} ⇒
     * 这一件就能**放进原版头盔槽** ✓（和南瓜/骷髅头同一机制 ✓）；
     * curios 头部槽则靠物品标签 {@code curios:head}（见 {@code data/curios/tags/items/head.json} ✓）。
     */
    /** §892 探针：只打一次，用来判断护甲渲染路径到底有没有被调用 */
    private static volatile boolean tnl$armorLogged = false;

    @Override
    public net.minecraft.world.entity.EquipmentSlot getEquipmentSlot(net.minecraft.world.item.ItemStack stack) {
        return net.minecraft.world.entity.EquipmentSlot.HEAD;
    }

    @Override
    public void initializeClient(java.util.function.Consumer<
            net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoItemRenderer.INSTANCE;
            }

            /** §891：**戴在头上能看见** —— Forge 的自定义护甲模型入口 ✓（只含 fufu ✓ 挂在 head 下 ✓） */
            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                    net.minecraft.world.entity.LivingEntity entity,
                    net.minecraft.world.item.ItemStack stack,
                    net.minecraft.world.entity.EquipmentSlot slot,
                    net.minecraft.client.model.HumanoidModel<?> original) {
                if (!tnl$armorLogged) {
                    tnl$armorLogged = true;
                    org.slf4j.LoggerFactory.getLogger("TinkersNewlife/FumoMo")
                            .info("[fufu] 护甲模型被调用 slot={}（说明头盔格那条渲染路确实走了 ✓）", slot);
                }
                return com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModelHolder.get();
            }
        });
    }
}
