package com.mofengbaizhi.tinkersnewlife.content;

import net.minecraft.world.item.Item;

/**
 * <b>内置默认皮肤的那只 fufu</b>（{@code tinkersnewlife:fumo_mo} ✓）—— §1079 起只是
 * {@link FumoMoBaseItem} 的一个薄子类：**行为、贴图、名字、创造栏位置全部零变化** ✓。
 *
 * <p>皮肤（{@link FumoMoSkins#DEFAULT_SKIN} ＝ {@code mo}）对应的贴图是原来那张
 * {@code textures/entity/momo_common.png} ✓ —— 它**不在** {@code textures/fumo/} 目录里，
 * 由 {@link FumoMoSkins} 内置处理 ✓。
 *
 * <p>其余皮肤不走本类 ✗：它们在 {@code FumoMoDoll} 里由
 * {@code new FumoMoBaseItem(block, props, 皮肤名)} 逐个实例化 ✓。
 */
public class FumoMoItem extends FumoMoBaseItem {

    public FumoMoItem(net.minecraft.world.level.block.Block block, Item.Properties props) {
        super(block, props, FumoMoSkins.DEFAULT_SKIN);
    }
}
