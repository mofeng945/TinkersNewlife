package com.mofengbaizhi.tinkersnewlife.content.entity;

// 移植自 TiCEX (MIT): moffy.ticex.entity.slashblade.SBToolItemEntity

import mods.flammpfeil.slashblade.entity.BladeItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 掉在地上的<b>拔刀剑本体</b>（= TiCEX 的 {@code SBToolItemEntity} ✓ 逐字移植 ✓）。
 *
 * <p>为什么要单独一个实体类型 ✗：拔刀剑本体只把**它自家的刀物品**（{@code ItemSlashBlade} 且带 BLADESTATE）
 * 的掉落物换成"会立起来转的刀" ✓；我们的刀是从匠魂物品移植来的 ⇒ 本体的判定认不出来 ✗
 * ⇒ 由 {@code KatanaItem#onEntityItemUpdate} 在落地第一 tick 把普通掉落物**换成这个实体** ✓，
 * 于是玩家看到的掉落的刀依旧是"刀的样子" ✓（渲染走 {@code SBToolBladeItemRenderer} ✓）。
 */
public class KatanaItemEntity extends BladeItemEntity {

    public KatanaItemEntity(EntityType<? extends BladeItemEntity> entityType, Level level) {
        super(entityType, level);
    }
}
