package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/**
 * 龙魂核心（Dragon Core）
 * 驯龙杖的核心部件，用于合成驯龙杖
 */
public class DragonCoreItem extends ToolPartItem {

    // 材料统计 ID，对应 tconstruct:head（头部统计）
    private static final MaterialStatsId STATS_ID =
            new MaterialStatsId(new ResourceLocation("tconstruct", "head"));

    public DragonCoreItem(Item.Properties properties) {
        super(properties, STATS_ID);
    }
}