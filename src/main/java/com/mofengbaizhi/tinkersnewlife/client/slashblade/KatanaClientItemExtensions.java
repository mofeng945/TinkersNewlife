package com.mofengbaizhi.tinkersnewlife.client.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.item.modifiable.ModifiableSlashBladeItem#initializeClient

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * 拔刀剑的客户端物品扩展（= TiCEX {@code ModifiableSlashBladeItem#initializeClient} 里那个匿名类 ✓ 逐字等价 ✓）。
 *
 * <p>为什么要单独一个类 ✗：本仓铁律 —— <b>公共代码不得直接引用客户端类</b> ✓
 * （否则专服加载 {@code KatanaItem} 时会 {@code NoClassDefFoundError} ✗）。
 * ⇒ {@code KatanaItem#initializeClient} 经 {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)}
 * 甩到这里 ✓，只有客户端才会加载本类 ✓。
 */
public final class KatanaClientItemExtensions {

    private KatanaClientItemExtensions() {
    }

    /** 给物品挂上"用 {@link SBToolISTER} 画刀"的客户端扩展 ✓ */
    public static void attach(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            final BlockEntityWithoutLevelRenderer renderer = new SBToolISTER(
                    Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                    Minecraft.getInstance().getEntityModels()
            );

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        });
    }
}
